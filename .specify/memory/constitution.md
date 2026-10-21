<!--
Sync Impact Report (temporary review material; remove before committing)
Version change: uninitialized scaffold → 1.0.0 (initial adoption)
Modified principles: replaced five unnamed scaffold slots with:
- I. Secure Handling of Untrusted Projects
- II. Accurate and Explainable Dependency Analysis
- III. Automated Verification as a Delivery Gate
- IV. Deterministic Core, Optional AI Assistance
- V. Deliverable Scope for Two Students
Added sections: Technology and Architecture Constraints; Development Workflow and Quality Gates;
completed Governance.
Removed sections: none; generic scaffold instructions replaced with project rules.
Follow-up TODOs: none.
-->
# ChangeLens AI Constitution

ChangeLens AI — Intelligent Code Change Impact Analyzer — helps developers inspect potential
change impact in Java projects through static dependency analysis and interactive visualization.
Its results describe evidence within the supported analysis model, not guaranteed runtime behavior.

## Core Principles

### I. Secure Handling of Untrusted Projects

Uploaded ZIP archives and their contents MUST be treated as untrusted input. Before extraction,
the backend MUST validate archive structure and enforce configured limits on compressed upload
size, entry count, per-entry expanded size, total expanded size, and processing time. Expanded
byte limits MUST also be enforced while reading; archive metadata alone is insufficient.
Extraction MUST reject absolute paths, traversal outside the upload's isolated directory,
symlinks, conflicting entries, and unsupported archive formats. Nested archives MUST NOT be
recursively extracted. Temporary data MUST be cleaned up after success, failure, or timeout,
with recovery cleanup for interrupted processing.

The service MUST NOT execute uploaded code, build scripts, annotation processors, or project
commands, and MUST NOT fetch dependencies specified by an upload. Clients MUST receive safe,
actionable errors without internal paths or stack traces. Source contents, credentials, and
API keys MUST NOT appear in logs. These constraints protect the host while retaining a useful
static analysis workflow.

### II. Accurate and Explainable Dependency Analysis

JavaParser MUST provide syntax analysis; dependency detection MUST use parsed structure and
available symbol information rather than text matching alone. Each supported dependency kind
MUST have documented semantics and fixture-based expected results. Symbol identities MUST
distinguish packages, declaring types, and overloaded method signatures where applicable.
Unresolved symbols, parse failures, external dependencies, and unsupported Java constructs
MUST be reported explicitly; the analyzer MUST NOT fabricate resolved edges or silently claim
complete coverage.

The dependency model MUST be a directed graph with the convention `A → B` meaning that A
depends on B. Impact propagation MUST use reverse breadth-first search from the selected
changed nodes to find transitive dependents. Traversal MUST terminate on cycles, deduplicate
nodes, distinguish direct from transitive impact, and retain dependency paths explaining
results. The same input and configuration MUST produce stable results. Static reachability
MUST be presented as potential impact, with supported analysis boundaries visible to users.

### III. Automated Verification as a Delivery Gate

Every delivered behavior change MUST include automated tests proportional to its risk; bug
fixes MUST include a regression test. Security checks and graph correctness MUST have tests
before or alongside implementation and MUST pass before merge. Required cases include ZIP
traversal and expansion attacks, invalid uploads, cleanup on failure, parser failures,
unresolved and overloaded symbols, edge direction, cycles, disconnected nodes, multiple
change seeds, and direct versus transitive impact.

Unit tests MUST verify parser and graph behavior against small, independently specified
fixtures. Integration tests MUST verify upload-to-analysis API behavior and PostgreSQL
persistence where used. Frontend tests MUST cover result rendering, graph interactions, and
error states. A small end-to-end smoke test MUST exercise the main user journey. External
LLM calls MUST be replaced by controlled test doubles in required automated checks. Passing
tests, backend compilation, frontend type checking, and production builds MUST gate delivery.

### IV. Deterministic Core, Optional AI Assistance

Uploading, parsing, building the dependency graph, computing impact, and viewing results MUST
work without an LLM API or credentials. Optional LLM output MUST be labeled as generated
explanation and MUST NOT create or override dependency edges, impact sets, or analysis facts.
Failures, timeouts, or disabled AI access MUST leave the core workflow usable.

Sending source code or source-derived context to an external LLM MUST require explicit user
opt-in for that analysis and clear disclosure of the data sent. Requests MUST minimize source
content, use server-side secrets, and enforce request size, timeout, and cost limits. Uploaded
text MUST be treated as data, never as trusted instructions or authorization to invoke tools.
This keeps reproducibility and privacy independent of an optional external service.

### V. Deliverable Scope for Two Students

The project MUST prioritize one complete workflow: upload a bounded Java project ZIP, inspect
its dependencies, select changed code elements, compute potential impact, and explore the
results. The initial supported Java subset and change-selection method MUST be explicit in
the specification; unsupported behavior MUST remain visible rather than implied.

Work MUST be divided into small, independently verifiable milestones sized for two student
contributors. A single backend application, a single frontend, and PostgreSQL MUST be the
default architecture. New infrastructure or major capabilities MUST include a documented
need, effort estimate, and tradeoff against the remaining schedule. Multi-language analysis,
full runtime resolution, microservices, distributed processing, automatic code modification,
and enterprise integrations MUST remain outside the initial scope unless separately approved
through planning. Optional AI MUST follow a working, tested deterministic workflow.

## Technology and Architecture Constraints

- The backend MUST use Java 21 and Spring Boot, with JavaParser for Java source analysis.
- The frontend MUST use React and TypeScript, with React Flow for directed graph visualization.
- Dependency storage and traversal MUST use an explicit directed graph model; reverse BFS
  MUST implement impact propagation. PostgreSQL MUST store durable application data, with
  versioned schema migrations. A separate graph database is outside the initial architecture.
- Upload handling, parsing, graph construction, impact traversal, persistence, and optional AI
  integration MUST have clear module boundaries so core analysis can be tested independently.
- APIs MUST validate inputs, expose stable node identities and structured errors, and carry
  analysis diagnostics and completeness information through to the frontend.
- Upload limits, graph rendering limits, supported project sizes, and analysis time budgets
  MUST be defined before accepting the associated feature. A representative bounded project
  fixture MUST verify those budgets; oversized inputs MUST fail with an explicit explanation.
- Dependency versions MUST be pinned through build manifests or lockfiles as appropriate.
  Configuration MUST be externalized, and secrets MUST NOT be committed or shipped to clients.

## Development Workflow and Quality Gates

Each feature specification and plan MUST identify its supported behavior, acceptance criteria,
security implications, analysis limitations, test strategy, and estimated effort. Tasks MUST
fit the agreed student-project milestone. Features MUST NOT be accepted solely because a
visual demonstration succeeds.

Changes MUST be reviewed by the other contributor before merge. Review MUST verify these
principles, test evidence, error handling, and any API or schema changes. Required automated
checks MUST pass from documented commands in a clean environment. Changes to dependency
semantics MUST update fixtures and user-facing limitations together.

The project MUST document local setup, configuration, test commands, supported inputs, and
known limitations as implementation is delivered. Logs MUST provide processing stage,
duration, and diagnostic identifiers without exposing source contents. Each milestone MUST
include a reproducible demonstration of its acceptance criteria; deferred work MUST be
recorded explicitly rather than added implicitly to the current milestone.

## Governance

This constitution takes precedence over conflicting project specifications, plans, tasks,
and implementation practices. All feature plans and merge reviews MUST check compliance.
Conflicts MUST be resolved before the affected work is accepted.

Amendments MUST document the proposed text, rationale, affected artifacts, and any migration
or follow-up work. Both contributors MUST approve amendments before adoption. Any exception
MUST be recorded with scope, rationale, compensating controls, and an expiry or removal task;
an exception that redefines a principle MUST instead amend this constitution.

Versions MUST follow semantic versioning: MAJOR for incompatible principle removals or
redefinitions, MINOR for new principles or materially expanded guidance, and PATCH for
clarifications without semantic change. Every adopted amendment MUST update the version and
last-amended date while preserving the original ratification date. Dependent artifacts MUST
be reviewed for consistency when an amendment is adopted; this constitution workflow itself
changes only this document.

**Version**: 1.0.0 | **Ratified**: 2026-10-21 | **Last Amended**: 2026-10-21
