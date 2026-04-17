"""Conditional-edge routing logic.

Kept in its own module so the graph wiring in ``graph.py`` stays
declarative: the routing *policy* lives here, the *topology* lives there.
"""

from __future__ import annotations

import logging

from .state import AgentState

logger = logging.getLogger("stress_agent")


def route_after_decision(state: AgentState) -> str:
    """Return the name of the next node based on ``state['decision']``.

    The three return values correspond to the three branches registered in
    ``graph.add_conditional_edges`` — keep them in sync.
    """
    decision = state.get("decision")
    if decision == "escalate":
        return "escalation_node"
    if decision == "recommend":
        return "recommendation_node"
    if decision == "report":
        return "report_node"

    # Unknown decision: fail safe to the report path rather than stall.
    logger.error(
        "route_after_decision: unknown decision %r — falling back to report_node",
        decision,
    )
    return "report_node"


__all__ = ["route_after_decision"]
