# Engineering Summary

## Plan and rationale

The goal was a runnable prototype that turns a requirement into a reviewable engineering outcome
with controlled autonomy. The effort went first into the orchestration layer (graph, gates,
approvals, failure handling, auditability), then a small URL shortener as the use case, then
scenarios and documentation.

Order of work: (1) graph and state model, (2) engine with agents and policy, (3) API, metrics,
shortener and model-backed agent, (4) scenarios and documentation. Each step was committed
separately and is covered by tests.

## Artifacts

- Orchestrator: `engine/`, `graph/`, `domain/`
- Agents: `agent/` (simulated and model-backed)
- REST API with OpenAPI/Swagger: `api/`
- Metrics: `metrics/`
- URL shortener: `shortener/`
- 53 automated tests (unit and plain controller tests)
- Scenario runner and recorded evidence: `scripts/`, `docs/scenarios/`

## Scenarios

| Scenario | Requirement | What it shows |
|---|---|---|
| Greenfield | Build the URL shortener | Full graph, parallel branches, two human approvals, secret in a note blocked |
| Brownfield | Add link expiration | Conditional impact analysis stage runs |
| Ambiguous | "Make it more secure" | Ambiguity detected, human clarification recorded, design rejection triggers re-planning |
| Safe-stop | Any run | Run stops and no new stage starts |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Agent output contains secrets | `PolicyGuard` on every output and approval note; message never echoes the secret |
| Model fails, times out or returns nothing | Bounded retries, then fallback agent, then rollback to the last approved checkpoint |
| Runaway or unwanted execution | Safe-stop; human gates before design is built and before release |
| Upstream change leaves stale downstream work | Hash comparison invalidates and re-runs dependents |
| Untrusted requirement text steering a model | Requirement is passed as data with a fixed system prompt; output still passes policy and human gates |
| Shortener abuse (SSRF, open redirect, flooding) | Only http/https, internal and private hosts blocked, per-client rate limit on creation, expiration support |
| API key leakage | Read only from the environment, sent only as a header, `.env` is git-ignored, policy rules detect common key formats |

## Validation

- Every control in the architecture document has at least one test (see section 6 there).
- The shortener has tests for validation, internal-target blocking, expiration, click counting and rate limiting.
- The model-backed agent is tested through a fake client, so tests are deterministic and need no network.
- End-to-end behavior is exercised by `scripts/run-scenarios.sh` against the running API.

## Assumptions

- Single node, single user, in-memory state is acceptable for a prototype.
- A human reviewer is available at the two approval points.
- Agent output is text artifacts (design, code suggestions, tests, docs), not files applied to a repository.

## Limitations

These are known and intentional for the time available:

1. **Agents do not apply or execute code.** The implementation, tests and validation stages produce and check text artifacts. The orchestrator does not compile generated code or run generated tests. The shortener in this repository was written by hand. A production version would give agents a sandbox (branch, build, test runner) and make "tests pass" a real exit gate.
2. **The exit gate is shallow.** It checks that output exists and passes the secret policy. There is no semantic check (compilation, coverage, schema validity).
3. **Policies not implemented:** dependency allow-list, license checks, static analysis, change-size limits.
4. **No API authentication.** Approvals are accepted from anyone who can reach the API and the `actor` field is self-declared, so the audit trail is not tamper-proof or attributable. Production needs authentication, authorization and signed approvals.
5. **State is in memory.** Runs and the audit log are lost on restart and cannot be shared across nodes.
6. **Synchronous execution.** `POST /runs` and approvals return after the engine reaches the next gate; with a real model this can take a while. Production would run asynchronously with a queue.
7. **Rollback is logical.** It resets stage state to the last approved checkpoint; there are no files or deployments to undo yet.
8. **Retries have no backoff.** Failed attempts are retried immediately.
9. **Model output is non-deterministic.** The model-backed agent is tested only through a fake client, not against the real API in the automated suite.
10. **Shortener limits:** codes are sequential (enumerable), DNS rebinding is not handled, the rate limiter is per-node and does not evict old keys, and storage is in memory.
11. **Metrics are computed on demand** from the audit log and are not exported to a monitoring system.

## AI-assisted development notes

The design and code were built with an AI assistant (Claude) as a pair-programming partner. The engineer made the design decisions, reviewed the output, ran the tests and owns the result. Changes were committed incrementally and verified by the test suite.
