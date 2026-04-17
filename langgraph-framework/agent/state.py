"""Typed state carried through the LangGraph pipeline.

Every node reads from and writes to an ``AgentState`` dict. ``total=False``
makes every field optional so we can start with a sparse initial state
(only ``audio_input``) and let each node fill in what it produces.
"""

from __future__ import annotations

from typing import Any, Optional, TypedDict


class AgentState(TypedDict, total=False):
    audio_input: dict
    audio_bytes: bytes
    features: list
    stress_result: dict
    decision: str
    escalation_alert: Optional[dict]
    recommendations: list
    final_report: dict
    metadata: dict
    errors: list


def empty_state() -> AgentState:
    """Return an empty, well-formed state scaffold."""
    return {
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


__all__ = ["AgentState", "empty_state"]
