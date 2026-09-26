# Documentation policy

This policy applies to the maintained documentation in the Radiance and MCVR repositories. Its
purpose is to keep product behavior, implementation history, future plans, research, and archived
evidence distinguishable after long development and multiple task handoffs.

## Authority and document classes

Each fact should have one canonical home. Other documents link to it instead of copying its current
state.

| Class | Canonical location | Responsibility | Update model |
| --- | --- | --- | --- |
| User-facing overview | Root `README.md` and translated variants | Current installation, supported platforms/features, required runtime files, build entry points, and user-visible limitations | Update when released or development-branch behavior changes; translations retain semantic parity |
| Documentation index | `docs/README.md` | Map readers to the authoritative document for each kind of information | Keep short and current |
| Implementation ledger | `docs/DEVELOPMENT_LEDGER.md` in each repository | Implemented behavior, design decisions, exact evidence, deployment identity, corrections, and known limits | Append a new dated entry after handoff; do not rewrite an accepted historical entry to look as if it was always correct |
| Roadmap | Radiance `docs/ROADMAP.md` | User-approved or explicitly deferred future work, constraints, and acceptance gates | Update status when the user changes priority or implementation evidence changes the plan |
| Research | `docs/research/` | Static investigation, external-source findings, feasibility, alternatives, and preliminary implementation plans | Pin observations to a date, source version, commit, or artifact; never present a proposal as implemented |
| Dated audit | `docs/audits/` | A bounded review of documentation, code, compatibility, or evidence at a fixed snapshot | Immutable after closure except for an explicit correction note or a link to a superseding audit |
| Thread extraction | `docs/history/threads/` | Durable decisions and evidence pointers extracted before a completed Codex task is archived | Append one file per task; exclude raw transcripts and routine coordination |
| Imported history | `docs/history/port-artifacts-*` | Compact historical reports preserved from external workspaces | Treat as read-only evidence; add an external index or correction rather than normalizing old content in place |
| Legal and third-party notices | `LICENSE*`, `NOTICE*`, third-party manifests | License text, attribution, redistribution terms, and vendor notices | Change only for a verified dependency or licensing reason; editorial cleanup rules do not apply |

Radiance owns cross-repository product decisions and the overall roadmap. MCVR records the native
implementation evidence required to review that repository independently. MCVR links back to the
Radiance policy rather than maintaining a divergent copy.

## Status vocabulary

Every roadmap item, research conclusion that may be mistaken for implementation, and ledger entry
with incomplete acceptance must use an explicit status. Prefer one of these terms:

| Status | Meaning |
| --- | --- |
| `proposed` | A design or product idea; no implementation authorization is implied |
| `investigating` | Evidence is being collected; no final technical conclusion |
| `deferred` | Preserved for later work by user decision |
| `implemented` | Source exists in the identified revision or worktree |
| `build-verified` | The stated compile/package gates passed |
| `automated-verified` | Named deterministic tests passed; scope must be stated |
| `runtime-observed` | A real client or GPU run exercised the named path |
| `visually-accepted` | The user accepted the named visible cases |
| `deployed` | An identified artifact was copied to an identified instance; this alone says nothing about launch or visuals |
| `superseded` | A later implementation or decision replaces this record; link to it |
| `archived` | Historical evidence retained with no active work |

Several terms may apply at once. Write the strongest evidence actually obtained, not the intended
completion state. Avoid unqualified words such as `complete`, `fixed`, `verified`, `working`, or
`supported`.

## Evidence levels

Claims must identify their evidence level and practical boundary:

1. `static`: source, bytecode, diff, API, specification, or artifact inspection;
2. `build`: compilation, linking, shader compilation, packaging, or symbol/export checks;
3. `automated`: deterministic unit, contract, fixture, or integration tests;
4. `gpu`: real Vulkan/OpenGL command execution or validation-layer evidence;
5. `runtime`: a real Minecraft client reached and exercised the named state;
6. `visual`: the user inspected and accepted the named presentation;
7. `deployment`: source and deployed artifact identity, including path and hash.

A later level is not implied by an earlier one. Process existence, a Java PID, an accepted present,
a counter, a hidden-window probe, or a Gradle exit code is not client readiness. A deployed hash is
not launch evidence. One non-crashing run is not a root-cause proof.

## Required entry shape

New ledger and audit entries should contain, in this order:

1. ISO date and concrete subject in the heading;
2. status and evidence levels;
3. affected repository, version, commit/worktree snapshot, and source areas;
4. observed problem or requested behavior;
5. final design and important rejected approaches;
6. validation results with counts, duration, configuration, and limitations where relevant;
7. deployment path and SHA-256 when an artifact was deployed;
8. remaining risks and the next acceptance action;
9. `Supersedes` or `Superseded by` links when the entry changes an older conclusion.

Use one H1 per document. Every independent ledger event uses an H2 heading; H3 headings are internal
parts of that event. Dates use `YYYY-MM-DD`. Use repository-relative links and paths where possible.
Machine-local absolute paths are allowed only when the location itself is operational evidence, such
as a retained backup or deployed instance, and must be labeled non-portable.

## Current facts versus snapshots

- The word `current` must name its reference point: a commit, worktree snapshot, artifact hash, or
  observation date. Do not let a dated dirty-worktree checkpoint silently describe a later commit.
- Research files describe what was observed at their pinned source versions. Later implementation
  state belongs in the ledger.
- Roadmap items link to research for reasoning and to ledger entries for completed portions. They do
  not duplicate long implementation narratives.
- Root README files contain present user-facing behavior. Preserve the upstream document's
  structure, tone, and subject range, but edit stale facts in place. Do not append a fork disclaimer
  instead of correcting text that no longer represents this branch, and do not introduce unrelated
  version lines or product scope merely for completeness.
- Imported history may contain obsolete absolute links or claims. Its archive index explains the
  boundary; active documents must not rely on those links as the only route to evidence.

## Git history and backup policy

Radiance and MCVR retain upstream history followed by exactly one signed `Initial port` commit on
the maintained branch. Later changes are amended into that commit. Every rewrite preserves the
original commit's author and committer names, emails, timestamps with time zones, message, and
upstream parent; a new commit object and SSH signature are expected. Cross-repository records use
one-way references and never require reciprocal SHA backfills or repeated amendments.

User-approved exception, 2026-09-25: split the accumulated work by subject. Amend the unrelated
diagram-rendering corrections and visual-investigation records into `Initial port`, preserving
its metadata. Retain the performance changes in the working trees for a later, separately
authorized `Performance Optimization Test V1` commit in each repository. This exception permits
that additional performance checkpoint; it does not authorize creating it during the split or
change the metadata/signature rules for `Initial port`.

User-approved exception, 2026-09-26: preserve `Initial port` and the existing V1 checkpoints and
create one paired `Performance Optimization Test V2` commit per repository for the accumulated
performance, diagnostic/benchmark tooling and validation records. Use the configured identity,
actual new commit times and SSH signatures, with an English title and Markdown bullet body as in
V1. This is an additive checkpoint, not an amendment or authorization to push/publish. Original
historical metadata remains unchanged; cross-repository references stay one-way.

History-rewrite backups are temporary rollback tools. Before a destructive local or remote update,
create and verify the smallest bundle that can restore the affected refs. Remove superseded
project-maintenance backup refs after the rewrite and recovery bundle have been verified. Keep a
bundle needed for force-push recovery until the remote state is independently verified, then send
it through the Windows Recycle Bin. Do not accumulate a new permanent backup for every amend, and
do not prune reflogs, force garbage collection, empty the Recycle Bin, or remove backups whose
origin or unique evidence value is uncertain.

## Automated runtime and user acceptance locations

User decision, 2026-09-24, applying to the Radiance/MCVR pair:

- Run automated Minecraft clients in isolated, case-specific directories under Radiance `run/`.
  Keep build/unit-test output in its normal build directories. Existing runtime and acceptance
  evidence is retained in place; this rule does not authorize moving or deleting historical runs.
- User decision, 2026-09-25: every automated Minecraft test must start with master volume zero
  (`soundCategory_master:0.0` in the isolated instance's `options.txt`). Apply this equally to all
  benchmark variants and record the configuration; do not change Windows-wide audio or the user's
  manual/Prism settings. Historical measurements keep their original audio configuration.
- Hand off manual acceptance by deploying the matched Radiance JAR and, when needed, its matched
  diagnostic mod to the user's designated Prism instance. The current authorized instance is
  `E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge` (non-portable).
  The user starts and operates it; do not require a separate repository manual instance or script.
- Default Prism handoff changes only the mod artifacts. Preserve its worlds, other mods and
  settings; automated world creation, scripted gameplay and automatic shutdown belong in the
  repository test directories. A later explicit request can change this division of work.
- The agent verifies artifact identity and reads the relevant logs/diagnostics. Record automated
  runs and user observations separately, each with its actual artifact and configuration.

## Authoring templates

Use this shape for a ledger event:

```markdown
## YYYY-MM-DD: Concrete outcome

Status: implemented; build-verified.
Evidence: static, build.
Applies to: Radiance `<commit/worktree>`; MCVR `<commit/worktree>`.
Supersedes: `<link or none>`.
Remaining acceptance: runtime and visual.

### Problem or requested behavior

### Final design and rejected alternatives

### Validation and evidence boundary

### Deployment and remaining work
```

Use this shape for research:

```markdown
# Investigation title

Observed on: YYYY-MM-DD.
Sources: exact repository commits, versions, artifacts, and authoritative external references.
Status: investigating or proposed.

## Question and scope
## Findings
## Proposed design
## Evidence boundary
```

A roadmap item needs a concrete title, status, preserved constraints, a link to its research, and
measurable acceptance gates. It should not include a chronological implementation diary.

## Editing and correction rules

- Preserve user-approved behavior separately from the code that currently implements it.
- When evidence changes a handed-off ledger entry, append a correction with a link to the old entry.
- Move unfinished implementation plans to the roadmap; move source investigation to research; move
  completed-task extraction to thread history.
- Do not keep plans, renderer contracts, acceptance state, or deferred work only in agent memory,
  chat, ignored development directories, or external artifacts.
- Do not copy raw logs, dumps, binaries, generated reports, or full chat transcripts into maintained
  documentation. Preserve compact conclusions, hashes, commands needed for reproduction, and an
  evidence manifest.
- Avoid editing imported historical reports for style, path portability, terminology, or modern
  status. Add a correction/index outside the imported tree if readers could be misled.
- Maintain English as the canonical language for active engineering documents. Root English and
  Chinese user-facing README files must remain semantically equivalent. Historical material retains
  its original language.

## Review and validation

Before committing documentation changes:

- run `git diff --check` in both repositories;
- verify every new or changed repository-relative Markdown link;
- scan active documents for unexplained absolute paths;
- confirm that every status claim names its evidence level;
- check that root `README.md` and `README-CN.md` describe the same current behavior;
- confirm that legal/vendor documents were not changed by editorial cleanup;
- ensure a new current claim does not conflict with a later ledger entry or remain duplicated in a
  research file;
- leave thread extraction changes uncommitted until the requested extraction batch is reviewed.

## Codex task extraction and archival

Before archiving a development task:

1. read all task turns, including older cursor pages;
2. identify scope drift beyond the task title;
3. move durable product decisions or current work into the ledger/roadmap/research document that owns
   them;
4. create one `docs/history/threads/<task-id>.md` extraction with canonical links, historical facts,
   superseded conclusions, and the archive reason;
5. add it to `docs/history/threads/README.md`;
6. verify there is no active turn, pending user approval, or unique evidence left only in the task;
7. archive the task in the Codex app and record the result in the index.

Task archival is reversible and does not delete the conversation. It is not evidence that the
engineering goal was completed.
