"""MemorySaver setup.

Isolated in its own module because swapping in a durable backend (SQLite,
Postgres, Redis) for production should not require touching ``graph.py``.
"""

from __future__ import annotations

from langgraph.checkpoint.memory import MemorySaver


def get_checkpointer() -> MemorySaver:
    """Return a fresh in-process checkpointer.

    A new instance per graph build keeps test runs independent. In
    production you would return a shared ``MemorySaver()`` (or a
    SqliteSaver/PostgresSaver) from a module-level singleton.
    """
    return MemorySaver()


__all__ = ["get_checkpointer"]
