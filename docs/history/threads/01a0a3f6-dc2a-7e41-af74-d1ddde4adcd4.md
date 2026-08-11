# Task extraction: focused native-rendering crash investigation

## Task identity

- Codex task ID: `01a0a3f6-dc2a-7e41-af74-d1ddde4adcd4`
- Original title: `修复 Radiance 原生渲染崩溃`
- Host: `local`
- Extraction date: 2026-09-21
- Read coverage: all 3 available cursor pages, 23 recorded turns
- Archive result: archived in the Codex app on 2026-09-21
- State at extraction: idle; no active command, approval, or unanswered user request

## Effective scope

The task was a focused continuation of the first real-client acceptance failure. It separated a
native NVIDIA-driver access violation from Java startup failures, applied two valid synchronization
fixes, tried bounded descriptor-lifetime experiments, and established a stricter testing boundary
between the repository instance and the user's Prism instance.

## Durable findings

- Real-client crashes occurred inside `nvoglv64.dll` under indexed draw submission. Title text,
  receiving screens, subtitles, and hotbar items were observed triggers, not proved causes.
- Correcting depth/stencil layout handling and the world-to-HUD depth handoff was necessary but did
  not remove the access violation. The color synchronization correction was independently valid.
- Validation can change timing. One validation-enabled survival or one successful world entry is
  insufficient to establish a fix.
- Headless and fixture tests did not reproduce the real-client failure. They remain useful contract
  checks, not client acceptance.
- Vulkan API dumping was too expensive and unstable for an unbounded run: it produced about
  12.25 GB of output and about 8.6 GiB of working set before the layer failed.
- Agent-controlled client work belongs in timestamped repository `run/` directories. Prism is a
  user-owned manual acceptance environment unless explicitly authorized for a particular action.

The consolidated account and current evidence boundary are in
[`../../research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md`](../../research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md).

## Superseded conclusions

- The first E-02 whole-descriptor-table survival was only an experiment. Later A/B/A work supplied
  stronger evidence and replaced the broad retention with a minimal pipeline-layout/set-layout
  ownership token.
- A later revision in this task tried to retain every descriptor resource generation until table
  retirement. It had no post-change real-client acceptance in this task and is not the current
  source behavior. Current binding ownership replaces the retained resource for the rebound slot.
- Old branch names, hashes, and deployment paths in the task are historical snapshots. Current
  repository state is governed by the development ledgers and Git.

## Canonical records

- [Historical crash synthesis](../../research/CRASH_AND_RUNTIME_INVESTIGATION_HISTORY.md)
- [E-02 retest and minimal-fix report](../port-artifacts-20260921/RadianceCrashInvestigation/20260917-PID15936/e02-retest-20260917/EXPERIMENT.md)
- [Known validation work](../../ROADMAP.md#known-vulkan-validation-findings)

## Archive decision

The unique evidence and corrections are now represented in repository documentation. The task has
no active work and can be archived without treating the crash mechanism as fully explained.
