# Radiance development records

This directory is the project-owned source of truth for work that spans the Radiance Java mod
and the sibling MCVR native renderer.

- [`DOCUMENTATION_POLICY.md`](DOCUMENTATION_POLICY.md) defines the document classes, evidence
  vocabulary, update rules, and archive procedure used by both repositories.
- [`audits/2026-09-21-documentation.md`](audits/2026-09-21-documentation.md) records the initial
  documentation audit and the ordered cleanup backlog. It is a dated snapshot, not current status.
- [`audits/2026-09-22-precommit-documentation.md`](audits/2026-09-22-precommit-documentation.md)
  records the complete documentation-batch review, corrections, archive-integrity check, and
  final commit-preparation boundary.
- [`audits/2026-09-22-gpt6-pro-code-review-verification.md`](audits/2026-09-22-gpt6-pro-code-review-verification.md)
  independently verifies the thirteen findings from the external GPT-6 Pro static review against
  the pinned Radiance and MCVR commits and defines their repair and runtime-acceptance boundaries.
- [`DEVELOPMENT_LEDGER.md`](DEVELOPMENT_LEDGER.md) records implemented changes, design decisions,
  evidence, deployment state, and known limits. Entries are append-only once a build has been
  handed to the user; later corrections add a new entry rather than silently rewriting history.
- [`ROADMAP.md`](ROADMAP.md) records explicitly deferred work and the evidence required before it
  can resume.
- [`research/COMPATIBILITY_INVESTIGATIONS.md`](research/COMPATIBILITY_INVESTIGATIONS.md) records
  the Physics Mod and Modern UI compatibility proposals and their static evidence boundaries.
- [`research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md`](research/PT_VISUAL_AND_PERFORMANCE_INVESTIGATIONS.md)
  records the PT visual, material, denoising, transparency, Ponder, and special-effect
  investigations.
- [`research/RENDERPEARL_SODIUM_MIGRATION.md`](research/RENDERPEARL_SODIUM_MIGRATION.md) records the
  proposed RenderPearl/Sodium dual-backend migration and the preliminary feasibility result.
- [`research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md`](research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md)
  consolidates the native access-violation, descriptor-lifetime, device-loss, and driver-state
  evidence while separating retained fixes from superseded experiments.
- [`history/port-artifacts-20260921/`](history/port-artifacts-20260921/) contains the compact
  historical reports recovered from external Port test and investigation directories before their
  large reproducible payloads were recycled.
- [`history/ponder-pt-2026-09-23.md`](history/ponder-pt-2026-09-23.md) records the user-directed
  Ponder PT archive, retained code/evidence, and default raster replacement boundary.
- [`research/RASTER_PARITY_AND_PONDER_RETIREMENT.md`](research/RASTER_PARITY_AND_PONDER_RETIREMENT.md)
  assesses raster equivalence evidence, remaining global hook boundaries and proposed isolation
  of Ponder-only work from independently useful renderer fixes.
- [`history/threads/`](history/threads/) indexes completed Codex tasks after their durable decisions,
  evidence boundaries, superseded conclusions, and canonical project records have been extracted.

Agent memory and chat handovers may point here, but must not be the only location of a project
plan, deferred task, renderer contract, or acceptance result.

Each substantial change should record:

1. the observed problem or requested behavior;
2. the final design and rejected approaches that are dangerous to repeat;
3. the exact Radiance and MCVR source areas involved;
4. static, build, automated, runtime, and user-visual evidence as separate claims;
5. the deployed artifact hash, or an explicit statement that deployment is pending;
6. remaining risks and the next acceptance step.

- [Radiance Audit](../Modules/RadianceAudit/README.md) documents the separately installed diagnostic
  module, build tasks, controls, experiment guards and retained native capture boundaries.
- [Render-call inventory and upstream benchmarks](research/RENDER_CALL_INVENTORY_AND_BENCHMARKS.md)
  fixes the static-enumeration boundary, the two upstream artifacts and the common-metrics benchmark
  design. Implementation/preflight evidence belongs to the linked development-ledger entry.
