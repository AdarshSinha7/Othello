"""Timing helpers shared across nodes.

``NodeTimer`` is the primary utility — every node wraps its body in one
so that ``state["metadata"][node_name]["latency_ms"]`` is populated
consistently across the pipeline.
"""

from __future__ import annotations

import time
from types import TracebackType
from typing import Iterable, Optional, Type


class NodeTimer:
    """Context manager that records wall-clock latency in milliseconds."""

    __slots__ = ("name", "_start", "elapsed_ms")

    def __init__(self, name: str) -> None:
        self.name = name
        self._start: float = 0.0
        self.elapsed_ms: float = 0.0

    def __enter__(self) -> "NodeTimer":
        self._start = time.perf_counter()
        return self

    def __exit__(
        self,
        exc_type: Optional[Type[BaseException]],
        exc: Optional[BaseException],
        tb: Optional[TracebackType],
    ) -> None:
        self.elapsed_ms = (time.perf_counter() - self._start) * 1000.0
        # Return None to propagate exceptions; node bodies own the try/except.
        return None


def now_ms() -> float:
    """Monotonic clock in milliseconds (for ad-hoc spot measurements)."""
    return time.perf_counter() * 1000.0


def sum_latencies(metadata: dict, node_names: Iterable[str]) -> float:
    """Sum ``metadata[node]['latency_ms']`` for the given node names."""
    total = 0.0
    for name in node_names:
        node_meta = metadata.get(name) or {}
        total += float(node_meta.get("latency_ms", 0.0))
    return total


__all__ = ["NodeTimer", "now_ms", "sum_latencies"]
