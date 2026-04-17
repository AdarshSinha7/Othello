"""Stress-detector model implementations.

Import ``StressDetectorBase`` to plug in a real model; the agent depends
only on that interface.
"""

from .base import DetectorResult, StressDetectorBase
from .mock_detector import MockStressDetector

__all__ = ["StressDetectorBase", "DetectorResult", "MockStressDetector"]
