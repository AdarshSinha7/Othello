"""LangGraph StateGraph definition and compilation.

Graph shape::

    START -> audio_ingestion_node -> preprocessing_node -> stress_analysis_node
          -> decision_node --(conditional)-->
                 escalation_node   ---\\
                 recommendation_node ->->  report_node -> END
                 report_node       ---/

When ``use_hitl=True`` the graph is compiled with
``interrupt_before=["escalation_node"]`` so that a human can approve or
veto the escalation before the alert is created.
"""

from __future__ import annotations

import logging
from typing import Any

from langgraph.graph import END, START, StateGraph

from .checkpointer import get_checkpointer
from .edges import route_after_decision
from .nodes import (
    audio_ingestion_node,
    decision_node,
    escalation_node,
    preprocessing_node,
    recommendation_node,
    report_node,
    stress_analysis_node,
)
from .state import AgentState

logger = logging.getLogger("stress_agent")


def build_graph(use_hitl: bool = True) -> Any:
    """Build and compile the stress-detection graph.

    Args:
        use_hitl: When True (default), insert an ``interrupt_before`` on
            ``escalation_node`` so callers can prompt a human operator.
            Tests disable this to run the graph non-interactively.

    Returns:
        A compiled LangGraph application.
    """
    graph = StateGraph(AgentState)

    graph.add_node("audio_ingestion_node", audio_ingestion_node)
    graph.add_node("preprocessing_node", preprocessing_node)
    graph.add_node("stress_analysis_node", stress_analysis_node)
    graph.add_node("decision_node", decision_node)
    graph.add_node("escalation_node", escalation_node)
    graph.add_node("recommendation_node", recommendation_node)
    graph.add_node("report_node", report_node)

    graph.add_edge(START, "audio_ingestion_node")
    graph.add_edge("audio_ingestion_node", "preprocessing_node")
    graph.add_edge("preprocessing_node", "stress_analysis_node")
    graph.add_edge("stress_analysis_node", "decision_node")

    # Conditional fan-out after decision_node. The mapping keys are the
    # strings returned by ``route_after_decision``; the values are the
    # node names they route to.
    graph.add_conditional_edges(
        "decision_node",
        route_after_decision,
        {
            "escalation_node": "escalation_node",
            "recommendation_node": "recommendation_node",
            "report_node": "report_node",
        },
    )

    # All three branches converge on report_node.
    graph.add_edge("escalation_node", "report_node")
    graph.add_edge("recommendation_node", "report_node")
    graph.add_edge("report_node", END)

    checkpointer = get_checkpointer()
    compile_kwargs: dict[str, Any] = {"checkpointer": checkpointer}
    if use_hitl:
        compile_kwargs["interrupt_before"] = ["escalation_node"]

    compiled = graph.compile(**compile_kwargs)
    logger.info(
        "graph compiled hitl=%s nodes=%d",
        use_hitl,
        7,
    )
    return compiled


__all__ = ["build_graph"]
