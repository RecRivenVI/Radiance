# Codex task archive index

This directory records the durable project information extracted from completed Codex tasks before
those tasks are archived in the Codex app. It is an index and interpretation layer, not a copy of
chat history. Detailed evidence remains in the linked research, ledger, roadmap, and historical
artifact records.

Archiving a task means that it no longer contains active work. It does not turn historical build
results into runtime acceptance, revive superseded plans, or make old workspace snapshots describe
the current checkout.

| Task | Original title | Effective scope | Extraction | Archive status |
| --- | --- | --- | --- | --- |
| [`01a0a13c-7ef7-7621-89e0-a883e5086fa9`](01a0a13c-7ef7-7621-89e0-a883e5086fa9.md) | 全面审计 Radiance 分叉差异 | Fork audit, behavior decisions, diagnostic cleanup, compatibility implementation coordination, and early native-crash investigation | Extracted 2026-09-21 | Archived 2026-09-21 |
| [`01a0a3f6-dc2a-7e41-af74-d1ddde4adcd4`](01a0a3f6-dc2a-7e41-af74-d1ddde4adcd4.md) | 修复 Radiance 原生渲染崩溃 | Focused NVIDIA-driver access-violation and descriptor-lifetime investigation | Extracted 2026-09-21 | Archived 2026-09-21 |
| [`01a0a26e-a9c8-7ac0-87a9-862b84dd0bd6`](01a0a26e-a9c8-7ac0-87a9-862b84dd0bd6.md) | 核对 Radiance 与 MCVR 进度 | Compatibility checkpoint, evidence boundary, and first-client acceptance triage | Extracted 2026-09-21 | Archived 2026-09-21 |
| [`01a08c26-4fb4-76a3-a968-75c8680d4fe6`](01a08c26-4fb4-76a3-a968-75c8680d4fe6.md) | 了解 Radiance 与 MCVR 状态 | Repository reset, upstream-shaped port, Early Window, rebuild-cost, and audit direction | Extracted 2026-09-21 | Archived 2026-09-21 |
| [`019ff1cf-fec9-7e90-9a71-e41d5cf31d51`](019ff1cf-fec9-7e90-9a71-e41d5cf31d51.md) | 梳理 Radiance 与 MCVR 联动上下文 | Broad rendering-integration history, PT semantics, reload, Ponder, and RR/device-loss investigation | Extracted 2026-09-21 | Archived 2026-09-21 |
| [`019fde4f-872e-7200-aad3-31b00811f843`](019fde4f-872e-7200-aad3-31b00811f843.md) | 检查并重置 Radiance 远程仓库 | Initial NeoForge port, Create/Aeronautics/Sable/Ponder/Veil integration, and early device-loss work | Extracted 2026-09-21 | Archived 2026-09-21 |

## Extraction rules

For each task:

1. Record the exact task ID, original title, extraction date, and whether any turn remains active.
2. Extract durable user decisions and engineering rules. Link the canonical project documents that
   contain the detailed evidence.
3. Label fixed historical snapshots, later-corrected conclusions, canceled plans, and current work
   separately. Never promote an old progress report into current project status.
4. Keep runtime evidence, static findings, automated tests, deployment, and user visual acceptance
   as separate claims.
5. Do not copy raw logs, dumps, binaries, prompts, or routine coordination messages into Git.
6. Use [`../../DEVELOPMENT_LEDGER.md`](../../DEVELOPMENT_LEDGER.md) and
   [`../../ROADMAP.md`](../../ROADMAP.md) for current implementation and pending work. A task
   extraction may point to them but must not silently rewrite them.
