"""Deterministic, side-effect-free mock of the future wav2vec2 detector.

The output is seeded off a hash of the input bytes, so the same audio
reproduces the same score — useful for tests and demos — while varying
audio produces varied scores.
"""

from __future__ import annotations

import hashlib
import random
import time
from typing import Optional

from .base import DetectorResult, StressDetectorBase


class MockStressDetector(StressDetectorBase):
    """Stand-in for a real model. Returns a plausible stress result."""

    SIMULATED_LATENCY_S: float = 0.1

    def __init__(self, seed: Optional[int] = None) -> None:
        self._seed = seed

    def detect(self, audio_bytes: bytes) -> DetectorResult:
        if not isinstance(audio_bytes, (bytes, bytearray)):
            raise TypeError(
                f"audio_bytes must be bytes, got {type(audio_bytes).__name__}"
            )
        if len(audio_bytes) == 0:
            raise ValueError("audio_bytes is empty; cannot run inference")

        time.sleep(self.SIMULATED_LATENCY_S)

        if self._seed is not None:
            rng_seed = self._seed
        else:
            head = audio_bytes[:1024]
            digest = hashlib.md5(head).hexdigest()
            rng_seed = int(digest[:8], 16)
        rng = random.Random(rng_seed)

        stress_score = round(rng.uniform(0.0, 1.0), 3)
        confidence = round(rng.uniform(0.7, 0.99), 3)
        label = self.score_to_label(stress_score)

        return {
            "stress_score": stress_score,
            "confidence": confidence,
            "label": label,
        }


__all__ = ["MockStressDetector"]
