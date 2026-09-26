# Artifact-scoped OpenGL inventory

`GlInventory` reads bytecode without loading inspected classes. It enumerates direct LWJGL OpenGL
calls, method references, nested JARs, symbolic possible callers and Mixin annotation evidence.
It also reports duplicate class definitions, multi-release variants, native bodies, reflection and
unresolved dynamic calls. The input list is the coverage boundary, not "every mod in existence".

```json
{
  "artifacts": [
    {"path": "absolute-or-manifest-relative/minecraft.jar", "role": "minecraft"},
    {"path": "absolute-or-manifest-relative/mod.jar", "role": "third-party"}
  ],
  "decisions": []
}
```

Run with Java 21 from the repository root; relative CLI paths resolve there:

```powershell
.\gradlew.bat :radiance-audit:renderInventory `
  -Pinventory.manifest=run/my-inventory/input.json -Pinventory.output=run/my-inventory/report
```

The output directory must be empty/new. Outputs include `calls.csv`, full `inventory.json`, the
input manifest, symbolic reverse-call list and a scope/limitations report. Each call includes the
JAR SHA-256, nested origin, class, overloaded method descriptor, executable instruction ordinal,
source line where available and target API. An ordinal is not a JVM byte offset. Binding-internal
calls are retained by role; do not count them as thousands of missing game draws.

All rows initially have `translation=UNKNOWN` and `semanticChange=UNKNOWN`. Optional `decisions`
are exact-site records `{id, translation, semanticChange, evidence}`. Translation values are
`UNKNOWN`, `TRANSLATED`, `REPLACED`, `INTENTIONALLY_SKIPPED`, `UNSUPPORTED`, `VANILLA_RETAINED`;
semantic values are `UNKNOWN`, `PRESERVED`, `INTENTIONAL_CHANGE`, `DEFECT`. Evidence is mandatory.
Duplicate/stale/out-of-scope IDs fail instead of carrying an old verdict into a changed artifact.

A Mixin target does not prove consumption or equivalent pixels. Runtime reachability, final
transformed classes, virtual dispatch, reflection/generated code and JNI/native OpenGL need
separate evidence. Cancelled producers need intent-level observation before cancellation; a probe
only at `glDraw*` cannot discover code that never reaches it. Existing Audit coverage ledgers are
the appropriate runtime complement. Do not run expensive call tracing while collecting formal
performance samples. No row is mechanically upgraded to equivalent just because names match.
