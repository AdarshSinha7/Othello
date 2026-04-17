"""Abstract interface for stress detectors.

The agent depends on ``StressDetectorBase`` rather than a concrete class so
that the mock used in this demo can be swapped out for a real wav2vec2
implementation without touching the graph wiring.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Literal, TypedDict


class DetectorResult(TypedDict):
    stress_score: float
    confidence: float
    label: Literal["low", "medium", "high"]


class StressDetectorBase(ABC):
    """Anything that maps audio bytes -> a stress result dict."""

    @abstractmethod
    def detect(self, audio_bytes: bytes) -> DetectorResult:
        """Run inference on a WAV byte payload.

        Implementations must return a dict with keys ``stress_score`` (0.0-1.0),
        ``confidence`` (0.0-1.0), and ``label`` (``"low"``/``"medium"``/``"high"``).
        """
        raise NotImplementedError

    @staticmethod
    def score_to_label(score: float) -> Literal["low", "medium", "high"]:
        """Convenience mapping used by most implementations."""
        if score >= 0.7:
            return "high"
        if score >= 0.4:
            return "medium"
        return "low"


__all__ = ["StressDetectorBase", "DetectorResult"]
