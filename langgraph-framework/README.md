# LangGraph Stress-Detection Agent

A production-grade, stateful audio-stress-detection agent built with
[LangGraph](https://langchain-ai.github.io/langgraph/). This is the LangGraph
entry in a Motorola Solutions framework-comparison project whose goal is to
evaluate how different agent frameworks handle the same real-world pipeline:

> *Ingest audio → preprocess → run an ML model → make a decision → either
> escalate (with a human in the loop), recommend, or simply report.*

The same pipeline will be re-implemented in at least one other framework so
we can compare ergonomics, observability, checkpointing, and HITL support on
a like-for-like basis.

---

## What this is (and what it is not)

- ✅ **Is**: a fully-wired LangGraph `StateGraph` with seven nodes, one
  conditional edge, retry logic, timing, structured logging, in-memory
  checkpointing, and a human-in-the-loop break point before escalation.
- ✅ **Is**: runnable out of the box — `python main.py` generates a silent
  WAV and runs the pipeline end-to-end.
- ❌ **Is not**: connected to a real model. The detector is mocked. A clean
  `StressDetectorBase` abstraction makes swapping in wav2vec2 a one-file
  change (see "Swapping in the real model" below).

---

## Setup

```bash
cd langgraph-framework
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Tested on Python 3.10+.

---

## Running

### Run the demo pipeline
```bash
python main.py
```

### Run the tests
```bash
pytest tests/ -v
```

---

## Architecture

```
                       ┌────────────────┐
                       │     START      │
                       └───────┬────────┘
                               │
                               ▼
                       ┌─────────────────────┐
                       │ audio_ingestion     │  validate, normalize to bytes
                       │ (max 10 MB, .wav)   │
                       └───────┬─────────────┘
                               │
                               ▼
                       ┌─────────────────────┐
                       │ preprocessing       │  mock MFCC, 40 features
                       └───────┬─────────────┘
                               │
                               ▼
                       ┌─────────────────────┐
                       │ stress_analysis     │  retry x3, expo backoff
                       │ (StressDetectorBase)│
                       └───────┬─────────────┘
                               │
                               ▼
                       ┌─────────────────────┐
                       │ decision            │  threshold → routing key
                       └───────┬─────────────┘
                               │  conditional edge
           ┌───────────────────┼────────────────────────┐
           │ score ≥ 0.7       │ 0.4 ≤ score < 0.7      │ score < 0.4
           ▼                   ▼                        │
  ┌────────────────┐    ┌────────────────┐              │
  │ [HITL pause]   │    │ recommendation │              │
  │ interrupt_     │    │ 3 wellness tips│              │
  │ before         │    └───────┬────────┘              │
  │ escalation     │            │                       │
  └────────┬───────┘            │                       │
           │ approve / deny     │                       │
           ▼                    │                       │
  ┌────────────────┐            │                       │
  │ escalation     │            │                       │
  │ HIGH severity  │            │                       │
  └────────┬───────┘            │                       │
           │                    │                       │
           └────────────────────┴───────────┬───────────┘
                                            ▼
                               ┌────────────────────────┐
                               │ report                 │
                               │ aggregated final dict  │
                               └──────────┬─────────────┘
                                          │
                                          ▼
                                       ( END )
```

Every node writes its latency to `state["metadata"]` and captures its own
exceptions into `state["errors"]` — the graph is guaranteed to reach
`report_node` regardless of what fails upstream.

---

## Project structure

```
langgraph-framework/
├── agent/
│   ├── __init__.py
│   ├── graph.py          # StateGraph wiring + compile()
│   ├── nodes.py          # all seven node functions
│   ├── state.py          # TypedDict state
│   ├── edges.py          # conditional-edge routing
│   └── checkpointer.py   # MemorySaver factory
├── models/
│   ├── __init__.py
│   ├── base.py           # StressDetectorBase (ABC)
│   └── mock_detector.py  # MockStressDetector
├── shared/
│   ├── __init__.py
│   └── metrics.py        # NodeTimer, latency helpers
├── tests/
│   ├── __init__.py
│   ├── test_nodes.py
│   ├── test_graph.py
│   └── test_mock_detector.py
├── main.py
├── requirements.txt
└── README.md
```

---

## Swapping in the real wav2vec2 model

The agent only ever talks to the `StressDetectorBase` interface, so plugging
in a real model is a three-step change:

1. **Create a new file** under `models/`, e.g. `wav2vec2_detector.py`:

    ```python
    from models.base import StressDetectorBase, DetectorResult

    class Wav2Vec2StressDetector(StressDetectorBase):
        def __init__(self, model_path: str, device: str = "cpu"):
            import torch
            from transformers import Wav2Vec2ForSequenceClassification, Wav2Vec2Processor
            self.processor = Wav2Vec2Processor.from_pretrained(model_path)
            self.model = Wav2Vec2ForSequenceClassification.from_pretrained(model_path).to(device)
            self.device = device

        def detect(self, audio_bytes: bytes) -> DetectorResult:
            import io, soundfile as sf, torch
            wav, sr = sf.read(io.BytesIO(audio_bytes))
            inputs = self.processor(wav, sampling_rate=sr, return_tensors="pt").to(self.device)
            with torch.no_grad():
                logits = self.model(**inputs).logits
            probs = logits.softmax(dim=-1).squeeze().tolist()
            score = float(probs[1])                       # P(stressed)
            confidence = float(max(probs))
            return {
                "stress_score": score,
                "confidence": confidence,
                "label": self.score_to_label(score),
            }
    ```

2. **Swap it into `agent/nodes.py`**:

    ```python
    # was:
    # _detector: StressDetectorBase = MockStressDetector()
    from models.wav2vec2_detector import Wav2Vec2StressDetector
    _detector: StressDetectorBase = Wav2Vec2StressDetector(
        model_path="path/to/your/finetuned/model",
    )
    ```

3. **Add `torch`, `transformers`, and `soundfile`** to `requirements.txt`.

No graph code, no state, no tests need to change. The retry logic in
`stress_analysis_node` will even cover transient CUDA/loading failures for
free.

---

## How the HITL break point works

- The graph is compiled with `interrupt_before=["escalation_node"]`.
- When a high-stress score would route to `escalation_node`, the graph
  pauses *before* executing it. `main.py` detects this via
  `app.get_state(config).next` and prompts the operator.
- **Approve**: `app.invoke(None, config)` resumes execution from the pause
  point, running `escalation_node` → `report_node` → END.
- **Deny**: `app.update_state(config, {"escalation_alert": None,
  "decision": "report"}, as_node="escalation_node")` writes a
  no-op completion of `escalation_node` to the checkpoint, then
  `invoke(None, config)` follows the outgoing edge to `report_node`, so the
  final report has `escalated=False`.

---

## Known limitations & honest engineering notes

**LangGraph-specific limitations as of this implementation:**

- **State replacement semantics can bite**: With a plain `TypedDict`, every
  key returned by a node *replaces* the corresponding state key. Accumulating
  values (e.g. `errors`) means each node has to read-append-return the
  full list. The alternative — `Annotated[list, operator.add]` reducers —
  works but makes it easy to accidentally *duplicate* items if a node
  re-emits an entry. We went with manual list management for transparency.

- **MemorySaver is in-process and ephemeral**: fine for demos and tests,
  but unusable across process restarts or horizontal scaling. A production
  deployment needs `SqliteSaver`/`PostgresSaver` (or the async equivalents).

- **`interrupt_before` resumption is stateful**: resuming requires the same
  `thread_id` *and* the same compiled graph. If the operator takes an hour
  to answer and the service restarts in the meantime, MemorySaver loses the
  pause and the run is lost. Durable checkpointing fixes this.

- **The "skip escalation" path is a bit of a trick**: LangGraph doesn't have
  a first-class "jump to another node on deny" API inside an
  `interrupt_before`. We use `update_state(..., as_node="escalation_node")`
  to forge a no-op completion and let the natural outgoing edge carry us
  to `report_node`. It works and is supported, but it would be cleaner if
  LangGraph offered a direct `Command.goto("report_node")` for this case.

- **Conditional edges return strings, not nodes**: easy to mistype a key and
  get a runtime error only on the escalate path. We centralize the mapping
  in `edges.py` so the three valid outputs are in one place.

- **Schema for state is validated loosely**: `TypedDict` doesn't enforce
  types at runtime. Malformed state only surfaces when a node actually
  reaches for a field. Pydantic state schemas would catch this earlier.

**Versus other frameworks** (notes to compare against):

- LangGraph's strong suit is the explicit graph topology + first-class
  checkpointing + HITL. If your framework treats agents as a loop of
  tool calls, you lose the topology clarity — it's harder to draw an ASCII
  diagram of what it actually does.
- LangGraph gives you nothing for free on observability. Every node here
  had to opt in to timing/logging. A framework with built-in spans/traces
  would show a clear win on this axis.
- The escalation-HITL interaction is more verbose here than it would be in
  a framework where pausing is native to every node (not just a compile-
  time list of names).

---

## What the console output looks like

See the comment block in `main.py` and the "Expected output" section in
the top-level project summary.
