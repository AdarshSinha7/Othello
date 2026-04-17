"""Unit tests for each node, exercised in isolation with hand-crafted state."""

from __future__ import annotations

import struct
import tempfile
import wave
from pathlib import Path

import pytest

from agent import nodes as nodes_module
from agent.nodes import (
    MAX_FILE_SIZE_BYTES,
    audio_ingestion_node,
    decision_node,
    escalation_node,
    preprocessing_node,
    recommendation_node,
    report_node,
    stress_analysis_node,
)
from models.base import StressDetectorBase


def _make_wav(path: Path, duration_s: float = 0.25) -> Path:
    n = int(duration_s * 16000)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(struct.pack("<" + "h" * n, *([0] * n)))
    return path


def _fresh_state(**overrides) -> dict:
    base = {
        "audio_input": {},
        "audio_bytes": b"",
        "features": [],
        "stress_result": {},
        "decision": "",
        "escalation_alert": None,
        "recommendations": [],
        "final_report": {},
        "metadata": {},
        "errors": [],
    }
    base.update(overrides)
    return base


# --------------------------------------------------------------------------- #
# audio_ingestion_node                                                        #
# --------------------------------------------------------------------------- #
def test_ingest_from_valid_path(tmp_path):
    p = _make_wav(tmp_path / "ok.wav")
    state = _fresh_state(audio_input={"path": str(p)})
    out = audio_ingestion_node(state)
    assert out["audio_bytes"], "audio_bytes should be populated"
    assert out["metadata"]["audio_ingestion_node"]["filename"] == "ok.wav"
    assert out["metadata"]["audio_ingestion_node"]["size_bytes"] > 0
    assert out["metadata"]["audio_ingestion_node"]["latency_ms"] >= 0.0
    assert out["errors"] == []


def test_ingest_from_raw_bytes():
    payload = b"RIFF" + b"x" * 200
    state = _fresh_state(audio_input={"bytes": payload, "filename": "buf.wav"})
    out = audio_ingestion_node(state)
    assert out["audio_bytes"] == payload
    assert out["errors"] == []
    assert out["metadata"]["audio_ingestion_node"]["filename"] == "buf.wav"


def test_ingest_rejects_non_wav_extension(tmp_path):
    p = tmp_path / "bad.mp3"
    p.write_bytes(b"not really mp3 but not wav either")
    state = _fresh_state(audio_input={"path": str(p)})
    out = audio_ingestion_node(state)
    assert out["errors"], "a non-.wav file must be rejected"
    assert "wav" in out["errors"][0]["message"].lower()


def test_ingest_rejects_missing_file():
    state = _fresh_state(audio_input={"path": "/nope/does/not/exist.wav"})
    out = audio_ingestion_node(state)
    assert out["errors"]
    assert out["errors"][0]["type"] == "FileNotFoundError"


def test_ingest_rejects_empty_file(tmp_path):
    p = tmp_path / "empty.wav"
    p.write_bytes(b"")
    state = _fresh_state(audio_input={"path": str(p)})
    out = audio_ingestion_node(state)
    assert out["errors"]
    assert "empty" in out["errors"][0]["message"].lower()


def test_ingest_rejects_oversize_bytes():
    # Build a payload just over the limit.
    payload = b"\x00" * (MAX_FILE_SIZE_BYTES + 1)
    state = _fresh_state(audio_input={"bytes": payload, "filename": "big.wav"})
    out = audio_ingestion_node(state)
    assert out["errors"]
    assert "10485760" in out["errors"][0]["message"] or \
        "exceeds" in out["errors"][0]["message"].lower()


def test_ingest_rejects_empty_bytes():
    state = _fresh_state(audio_input={"bytes": b"", "filename": "x.wav"})
    out = audio_ingestion_node(state)
    assert out["errors"]


def test_ingest_requires_path_or_bytes():
    state = _fresh_state(audio_input={})
    out = audio_ingestion_node(state)
    assert out["errors"]
    assert "path" in out["errors"][0]["message"].lower()


# --------------------------------------------------------------------------- #
# preprocessing_node                                                          #
# --------------------------------------------------------------------------- #
def test_preprocessing_produces_40_features():
    state = _fresh_state(audio_bytes=b"a" * 1000)
    out = preprocessing_node(state)
    assert len(out["features"]) == 40
    assert all(isinstance(x, float) for x in out["features"])
    assert out["errors"] == []


def test_preprocessing_is_deterministic_for_same_bytes():
    s1 = _fresh_state(audio_bytes=b"same bytes" * 20)
    s2 = _fresh_state(audio_bytes=b"same bytes" * 20)
    assert preprocessing_node(s1)["features"] == preprocessing_node(s2)["features"]


def test_preprocessing_records_errors_on_empty_audio():
    state = _fresh_state(audio_bytes=b"")
    out = preprocessing_node(state)
    assert out["errors"]
    assert out["features"] == []


# --------------------------------------------------------------------------- #
# stress_analysis_node                                                        #
# --------------------------------------------------------------------------- #
def test_stress_analysis_populates_result():
    state = _fresh_state(audio_bytes=b"a" * 200)
    out = stress_analysis_node(state)
    assert "stress_score" in out["stress_result"]
    assert 0.0 <= out["stress_result"]["stress_score"] <= 1.0
    assert out["metadata"]["stress_analysis_node"]["attempts"] >= 1
    assert out["errors"] == []


def test_stress_analysis_retries_three_times_on_failure(monkeypatch):
    call_log: list[int] = []

    class AlwaysFails(StressDetectorBase):
        def detect(self, audio_bytes):
            call_log.append(1)
            raise RuntimeError("simulated model crash")

    monkeypatch.setattr(nodes_module, "_detector", AlwaysFails())
    # Cut the backoff so the test doesn't take ~0.7s on retries.
    monkeypatch.setattr(nodes_module, "RETRY_BACKOFF_BASE_S", 0.0)

    state = _fresh_state(audio_bytes=b"payload")
    out = stress_analysis_node(state)
    assert len(call_log) == 3, "must retry up to 3 attempts"
    assert out["errors"], "final failure must be recorded"
    assert out["stress_result"]["label"] == "low"
    assert out["metadata"]["stress_analysis_node"]["attempts"] == 3


def test_stress_analysis_succeeds_on_second_attempt(monkeypatch):
    calls = {"n": 0}

    class FlakyDetector(StressDetectorBase):
        def detect(self, audio_bytes):
            calls["n"] += 1
            if calls["n"] == 1:
                raise RuntimeError("flaky first call")
            return {"stress_score": 0.55, "confidence": 0.9, "label": "medium"}

    monkeypatch.setattr(nodes_module, "_detector", FlakyDetector())
    monkeypatch.setattr(nodes_module, "RETRY_BACKOFF_BASE_S", 0.0)

    state = _fresh_state(audio_bytes=b"payload")
    out = stress_analysis_node(state)
    assert out["stress_result"]["stress_score"] == 0.55
    assert out["errors"] == []
    assert out["metadata"]["stress_analysis_node"]["attempts"] == 2


# --------------------------------------------------------------------------- #
# decision_node                                                               #
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize(
    "score,expected",
    [(0.0, "report"), (0.39, "report"), (0.4, "recommend"),
     (0.69, "recommend"), (0.7, "escalate"), (1.0, "escalate")],
)
def test_decision_thresholds(score, expected):
    state = _fresh_state(stress_result={"stress_score": score})
    out = decision_node(state)
    assert out["decision"] == expected


def test_decision_missing_score_falls_back_safely():
    state = _fresh_state(stress_result={})
    out = decision_node(state)
    assert out["decision"] == "report"


# --------------------------------------------------------------------------- #
# escalation_node                                                             #
# --------------------------------------------------------------------------- #
def test_escalation_produces_high_severity_alert():
    state = _fresh_state(
        stress_result={"stress_score": 0.9, "confidence": 0.95, "label": "high"}
    )
    out = escalation_node(state)
    alert = out["escalation_alert"]
    assert alert["severity"] == "HIGH"
    assert "timestamp" in alert
    assert alert["recommended_action"] == "Immediate supervisor notification"
    assert alert["stress_score"] == 0.9


# --------------------------------------------------------------------------- #
# recommendation_node                                                         #
# --------------------------------------------------------------------------- #
def test_recommendations_has_three_items():
    state = _fresh_state()
    out = recommendation_node(state)
    assert len(out["recommendations"]) == 3
    assert all(isinstance(r, str) and r for r in out["recommendations"])


# --------------------------------------------------------------------------- #
# report_node                                                                 #
# --------------------------------------------------------------------------- #
def test_report_aggregates_low_stress_path():
    state = _fresh_state(
        audio_input={"path": "x.wav"},
        stress_result={"stress_score": 0.2, "confidence": 0.91, "label": "low"},
        metadata={
            "audio_ingestion_node": {"filename": "x.wav", "latency_ms": 1.0},
            "preprocessing_node": {"latency_ms": 2.0},
            "stress_analysis_node": {"latency_ms": 3.0},
            "decision_node": {"latency_ms": 0.5},
        },
    )
    out = report_node(state)
    report = out["final_report"]
    assert report["stress_label"] == "low"
    assert report["stress_score"] == 0.2
    assert report["pipeline_latency_ms"] > 0
    assert report["escalated"] is False
    assert report["escalation_alert"] is None
    assert report["recommendations"] == []
    assert report["audio_file"] == "x.wav"


def test_report_reflects_escalation():
    alert = {"severity": "HIGH", "timestamp": "2025-01-01T00:00:00Z"}
    state = _fresh_state(
        audio_input={"path": "x.wav"},
        stress_result={"stress_score": 0.9, "confidence": 0.95, "label": "high"},
        escalation_alert=alert,
        metadata={"audio_ingestion_node": {"filename": "x.wav"}},
    )
    out = report_node(state)
    assert out["final_report"]["escalated"] is True
    assert out["final_report"]["escalation_alert"] == alert


def test_report_includes_errors_list():
    state = _fresh_state(
        audio_input={"path": "x.wav"},
        stress_result={"stress_score": 0.1, "confidence": 0.9, "label": "low"},
        errors=[{"node": "audio_ingestion_node", "message": "boom"}],
    )
    out = report_node(state)
    assert out["final_report"]["errors"]
    assert out["final_report"]["errors"][0]["message"] == "boom"
