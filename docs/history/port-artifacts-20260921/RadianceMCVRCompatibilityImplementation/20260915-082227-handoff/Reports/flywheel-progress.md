# Flywheel current source audit and implementation

Luna Max bounded read-only audit: real model/instance/BLAS/TLAS code exists, but backend registration mixin and GAME JNI owner absent. Native engine was synthetic handle 1 and delete cleared global instancing. Light sections only recorded/cleared; embeddingNormal ignored; material flags and distinct blend modes incomplete.

Root changes now written (not yet verified):
- Registered backend mixin and NativeInstancingProxy GAME JNI owner.
- Native root Instancing now allocates unique engine handles, owns independent engine stores; JNI routes all model/instance operations via supplied engine; world preparation aggregates children and deleting one engine clears only its store.
- Remaining: synchronization/engine lifecycle tests, light upload, normals/material ABI, correct fixed dependency consumers and reload/close/config/off acceptance. Existing engine classes must not be labeled complete.

Ownership: root currently owns instancing.cpp/hpp, NativeInstancingProxy middleware, RadianceClient and mixins registration. Worker owns persistent raster only; second worker owns Veil FBO only.
