"""End-to-end integration tests for the compiled LangGraph."""

from __future__ import annotations

import struct
import uuid
import wave
from pathlib import Path

import pytest

from agent import nodes as nodes_module
from agent.graph import build_graph
from models.base import StressDetectorBase


def _make_wav(path: Path, duration_s: float = 0.25) -> Path:
    n = int(duration_s * 16000)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(struct.pack("<" + "h" * n, *([0] * n)))
    return path


def _run(app, audio_path: Path) -> dict:
    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    return app.invoke(
        {
            "audio_input": {"path": str(audio_path)},
            "metadata": {},
            "errors": [],
            "recommendations": [],
        },
        config,
    )


class _FixedDetector(StressDetectorBase):
    def __init__(self, score: float, confidence: float = 0.9) -> None:
        self.score = score
        self.confidence = confidence

    def detect(self, audio_bytes):
        label = self.score_to_label(self.score)
        return {
            "stress_score": self.score,
            "confidence": self.confidence,
            "label": label,
        }


# --------------------------------------------------------------------------- #
# Happy paths                                                                  #
# --------------------------------------------------------------------------- #
def test_graph_low_stress_reaches_report_without_recommendations(
    tmp_path, monkeypatch
):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.15))
    app = build_graph(use_hitl=False)
    final = _run(app, _make_wav(tmp_path / "low.wav"))
    report = final["final_report"]
    assert report["stress_label"] == "low"
    assert report["escalated"] is False
    assert report["escalation_alert"] is None
    assert report["recommendations"] == []
    assert report["pipeline_latency_ms"] > 0
    assert final["errors"] == []


def test_graph_medium_stress_produces_recommendations(tmp_path, monkeypatch):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.5))
    app = build_graph(use_hitl=False)
    final = _run(app, _make_wav(tmp_path / "mid.wav"))
    report = final["final_report"]
    assert report["stress_label"] == "medium"
    assert report["escalated"] is False
    assert len(report["recommendations"]) == 3


def test_graph_high_stress_escalates_when_hitl_disabled(tmp_path, monkeypatch):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.92))
    app = build_graph(use_hitl=False)
    final = _run(app, _make_wav(tmp_path / "hi.wav"))
    report = final["final_report"]
    assert report["stress_label"] == "high"
    assert report["escalated"] is True
    assert report["escalation_alert"]["severity"] == "HIGH"
    assert "recommended_action" in report["escalation_alert"]


# --------------------------------------------------------------------------- #
# Resilience                                                                   #
# --------------------------------------------------------------------------- #
def test_graph_reaches_report_even_on_missing_file():
    app = build_graph(use_hitl=False)
    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    final = app.invoke(
        {"audio_input": {"path": "/no/such/file.wav"}, "metadata": {},
         "errors": [], "recommendations": []},
        config,
    )
    report = final["final_report"]
    assert report, "must always produce a final report"
    assert final["errors"], "the failure must be captured, not raised"
    assert final["errors"][0]["node"] == "audio_ingestion_node"


def test_graph_records_latency_for_every_node(tmp_path, monkeypatch):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.2))
    app = build_graph(use_hitl=False)
    final = _run(app, _make_wav(tmp_path / "lat.wav"))
    meta = final["metadata"]
    for node in (
        "audio_ingestion_node",
        "preprocessing_node",
        "stress_analysis_node",
        "decision_node",
        "report_node",
    ):
        assert "latency_ms" in meta[node], f"{node} must record latency"
        assert meta[node]["latency_ms"] >= 0.0


# --------------------------------------------------------------------------- #
# Human-in-the-loop                                                           #
# --------------------------------------------------------------------------- #
def test_hitl_pauses_before_escalation(tmp_path, monkeypatch):
    """With HITL enabled, the graph must stop before escalation_node."""
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.9))
    app = build_graph(use_hitl=True)
    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    app.invoke(
        {"audio_input": {"path": str(_make_wav(tmp_path / "hitl.wav"))},
         "metadata": {}, "errors": [], "recommendations": []},
        config,
    )
    snap = app.get_state(config)
    assert "escalation_node" in snap.next, (
        f"interrupt_before did not fire; next nodes = {snap.next}"
    )


def test_hitl_approval_completes_escalation(tmp_path, monkeypatch):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.9))
    app = build_graph(use_hitl=True)
    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    app.invoke(
        {"audio_input": {"path": str(_make_wav(tmp_path / "hitl2.wav"))},
         "metadata": {}, "errors": [], "recommendations": []},
        config,
    )
    # Simulate approval: resume execution.
    app.invoke(None, config)
    final = app.get_state(config).values
    assert final["final_report"]["escalated"] is True


def test_hitl_denial_skips_escalation(tmp_path, monkeypatch):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.9))
    app = build_graph(use_hitl=True)
    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    app.invoke(
        {"audio_input": {"path": str(_make_wav(tmp_path / "hitl3.wav"))},
         "metadata": {}, "errors": [], "recommendations": []},
        config,
    )
    # Simulate denial: mark escalation_node as having completed with no alert.
    app.update_state(
        config,
        {"escalation_alert": None, "decision": "report"},
        as_node="escalation_node",
    )
    app.invoke(None, config)
    final = app.get_state(config).values
    report = final["final_report"]
    assert report["escalated"] is False
    assert report["escalation_alert"] is None
    assert report["stress_label"] == "high"  # the score/label are preserved


# --------------------------------------------------------------------------- #
# Checkpointing                                                                #
# --------------------------------------------------------------------------- #
def test_checkpointer_persists_state_between_get_state_calls(
    tmp_path, monkeypatch
):
    monkeypatch.setattr(nodes_module, "_detector", _FixedDetector(0.2))
    app = build_graph(use_hitl=False)
    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    _ = app.invoke(
        {"audio_input": {"path": str(_make_wav(tmp_path / "cp.wav"))},
         "metadata": {}, "errors": [], "recommendations": []},
        config,
    )
    # get_state should load the persisted checkpoint for this thread_id.
    snap = app.get_state(config)
    assert snap.values["final_report"]
    assert snap.values["stress_result"]["label"] == "low"
