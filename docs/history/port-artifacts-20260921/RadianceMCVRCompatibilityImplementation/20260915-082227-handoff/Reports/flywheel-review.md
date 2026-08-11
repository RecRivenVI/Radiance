# Independent Astra Extra High Flywheel source review

2026-09-15 09:48-09:54, read-only; no GPU/runtime. Official Flywheel1.0.6 JAR SHA25631dda15c205eb596d3b3449ef03f6af7363a6cd35b3da4bfe916b304f9e5337e.

Six confirmed issues returned to original Sol owner:
1. any-hit raw material vs closest-hit transformed instance color/UV/fluid/override.
2. coplanar base/crumbling TLAS duplicates cannot form deterministic 2*src*base blend.
3. official light is per-fragment; current per-triangle-vertex sampling loses interior changes, flat wrongly obeys global smoothness, all-solid early AO ignores material flag.
4. parent embedding transform fails to dirty descendants.
5. cross-engine steal retains old non-static Handle outer engine and can collide ids.
6. CHUNK cardinal formula and constant ambient dimension branch differ from official.

Verified invariants at review snapshot:56-byte canonical vertex,8 adapter sizes,256/6592 ABI and bindings10/11; light section contiguous ranges/18³/732-byteoffsets; JNI synchronous copies and model frame retain; engine/id history indexing. These do not establish runtime parity.

Root has dispatched repairs, no completion claim. The root also found shared vertex include/extension/helper compile errors through101 default standalone pack jobs and fixed them; worker now owns vertex.glsl for semantic repairs. Offline compiler script now includes runtime SHARC query/update/resolve definitions and six cubemap FACE variants; do not rerun until worker source freeze.
