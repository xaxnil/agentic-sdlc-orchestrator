# Agentic SDLC Orchestrator

A workflow engine that takes a requirement and drives it through the software lifecycle
(requirements, design, implementation, testing, documentation, release readiness) using agents
under governance: explicit dependency graph, human approval gates, bounded retries, fallback,
rollback, safe-stop, policy guardrails, audit trail and reliability metrics.

A small **URL shortener** (create, redirect, click analytics) is included as the use case and as a
standalone service in the same application.

> The orchestrator is deterministic code, not an AI agent. Agents only do the work inside a stage.

## Requirements

- Java 21
- Maven (the included `./mvnw` wrapper is enough)
- Optional: an Anthropic API key to use real model agents

## Quick start

```bash
./mvnw test                      # 53 tests
./mvnw spring-boot:run           # starts on http://localhost:8080
```

Swagger UI: http://localhost:8080/swagger-ui/index.html

Run the three demo scenarios end to end (starts the app if needed, saves evidence in `docs/scenarios/`):

```bash
./scripts/run-scenarios.sh
```

## Agent modes

| Mode | How | Notes |
|---|---|---|
| `simulated` (default) | nothing to configure | deterministic, no network, used by tests and as fallback |
| `claude` | `AGENT_MODE=claude ANTHROPIC_API_KEY=... ./mvnw spring-boot:run` | model used for Requirement, Planning, Architecture, Security and Implementation; other stages use the simulated agent |

Optional: `ANTHROPIC_MODEL` overrides the model. The key is only read from the environment and is
never logged or stored. If the model fails or times out, the engine retries and then falls back
to the simulated agent.

## API

| Endpoint | Purpose |
|---|---|
| `POST /runs` | Start a run: `{"requirement": "...", "type": "GREENFIELD" or "BROWNFIELD"}` |
| `GET /runs` / `GET /runs/{id}` | List runs / run state with stages, artifacts and decisions |
| `POST /runs/{id}/stages/{stage}/approve` | Human approval: `{"actor": "...", "note": "..."}` |
| `POST /runs/{id}/stages/{stage}/reject` | Human rejection, triggers re-planning |
| `POST /runs/{id}/stop` | Safe-stop |
| `GET /runs/{id}/audit` | Immutable audit log |
| `GET /metrics` | Success rate, retries, rollbacks, MTTR, latency |
| `POST /urls`, `GET /{code}`, `GET /urls/{code}/stats` | URL shortener |

## Scenarios

`scripts/run-scenarios.sh` runs and records:

1. **Greenfield**: full lifecycle with both human approvals; a secret in an approval note is blocked.
2. **Brownfield**: adds impact analysis on existing code.
3. **Ambiguous**: "Make it more secure" stops for human clarification, then a design rejection forces re-planning.
4. **Safe-stop**: a run is stopped before any stage starts.

Evidence (full state and audit log of each run) is written to `docs/scenarios/`.

Note: the scenario runs use the deterministic simulated agent, which does not fail, so retry,
rollback and MTTR metrics are zero or null in the recorded evidence. Those paths are covered by
the automated tests (see docs/ARCHITECTURE.md, section 6).

## Project layout

```
src/main/java/dev/let/agentic
  domain/      Run, StageExecution, Artifact, Decision, AuditEvent, StageDefinition
  graph/       StageGraph: declarative DAG, cycle detection, downstream lookup
  engine/      Orchestrator (engine loop), PolicyGuard
  agent/       Agent interface, SimulatedAgent, ClaudeAgent, Anthropic HTTP client
  api/         Run API, response views, error handling
  metrics/     Metrics computed from the audit log
  shortener/   URL shortener service, controller, rate limiter
  config/      Wiring and agent mode selection
scripts/       Scenario runner
docs/          Architecture, summary, scenario evidence
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md): components, graph, control flow, key decisions
- [Engineering summary](docs/SUMMARY.md): plan, risks, trade-offs, validation, assumptions, limitations
