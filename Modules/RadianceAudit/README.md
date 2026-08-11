# Radiance Audit

Optional client diagnostics maintained in the Radiance repository. The installed mod ID is
`radiance_audit`; Java packages use `com.radiance.audit`. Identity, author attribution, icon and
project links derive from Radiance's upstream metadata. The Audit-specific name and description
distinguish this maintained diagnostic module; this does not claim upstream shipped the module.

## Build and install

Use Java 21 and the root Gradle wrapper:

```powershell
.\gradlew.bat :radiance-audit:check :radiance-audit:jar -Pmcvr.configuration=RelWithDebInfo
```

Install the resulting `Modules/RadianceAudit/build/libs/RadianceAudit-<version>.jar` separately.
The main Radiance installation does not include Audit. Root project dependencies express Java
compile order; no previously generated `build/classes` directory is used as a dependency.
The Windows collector builds through CMake/Visual Studio and contains only first-party code.
Its C ABI header comes from the matched sibling MCVR checkout. Native collector PDBs remain in
the local build directory. Aftermath is a separate explicit local SDK build; no vendor SDK or
vendor crash-capture DLL is bundled here.

The root build and collector share `mcvr.root`, `mcvr.cmakeGenerator` and `mcvr.configuration`
(default `Release`). The generator uses the existing CMake cache when no override is supplied.
Tests write their working files under `build/test-runtime/`; logs and rotated logs at any module
depth are ignored. Necessary module sources and tests remain visible to Git.

## Modes and controls

- Default `basic`: passive session/route observation; no automated gameplay or native performance
  collector. Detailed draw, screen and chunk probes are opt-in.
- `-Dradiance.audit.mode=off`: no observer or diagnostic Mixins are installed.
- `-Dradiance.audit.chunks=true`, `-Dradiance.audit.drawDetails=true`, and
  `-Dradiance.audit.screenEffects=true`: enable their respective Java probes.
- `-Dradiance.audit.nativePerformance=true`: load the matched first-party collector and aggregate
  resource allocations and host-call timing. These are not whole-process VRAM or generated FPS.
  Allocations made before attachment are absent from per-source inventory; VMA totals include
  host-visible allocations. Reports distinguish overwritten snapshots and dropped events.
- `-Dradiance.audit.capture=true`: authorize existing explicit GPU readback request files.
  This does not automatically capture anything or enable early GPU fault tooling.
- `-Dradiance.audit.nativeLibrary=<absolute-path>`: optional local collector override, with ABI
  checks. Normally its content-addressed copy is extracted from this JAR.

Active experiments require **both** `-Dradiance.audit.experiments=true` and a file named
`.radiance-audit-test-instance` in the process working directory, which must be the isolated
game directory. Then select a single existing `RADIANCE_*` probe. Old environment flags alone
cannot trigger scripted block changes, camera movement or automatic exit. `RADIANCE_AUDIT_SMOKE=1`
checks title-screen readiness and requests normal exit after eight seconds. It is not world or
visual acceptance. Lifecycle G1/G2 additionally require their original one-shot trigger file.
Never enable active probes in the user's Prism instance or a production world.

`RADIANCE_LICHEN_PROBE=1` is a bounded isolated experiment: it places floor/wall/ceiling glow
lichen on black concrete near the copied test player's column at Y=192, teleports the test
camera, records baked/PBR vertices, requests three eight-frame GPU captures and screenshots,
and requests normal shutdown. It requires the experiment gate above and integrated singleplayer;
GPU readbacks additionally require `-Dradiance.audit.capture=true`. It edits the copied world
and does not restore the fixture. Never use this probe on a world that must be preserved.
The probe reports execution only; alpha, lighting and visual findings require separate analysis.

Ordinary installation does not clear OpenAL properties or change renderer options. Audit does
not create an OpenGL context. Without Radiance, only available vanilla observation paths apply;
Radiance-specific native features require the matched renderer.

## Ownership and evidence boundaries

Radiance retains the versioned `api.audit` observer boundary, read-only runtime identity, native
test primitives and real failure/save/close behavior. Observer exceptions detach the observer;
they cannot replace a renderer failure. Active experiment failures still propagate through the
real game failure path. The collector ABI is install-once and process-lived, uses no STL ownership
across DLLs, and pins its Windows module because resources may retire after game-world closure.

The Java ledger bounds open intents (8,192), detail length (4,096 characters) and per-session output
(64 MiB). Asynchronous open work survives frame boundaries. Dropped samples, output exhaustion,
unobserved routes and unresolved work at process end are different evidence states. Zero unknowns
does not prove coverage of unexecuted scenarios. Detailed historical scenario probes remain
experimental and may have their own output policies; the ledger limit is not a process-wide quota.

GPU-owned readback operations, early device-fault/Aftermath hooks and some
explicit native traces remain in MCVR. They are not claimed to have all moved into this JAR.
Early device diagnostics must be selected before SERVICE device creation; attaching this GAME
mod later cannot retroactively enable those extensions. Resource retirement and fatal handling
remain renderer responsibilities.

The one-shot native scene capture/replay system and its Java controller were retired after the
performance investigation. Existing benchmark evidence remains historical; this module no longer
provides that capture, replay or camera tool. Other explicit FG/GPU readback diagnostics remain.

Policy and implementation history: [documentation policy](../../docs/DOCUMENTATION_POLICY.md),
[development ledger](../../docs/DEVELOPMENT_LEDGER.md), [roadmap](../../docs/ROADMAP.md).
The old ignored `dev/radiance-audit` tree is retained historical source/build evidence, not a
second supported build entry point. Do not erase existing evidence during migration.
