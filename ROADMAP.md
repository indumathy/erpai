# erpai roadmap — Invoice Intelligence

erpai is a learning project with a clear purpose: cover the **full AI-engineering stack end to end**
on one realistic enterprise use case — supplier invoice processing and three-way reconciliation.

Every milestone follows the same rule:

> **Build one feature → learn one AI concept → publish proof** (an eval report, a demo or a write-up).
> A milestone is not done without a measured number. "It works in the demo" does not count.

Design principle (unchanged): money, tax, matching and payment decisions are deterministic code in the ERP.
AI extracts, explains and recommends — it never makes an authoritative financial decision.

## Skill coverage

| Area | Topics | Milestone |
|---|---|---|
| Evaluation | golden datasets, metrics, LLM-as-judge, regression evals | M2 → every milestone |
| LLM fundamentals | tokens, context, temperature, prompting, structured output | M3 |
| Observability & cost | tracing, token/cost metrics, latency | M4 |
| Local / open models | Ollama, quantization, privacy trade-offs | M4 |
| Embeddings & vector search | similarity, thresholds, pgvector, hybrid search | M5 |
| RAG | chunking, reranking, citations, RAG evaluation | M6 |
| Tool calling | tool design, authorization, read vs write tools | M7 |
| Conversational AI | streaming, memory, chat UX | M8 |
| MCP | servers, tools, resources, clients | M9 |
| Agents | agent loop, patterns, human-in-the-loop | M10 |
| Python agent frameworks | LangGraph vs Spring AI | M10 |
| Multimodal | vision/document models | M11 |
| Classic ML | scikit-learn classification, anomaly detection | M12 |
| Fine-tuning | LoRA on a small model vs prompting | M13 |
| Multi-model | routing, fallback, caching, budgets | M14 |
| Security & governance | prompt injection, PII, GDPR, EU AI Act | M15 |
| LLMOps | evals in CI, prompt versioning, deployment, feedback loop | M16 |

## Stage 0 — Groundwork (Kotlin)

| # | Build | Learn | Proof |
|---|---|---|---|
| **M1** | Supplier invoices, supplier contracts (price terms), **three-way matching engine**: `PRICE_MISMATCH`, `QUANTITY_MISMATCH`, `NOT_RECEIVED`, `DUPLICATE_INVOICE`, `TAX_MISMATCH`, `CURRENCY_MISMATCH` with tolerances | Why decisions stay deterministic; the ground truth every later AI feature is measured against | Rules fully unit-tested; exceptions screen in the UI |

Python lessons (`pythonstudy`) are finished in parallel.

## Stage 1 — LLM application basics

| # | Build | Learn | Proof |
|---|---|---|---|
| **M2** | Golden dataset generator (Python): ~200 labeled invoice PDFs with expected fields and expected exceptions | Python, pytest, synthetic data, dataset design | `eval/` dataset in the repo |
| **M3** | Extraction service (FastAPI): classic PDF parsing baseline → LLM structured output | Prompting, structured output, Pydantic, validation-retry | **Eval report #1**: field-level accuracy, baseline vs LLM |
| **M4** | Spring AI behind a `ModelGateway`; tracing + token/cost metrics in Prometheus/Grafana; same extraction on a local model (Ollama) and a GDPR-hosted model (IONOS AI Model Hub) | Observability, cost, local vs cloud vs EU-hosted | Accuracy/cost/latency comparison |

## Stage 2 — Retrieval

| # | Build | Learn | Proof |
|---|---|---|---|
| **M5** | Product matching: invoice line text → ERP product | Embeddings, pgvector, thresholds, hybrid search | Top-1 / top-3 accuracy |
| **M6** | Contract RAG: retrieve the clause behind an exception | Chunking, reranking, citations | Retrieval precision/recall, groundedness |

## Stage 3 — Tools & agents

| # | Build | Learn | Proof |
|---|---|---|---|
| **M7** | "Explain this exception" with tool calling | Tool design, grounding, read-only tools | Tool-call accuracy; first prompt-injection tests |
| **M8** | Accounts-payable chat assistant in the UI | Streaming, conversation memory, chat UX | Demo video |
| **M9** | ERP MCP server (tools + resources), used from Claude Desktop | MCP, per-tool authorization via Keycloak scopes | Demo: external client investigates an invoice |
| **M10** | Investigation agent — single agent first, then patterns, with human approval in the UI; rebuilt in LangGraph for comparison | Agent loop and patterns, human-in-the-loop, two frameworks | End-to-end eval over the golden set; framework comparison write-up |

## Stage 4 — Beyond prompting

| # | Build | Learn | Proof |
|---|---|---|---|
| **M11** | Scanned invoices, photos, delivery notes | Vision / document models | Accuracy: scans vs clean PDFs |
| **M12** | Duplicate/anomaly detection (scikit-learn); cost-account coding with ML vs LLM | Classic ML, features, when *not* to use an LLM | ML vs LLM accuracy and cost |
| **M13** | Fine-tune a small model (LoRA) for cost-account coding; compare zero-shot, few-shot, fine-tuned | Fine-tuning, training-data preparation | Eval report: when fine-tuning pays off |

## Stage 5 — Production

| # | Build | Learn | Proof |
|---|---|---|---|
| **M14** | Model routing (cheap / strong / private), fallback with circuit breaker, caching, budgets | Routing and reliability for AI | Model comparison matrix from own data |
| **M15** | Prompt-injection suite, PII masking + crypto-shredding of invoice personal data, tool authorization, audit of AI decisions (Envers), GDPR / EU AI Act notes | AI security and governance | Security write-up |
| **M16** | Evals as a CI gate, versioned prompts, human corrections fed back into the dataset, Kubernetes deployment | LLMOps | Live deployment and project write-up |

## Language split

- **Kotlin / Spring**: ERP, matching engine, Spring AI, tools, MCP, security, operations.
- **Python (uv)**: extraction service, dataset and evals, classic ML, fine-tuning, notebooks, LangGraph.

## Working rhythm

About 60 % building, 20 % reading (only what the current milestone needs), 20 % writing up.
At ~10 hours per week the whole roadmap is roughly 9–12 months; each milestone is publishable on its own.
