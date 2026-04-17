"""All node implementations for the stress-detection graph.

Every node follows the same contract:
  1. Open a ``NodeTimer`` so a latency entry always lands in metadata.
  2. Wrap the body in ``try/except`` and push caught exceptions to
     ``state["errors"]`` rather than raising. The graph must always reach
     ``report_node``, so a failure here becomes data, not a crash.
  3. Return the mutated state.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from models.base import StressDetectorBase
from models.mock_detector import MockStressDetector
from shared.metrics import NodeTimer, sum_latencies

from .state import AgentState

logger = logging.getLogger("stress_agent")

MAX_FILE_SIZE_BYTES: int = 10 * 1024 * 1024  # 10 MB input cap
RETRY_MAX_ATTEMPTS: int = 3
RETRY_BACKOFF_BASE_S: float = 0.1

# Module-level detector so tests can monkeypatch ``_detector`` with a
# different implementation without having to rebuild the graph.
_detector: StressDetectorBase = MockStressDetector()

_PIPELINE_NODE_NAMES = (
    "audio_ingestion_node",
    "preprocessing_node",
    "stress_analysis_node",
    "decision_node",
    "escalation_node",
    "recommendation_node",
    "report_node",
)


def _append_error(state: AgentState, node: str, err: BaseException) -> None:
    errors = list(state.get("errors") or [])
    errors.append(
        {
            "node": node,
            "type": type(err).__name__,
            "message": str(err),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
    )
    state["errors"] = errors


def _update_metadata(state: AgentState, node: str, **entries: Any) -> None:
    metadata = dict(state.get("metadata") or {})
    existing = dict(metadata.get(node) or {})
    existing.update(entries)
    metadata[node] = existing
    state["metadata"] = metadata


# --------------------------------------------------------------------------- #
# 1. Ingestion                                                                 #
# --------------------------------------------------------------------------- #
def audio_ingestion_node(state: AgentState) -> AgentState:
    """Validate the incoming audio and normalize it to raw bytes."""
    with NodeTimer("audio_ingestion_node") as timer:
        try:
            audio_input = dict(state.get("audio_input") or {})
            filename = (
                audio_input.get("filename")
                or audio_input.get("path")
                or "<in-memory>"
            )

            raw_bytes: bytes
            source: str

            if audio_input.get("bytes") is not None:
                candidate = audio_input["bytes"]
                if not isinstance(candidate, (bytes, bytearray)):
                    raise TypeError(
                        "audio_input['bytes'] must be bytes, got "
                        f"{type(candidate).__name__}"
                    )
                raw_bytes = bytes(candidate)
                source = "bytes"
                if len(raw_bytes) == 0:
                    raise ValueError("audio payload is empty")
                if not str(filename).lower().endswith(".wav"):
                    raise ValueError(
                        f"only .wav audio is accepted (filename={filename!r})"
                    )
                if len(raw_bytes) > MAX_FILE_SIZE_BYTES:
                    raise ValueError(
                        f"audio payload exceeds {MAX_FILE_SIZE_BYTES} bytes: "
                        f"{len(raw_bytes)} bytes"
                    )
                filename = Path(str(filename)).name
            elif audio_input.get("path") is not None:
                path = Path(str(audio_input["path"]))
                source = "path"
                filename = path.name
                if not path.exists():
                    raise FileNotFoundError(f"audio file not found: {path}")
                if path.suffix.lower() != ".wav":
                    raise ValueError(
                        f"only .wav audio is accepted (got suffix {path.suffix!r})"
                    )
                size = path.stat().st_size
                if size == 0:
                    raise ValueError(f"audio file is empty: {path}")
                if size > MAX_FILE_SIZE_BYTES:
                    raise ValueError(
                        f"audio file exceeds {MAX_FILE_SIZE_BYTES} bytes: "
                        f"{size} bytes ({path})"
                    )
                raw_bytes = path.read_bytes()
            else:
                raise ValueError(
                    "audio_input must supply either 'path' or 'bytes'"
                )

            ingested_at = datetime.now(timezone.utc).isoformat()
            logger.info(
                "audio_ingestion ok source=%s filename=%s size_bytes=%d at=%s",
                source,
                filename,
                len(raw_bytes),
                ingested_at,
            )

            state["audio_bytes"] = raw_bytes
            _update_metadata(
                state,
                "audio_ingestion_node",
                filename=filename,
                size_bytes=len(raw_bytes),
                source=source,
                ingested_at=ingested_at,
            )
        except Exception as err:  # noqa: BLE001 — capture into state.errors
            logger.error("audio_ingestion_node failed: %s", err)
            _append_error(state, "audio_ingestion_node", err)
            # Provide a safe default so downstream nodes don't KeyError.
            state.setdefault("audio_bytes", b"")
    _update_metadata(
        state,
        "audio_ingestion_node",
        latency_ms=round(timer.elapsed_ms, 3),
    )
    return state


# --------------------------------------------------------------------------- #
# 2. Preprocessing                                                            #
# --------------------------------------------------------------------------- #
def preprocessing_node(state: AgentState) -> AgentState:
    """Produce a 40-dim mock MFCC vector from the audio bytes."""
    with NodeTimer("preprocessing_node") as timer:
        try:
            audio_bytes = state.get("audio_bytes") or b""
            if not audio_bytes:
                raise ValueError(
                    "audio_bytes missing or empty; upstream ingestion failed"
                )

            # Seed a deterministic RNG off the byte payload so the same audio
            # yields the same feature vector. This mimics the stable mapping
            # of a real feature extractor without depending on one.
            seed_material = audio_bytes[:256]
            seed = sum(seed_material) if seed_material else 1
            rng = np.random.default_rng(seed or 1)
            features_np: np.ndarray = rng.normal(loc=0.0, scale=1.0, size=40)

            # State declares features as list; LangGraph's checkpointer
            # serializes plain Python types more cleanly than numpy arrays.
            state["features"] = features_np.tolist()
            logger.info(
                "preprocessing ok feature_dim=%d seed=%d",
                features_np.shape[0],
                seed,
            )
        except Exception as err:  # noqa: BLE001
            logger.error("preprocessing_node failed: %s", err)
            _append_error(state, "preprocessing_node", err)
            state["features"] = []
    _update_metadata(
        state,
        "preprocessing_node",
        latency_ms=round(timer.elapsed_ms, 3),
    )
    return state


# --------------------------------------------------------------------------- #
# 3. Stress analysis with retry                                               #
# --------------------------------------------------------------------------- #
def stress_analysis_node(state: AgentState) -> AgentState:
    """Invoke the detector with up to 3 attempts and exponential backoff."""
    with NodeTimer("stress_analysis_node") as timer:
        attempts = 0
        last_exc: BaseException | None = None
        inference_ms_total = 0.0

        while attempts < RETRY_MAX_ATTEMPTS:
            attempts += 1
            attempt_start = time.perf_counter()
            try:
                audio_bytes = state.get("audio_bytes") or b""
                if not audio_bytes:
                    raise ValueError(
                        "no audio_bytes available for inference"
                    )
                result = _detector.detect(audio_bytes)
                inference_ms_total += (
                    (time.perf_counter() - attempt_start) * 1000.0
                )
                # Sanity-check the detector contract; a bad output shouldn't
                # silently poison the rest of the pipeline.
                score = float(result["stress_score"])
                confidence = float(result["confidence"])
                label = str(result["label"])
                if not 0.0 <= score <= 1.0:
                    raise ValueError(
                        f"detector returned stress_score out of range: {score}"
                    )
                state["stress_result"] = {
                    "stress_score": score,
                    "confidence": confidence,
                    "label": label,
                }
                logger.info(
                    "stress_analysis ok attempts=%d score=%.3f label=%s "
                    "confidence=%.3f",
                    attempts,
                    score,
                    label,
                    confidence,
                )
                last_exc = None
                break
            except Exception as err:  # noqa: BLE001 — retry loop handles it
                inference_ms_total += (
                    (time.perf_counter() - attempt_start) * 1000.0
                )
                last_exc = err
                logger.error(
                    "stress_analysis attempt %d/%d failed: %s",
                    attempts,
                    RETRY_MAX_ATTEMPTS,
                    err,
                )
                if attempts < RETRY_MAX_ATTEMPTS:
                    backoff = RETRY_BACKOFF_BASE_S * (2 ** (attempts - 1))
                    time.sleep(backoff)

        if last_exc is not None:
            _append_error(state, "stress_analysis_node", last_exc)
            state["stress_result"] = {
                "stress_score": 0.0,
                "confidence": 0.0,
                "label": "low",
                "error": str(last_exc),
            }

    _update_metadata(
        state,
        "stress_analysis_node",
        latency_ms=round(timer.elapsed_ms, 3),
        attempts=attempts,
        inference_ms=round(inference_ms_total, 3),
    )
    return state


# --------------------------------------------------------------------------- #
# 4. Decision                                                                 #
# --------------------------------------------------------------------------- #
def decision_node(state: AgentState) -> AgentState:
    """Map the stress score onto a routing decision for the conditional edge."""
    with NodeTimer("decision_node") as timer:
        try:
            stress = state.get("stress_result") or {}
            score = float(stress.get("stress_score", 0.0))
            if score >= 0.7:
                decision = "escalate"
            elif score >= 0.4:
                decision = "recommend"
            else:
                decision = "report"
            state["decision"] = decision
            logger.info(
                "decision=%s (stress_score=%.3f)", decision, score
            )
        except Exception as err:  # noqa: BLE001
            logger.error("decision_node failed: %s", err)
            _append_error(state, "decision_node", err)
            # Default to the benign path so the user still gets a report.
            state["decision"] = "report"
    _update_metadata(
        state,
        "decision_node",
        latency_ms=round(timer.elapsed_ms, 3),
        decision=state.get("decision"),
    )
    return state


# --------------------------------------------------------------------------- #
# 5. Escalation                                                               #
# --------------------------------------------------------------------------- #
def escalation_node(state: AgentState) -> AgentState:
    """Build the high-severity alert dict. Reached only for score >= 0.7."""
    with NodeTimer("escalation_node") as timer:
        try:
            stress = state.get("stress_result") or {}
            alert = {
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "severity": "HIGH",
                "recommended_action": "Immediate supervisor notification",
                "stress_score": stress.get("stress_score"),
                "confidence": stress.get("confidence"),
            }
            state["escalation_alert"] = alert
            logger.warning(
                "ESCALATION severity=HIGH stress_score=%s action=%s",
                alert["stress_score"],
                alert["recommended_action"],
            )
        except Exception as err:  # noqa: BLE001
            logger.error("escalation_node failed: %s", err)
            _append_error(state, "escalation_node", err)
            state["escalation_alert"] = None
    _update_metadata(
        state,
        "escalation_node",
        latency_ms=round(timer.elapsed_ms, 3),
    )
    return state


# --------------------------------------------------------------------------- #
# 6. Recommendation                                                           #
# --------------------------------------------------------------------------- #
def recommendation_node(state: AgentState) -> AgentState:
    """Produce three wellness recommendations. Reached for 0.4 <= score < 0.7."""
    with NodeTimer("recommendation_node") as timer:
        try:
            recommendations = [
                "Take a 5-minute box-breathing exercise (4s inhale, 4s hold, "
                "4s exhale, 4s hold).",
                "Step away from your current task for a 10-minute walking "
                "break to reset.",
                "Drink a glass of water — mild dehydration elevates stress "
                "markers.",
            ]
            state["recommendations"] = recommendations
            logger.info(
                "wellness recommendations generated count=%d",
                len(recommendations),
            )
        except Exception as err:  # noqa: BLE001
            logger.error("recommendation_node failed: %s", err)
            _append_error(state, "recommendation_node", err)
            state["recommendations"] = []
    _update_metadata(
        state,
        "recommendation_node",
        latency_ms=round(timer.elapsed_ms, 3),
    )
    return state


# --------------------------------------------------------------------------- #
# 7. Report                                                                   #
# --------------------------------------------------------------------------- #
def report_node(state: AgentState) -> AgentState:
    """Aggregate everything in state into a single structured report."""
    with NodeTimer("report_node") as timer:
        try:
            metadata = state.get("metadata") or {}
            stress = state.get("stress_result") or {}
            audio_meta = metadata.get("audio_ingestion_node") or {}

            # Total latency = sum of every node's recorded latency. The
            # report_node's own latency is included so the number matches
            # what the user perceives.
            total_ms = sum_latencies(metadata, _PIPELINE_NODE_NAMES)
            # Include this node's in-flight latency too.
            total_ms += timer.elapsed_ms

            alert = state.get("escalation_alert")
            final_report = {
                "audio_file": audio_meta.get("filename", "<unknown>"),
                "stress_label": stress.get("label", "unknown"),
                "stress_score": stress.get("stress_score", 0.0),
                "confidence": stress.get("confidence", 0.0),
                "pipeline_latency_ms": round(total_ms, 3),
                "escalated": bool(alert),
                "recommendations": list(state.get("recommendations") or []),
                "escalation_alert": alert,
                "errors": list(state.get("errors") or []),
                "timestamp": datetime.now(timezone.utc).isoformat(),
            }
            state["final_report"] = final_report
            logger.info(
                "report_node ok label=%s score=%s latency_ms=%.3f "
                "escalated=%s errors=%d",
                final_report["stress_label"],
                final_report["stress_score"],
                final_report["pipeline_latency_ms"],
                final_report["escalated"],
                len(final_report["errors"]),
            )
        except Exception as err:  # noqa: BLE001
            logger.error("report_node failed: %s", err)
            _append_error(state, "report_node", err)
            state["final_report"] = {
                "error": str(err),
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "errors": list(state.get("errors") or []),
            }
    _update_metadata(
        state,
        "report_node",
        latency_ms=round(timer.elapsed_ms, 3),
    )
    return state


__all__ = [
    "audio_ingestion_node",
    "preprocessing_node",
    "stress_analysis_node",
    "decision_node",
    "escalation_node",
    "recommendation_node",
    "report_node",
    "MAX_FILE_SIZE_BYTES",
    "RETRY_MAX_ATTEMPTS",
]
