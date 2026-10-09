# Rendering facts at the 2026-10-03 frozen checkpoint

Observed on: 2026-10-03; documentation and tool checks finalized on 2026-10-04.

Status: investigating; static inventories completed within the declared scope. This document is
a dated navigation summary, not a rendering specification, full semantic certification or runtime
acceptance. It does not authorize implementation. Sources are current checkout reads, pinned local
references and official API documentation; historical runtime evidence is identified separately.

## Source identity and operating boundary

| Repository | HEAD | Original upstream parent | Committed tree |
| --- | --- | --- | --- |
| Radiance | `e69a1e8ad8bd88663c5918896aa356cbdb42d006` | `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8` | `99af1e44b74255dc0a42d78c6a28aced43ea6a2b` |
| MCVR | `3a59c8b0f35982c4b1e4e3f615974a7942fdc2de` | `9905c81b1999f5845bf66d13501d371c16adf561` | `bf4d9dd126b5959bda17f9293f7ac95a01564f61` |

Each repository retains one Initial port above its original parent. Existing relay edits were
present at intake and were preserved. This task adds documentation and offline analysis tools,
not product changes; the index is unchanged. The portable history mapping is appended to both
ledgers. Do not infer chronology from preserved commit timestamps.

Frozen-period constraints: no product/shader change, game/client start, performance experiment,
staging, commit, push or deployment. Performance remains suspended until the user restores it.
New third-party artifact downloads require source/name/size and prior approval; none occurred.

Canonical direction: [design principles](2026-10-01-path-tracing-design-principles.md).
Communication/proposals: [relay protocol](../relay/README.md) and [idea registry](../relay/2026-10-03-01-user-idea-registry.md).

## Source inputs and static directory

Build target and readonly installed instance agree on Minecraft 1.21.1 / NeoForge 21.1.252,
Create 6.0.10, Aeronautics bundle 1.3.2 and Sable 2.0.5. Embedded identities follow the parents:
Simulated 1.3.2, Flywheel 1.0.6, Veil 4.3.2 (in Sable), Ponder 1.0.82 and SableRapier 2.0.5.

The user explicitly identified `D:/Workspaces/References/minecraft-references` as the historical
reference location and requested reuse. It was read/validated without modification. Exact 252
patched NeoForm sources are distinguished from historical 250 sources; release sources are not
claimed to be byte-for-byte generated from every installed binary. See [A1](../relay/2026-10-03-03-gpt-to-claude-a1-references.md).

Static binary inventory covers 19,214 definitions/156,128 methods and 26,978 relevant sites across 20
archives. All automatic site verdicts remain UNKNOWN pending contextual review. Duplicated classes,
reflection, native code, virtual dispatch and final Mixin transformations limit completeness.
The first-party source inventory contains 1,308 files; all raw hashes and script schemas are recorded
in ignored `run/inventory-20261003/`. See [A2](../relay/2026-10-03-04-gpt-to-claude-a2-draw-intents.md).

## Geometry and presentation actually implemented

- Default world geometry is bilateral. Source cull/winding facts are retained;
  `RADIANCE_WORLD_TWO_SIDED=0` recovers the earlier source-query behavior after restart.
- Authored warped reverse quads use concordant triangulation and paired-sheet ownership.
  Triangle lists are not arbitrarily treated as authored quads. Topology/history provenance remains.
- First-person, priority and other TLAS masks/hit groups still exist. Bilateral traversal does
  **not** establish the future distinction between camera imaging and full light participation.
  Reflection policy is undecided; implement it only after a specification/decision.
- Ponder PT remains archived. Its retained service/tests are not evidence that active Ponder uses
  PT; ordinary Ponder/UI3D follow source raster semantics through Vulkan translation.
- Cuboid/thick debug lines, F3+B/F3+G PBR emission and name-tag background alpha 128 are existing
  user decisions. Hurt/stress overlays are material tint, not independent illumination.

Source path/flags/shader ownership: [A3](../relay/2026-10-03-05-gpt-to-claude-a3-architecture.md).
251 shader files are counted in this task's defined scope, including 58 GLSL includes; 21 are any-hit
stages. Older 261-file counts retain their historical scope. There are 54 high-similarity pairs;
identical or one-line variants are a maintainability finding, not permission to remove a pipeline.

## Confirmed mismatches and ownership findings

1. CloudProxy still multiplies face brightness into source color, contrary to principle §3.
2. ParticleEmissionCapture derives emission from packed-light overrides, also contrary to §3.
3. DlssRR.deinit discards viewport/allocation identity even if SDK resource release reports failure.
4. Streamline viewport allocation has no counter-exhaustion guard; no observed wrap is claimed.
5. Options writes five legacy fields without reading them before rewrite. Pipeline configuration
   is a separate authority, so effective behavior must be resolved before changing persistence.

No fixes were made here. Conditional risks include cached white-texture integer identity and
unverified reload/asynchronous-present/device-loss cleanup combinations. Conversely, framebuffer
IDs are guarded, model/layer caches are capped/aged and descriptor keepalive replaces bindings.
These exclusions are not GPU synchronization proof. See [A4](../relay/2026-10-03-06-gpt-to-claude-a4-lifetimes.md).

## PT modules, APIs and migration

Vanilla and Advanced use different graphs (17 versus 30 declared passes). Advanced adds light
sampling/neighborhood/reservoir/visibility machinery; it is not the cause of a Vanilla-only
observation merely because its own costs exist. 109 pack attributes, 57 YAML attributes and 38
Java/native option declarations are inventoried. Five current screens and shader-pack/pipeline
authorities remain; their replacement awaits the separate RSO handoff. See [A6](../relay/2026-10-03-08-gpt-to-claude-a6-advanced-configuration.md).

Streamline 2.14.1 SR/RR/FG/Reflex, Vulkan FSR SR, XeSS SR, NRD and SHARC have source integration.
Other FG/latency/NR/MFG capability is not implied by dependency presence. RTX 4080 SUPER cannot
certify RTX 50-only MFG. NR's exact public SDK contract is unresolved; it is not NRD. Official API,
hardware/input/present and redistribution boundaries are in [A5](../relay/2026-10-03-07-gpt-to-claude-a5-enhancement-matrix.md).

The complete installable JAR owns SERVICE startup and embeds GAME/native resources. Maven GAME
is developer-only. Public binary licensing approval remains blocked independently of local build
or source publication. No tracked Actions workflow exists in either checkout; hosted automation
outside Git is not certified. Migration into one repository is proposed, not performed, and
requires a history-policy decision. See [A7](../relay/2026-10-03-09-gpt-to-claude-a7-native-migration.md).

## Historical evidence retained, not rerun

The ledger's 2026-10-01 bilateral package and Prism handoff record names main JAR
`70A505C397740982EDCB82BA9C16DD5BC19F35326E72E3EB778FDBA987C6ABC8`, core
`70F9874686F8C3AFB8EDCBA4FB9626F20AAE9CCB3BFA424A0E0E63B8BB01C450` and passive Audit
`93D11DDC5C3DCC27559FEB55200FC304F4974FF2C80811CDEDDE620B8A5F35B3`.
Native 77/77, GAME 250/250, bootstrap 11 passed/2 skipped and bounded normal/Advanced/source-producer
fixtures are **historical recorded results**, not tests executed in this static task. Their earlier
failed boat and misplaced-camera cases remain excluded; delivered artifacts were not relaunched.

This task ran analysis-tool tests only: eight Python fixtures and one ASM fixture exercising nine
API layers, fields/lambda references, source-line/ID preservation and UNKNOWN boundaries. No product
build, GPU/client test, deployment or new visual acceptance. GPU-loss cause, complete G3, generated
frame dynamic visuals, broad parity and long stability remain open. User/Claude decisions remain
in [the relay index](../relay/INDEX.md); reports are not silently promoted to implementation approvals.
