"""Unit tests for ``MockStressDetector``."""

from __future__ import annotations

import pytest

from models.base import StressDetectorBase
from models.mock_detector import MockStressDetector


def test_subclasses_base():
    assert issubclass(MockStressDetector, StressDetectorBase)


def test_detect_returns_required_keys():
    detector = MockStressDetector()
    result = detector.detect(b"some wav-ish bytes, more than zero")
    assert set(result.keys()) >= {"stress_score", "confidence", "label"}
    assert isinstance(result["stress_score"], float)
    assert isinstance(result["confidence"], float)
    assert isinstance(result["label"], str)


@pytest.mark.parametrize(
    "payload",
    [b"short", b"x" * 1024, b"mixed bag \x00\x01\x02\xff" * 50],
)
def test_score_and_confidence_in_unit_range(payload):
    detector = MockStressDetector()
    result = detector.detect(payload)
    assert 0.0 <= result["stress_score"] <= 1.0
    assert 0.0 <= result["confidence"] <= 1.0


def test_label_consistent_with_score():
    for seed in range(50):
        detector = MockStressDetector(seed=seed)
        result = detector.detect(b"sample")
        score = result["stress_score"]
        label = result["label"]
        if score >= 0.7:
            assert label == "high", (score, label)
        elif score >= 0.4:
            assert label == "medium", (score, label)
        else:
            assert label == "low", (score, label)


def test_deterministic_for_same_input():
    a = MockStressDetector().detect(b"same bytes repeated")
    b = MockStressDetector().detect(b"same bytes repeated")
    assert a == b


def test_different_inputs_yield_different_scores():
    # Not a hard invariant for a mock, but with 32-bit MD5 slice the
    # probability of collision on two distinct inputs is negligible.
    a = MockStressDetector().detect(b"payload one" * 10)
    b = MockStressDetector().detect(b"payload two is different" * 10)
    assert a["stress_score"] != b["stress_score"]


def test_rejects_non_bytes():
    detector = MockStressDetector()
    with pytest.raises(TypeError):
        detector.detect("not bytes")  # type: ignore[arg-type]


def test_rejects_empty_bytes():
    detector = MockStressDetector()
    with pytest.raises(ValueError):
        detector.detect(b"")


def test_simulated_latency_nonzero():
    import time

    detector = MockStressDetector()
    start = time.perf_counter()
    detector.detect(b"x" * 16)
    elapsed = time.perf_counter() - start
    # Mock sleeps 0.1s; allow jitter but require clearly > 0.
    assert elapsed >= 0.05


def test_seeded_output_is_reproducible():
    a = MockStressDetector(seed=42).detect(b"anything")
    b = MockStressDetector(seed=42).detect(b"something else entirely")
    # With an explicit seed the score should not depend on the bytes.
    assert a["stress_score"] == b["stress_score"]
