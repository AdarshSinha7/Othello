"""Entry point: run the stress-detection agent on a generated sample WAV.

Running this script:
  1. Creates a short silent .wav file in a temp directory.
  2. Builds the compiled LangGraph with HITL enabled.
  3. Streams the graph until either END or the escalation interrupt.
  4. If interrupted, prompts the operator for approval, then resumes.
  5. Pretty-prints the final structured report.
"""

from __future__ import annotations

import json
import logging
import struct
import sys
import tempfile
import uuid
import wave
from pathlib import Path
from typing import Any

from agent.graph import build_graph


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] %(levelname)-7s %(name)s :: %(message)s",
        datefmt="%H:%M:%S",
    )


def create_dummy_wav(
    path: Path,
    duration_s: float = 1.0,
    sample_rate: int = 16000,
) -> Path:
    """Write a valid silent 16-bit mono WAV to ``path`` and return it."""
    n_frames = int(duration_s * sample_rate)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)  # 16-bit PCM
        w.setframerate(sample_rate)
        silence = struct.pack("<" + "h" * n_frames, *([0] * n_frames))
        w.writeframes(silence)
    return path


def _prompt_escalation_approval() -> bool:
    """Block on stdin for a y/n answer. Defaults to ``True`` on EOF."""
    try:
        answer = input(
            "\n[HITL] HIGH STRESS DETECTED. Approve escalation? (y/n): "
        )
    except EOFError:
        # When run non-interactively (pipes, CI), default to approving so
        # the full escalation path is exercised end-to-end.
        print("(no tty — defaulting to 'y')")
        return True
    return answer.strip().lower().startswith("y")


def run_once(app: Any, audio_path: Path) -> dict:
    """Run the graph to completion on ``audio_path`` and return the report."""
    thread_id = str(uuid.uuid4())
    config = {"configurable": {"thread_id": thread_id}}
    initial_state = {
        "audio_input": {"path": str(audio_path)},
        "metadata": {},
        "errors": [],
        "recommendations": [],
    }

    app.invoke(initial_state, config)
    snapshot = app.get_state(config)

    # If the graph paused at interrupt_before escalation_node, handle HITL.
    if snapshot.next and "escalation_node" in snapshot.next:
        approved = _prompt_escalation_approval()
        if approved:
            print("[HITL] escalation approved — resuming.")
            app.invoke(None, config)
        else:
            print("[HITL] escalation denied — skipping to report.")
            # Treat escalation_node as having run but produced no alert.
            # LangGraph then follows the edge from escalation_node to
            # report_node, satisfying "skip to report_node with
            # escalated=False" without executing escalation_node.
            app.update_state(
                config,
                {
                    "escalation_alert": None,
                    "decision": "report",
                },
                as_node="escalation_node",
            )
            app.invoke(None, config)

    final_state = app.get_state(config).values
    return final_state.get("final_report") or {}


def main() -> int:
    _configure_logging()

    with tempfile.TemporaryDirectory() as tmp:
        sample_path = create_dummy_wav(Path(tmp) / "sample.wav")
        app = build_graph(use_hitl=True)
        report = run_once(app, sample_path)

        print("\n" + "=" * 60)
        print("FINAL REPORT")
        print("=" * 60)
        print(json.dumps(report, indent=2, default=str))
        print("=" * 60)
        print(
            f"Total pipeline latency: "
            f"{report.get('pipeline_latency_ms', 0.0):.3f} ms"
        )
        print("=" * 60)

    return 0


if __name__ == "__main__":
    sys.exit(main())
