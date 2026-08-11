# Flywheel / Sable Vulkan embedding lighting

## Result and evidence boundary

Implemented the Java consumers of the original Sable embedding lighting contract and restored Flywheel's original visualizer/BER selection policy. `compileJava`, `compileTestJava`, `generateJniHeaders`, and all six `*Flywheel*Test` suites passed: **16 tests, zero failures/errors/skips**. The generated header contains both approved JNI methods.

This is Java compilation, unit/bytecode verification, and source-contract evidence. It does not establish live Mixin application, native scene sampling, in-game appearance, or absence of duplicate BER pixels. Native implementation and GPU verification belong to the parent task; no game/UI was launched here.

## Original consumers and corrected behavior

Pinned Sable is `2.0.5+mc1.21.1`. Its `BlockEntityStorageMixin` creates Flywheel embeddings, updates the real physical pose/normal, and calls `EmbeddedEnvironmentExtension.sable$setLightingInfo` with the plot-relative scene matrix, container lighting-scene ID, and `latestSkyScale / 15`. Its contraption visual supplies the corresponding transformed scene matrix. The original Sable extension only targets Flywheel's GL `EmbeddedEnvironment`, so Radiance's custom embedding previously missed these calls.

`SableFlywheelEmbeddingMixins` implements that existing extension on Radiance's embedding and forwards copied source values. Explicit scene 0 uses the composed physical embedding pose; nonzero scenes use Sable's supplied matrix. Scene -1 remains missing and is not changed to scene 0 for instance sampling. Unconfigured ordinary instances leave native lighting on the physical world path. Unconfigured nested embeddings inherit their parent's actual scene matrix multiplied by the child's pose, scene ID, and sky scale. Explicit child lighting overrides that inheritance.

Lighting or pose changes dirty every dependent descendant instance. Handle upload first calls the existing `updateInstance`, then sends the current explicit lighting snapshot through `updateInstanceLighting`. The native reset-before-explicit-update contract prevents stale lighting after instance migration to another context. Deleting an ancestor causes descendant handles to be removed at flush.

The existing light-section ownership list remains absolute. The original `LightDataCollector` continues collecting the same bytes at each absolute section. A Sable helper attaches scene ID and plot-relative section coordinates to those bytes; ordinary sections use the existing upload API. Relationship reconciliation runs each frame, so an unchanged absolute requested set with changed scene/plot ownership still becomes dirty and is reuploaded. Missing registered plot scene IDs use scene 0 for section partitioning, matching original `SableFlywheelLightStorage`; an explicit instance scene -1 cannot match that section data. Removed absolute sections are removed through the existing `setLightSections` ownership path.

Removed Radiance's forced `supportsVisualization=false` during transformed-BE capture. Flywheel now retains its real backend/level/visualizer availability decision and original `VisualizationHelper.skipVanillaRender` policy. That includes visualizers which deliberately retain a partial vanilla BER; this change does not force every BER off.

## Native contract delegated to parent

Approved/generated declarations:

```java
updateInstanceLighting(long engine, long instance, int scene, float skyLightScale, long sceneMatrix)
uploadLightSectionScene(long engine, long absoluteSection, int scene, long relativeSection, long data, int size)
```

`sceneMatrix` maps embedding-local coordinates into the lighting scene; native multiplies it by the instance-local transform. Scene 0 receives Java's composed parent-relative pose and requires native engine-origin addition. Nonzero scenes retain Sable's original matrix. Native must reset explicit state during ordinary `updateInstance`, preserve explicit lighting through world preparation, preserve the existing 368-byte appearance ABI, and maintain absolute section ownership while indexing GPU light data by scene/relative section. Parent owns these native changes and their verification.

## Source ownership and registration

Modified:

- `src/main/java/com/radiance/compatibility/flywheel/RadianceFlywheelEngine.java`
- `src/main/java/com/radiance/client/proxy/world/NativeInstancingProxy.java`
- `src/main/java/com/radiance/mixins/compatibility/flywheel/FlywheelVisualizationManagerMixins.java`
- `src/test/java/com/radiance/compatibility/flywheel/FlywheelEngineOwnershipBytecodeTest.java`

Added:

- `src/main/java/com/radiance/compatibility/flywheel/FlywheelEmbeddingLightingAccess.java`
- `src/main/java/com/radiance/compatibility/flywheel/FlywheelEmbeddingLighting.java`
- `src/main/java/com/radiance/compatibility/flywheel/FlywheelLightSectionRelationships.java`
- `src/main/java/com/radiance/compatibility/flywheel/SableFlywheelLightSections.java`
- `src/main/java/com/radiance/mixins/compatibility/sable/SableFlywheelEmbeddingMixins.java`
- `src/test/java/com/radiance/compatibility/flywheel/FlywheelEmbeddingLightingTest.java`
- `src/test/java/com/radiance/compatibility/flywheel/FlywheelLightSectionRelationshipsTest.java`

Generated: `src/main/native/include/com_radiance_client_proxy_world_NativeInstancingProxy.h`.

Parent registered `compatibility.sable.SableFlywheelEmbeddingMixins` and its Sable/Flywheel class-presence gate in the shared JSON/MixinPlugin. No shared JSON, native implementation, WorldRenderer, EntityProxy, or Git mutation was performed by this subtask.

## Validation

Command: `.\gradlew.bat compileJava compileTestJava generateJniHeaders test --tests '*Flywheel*Test' --no-daemon`.

Result: `BUILD SUCCESSFUL in 18s`, 7 actionable tasks, 5 executed / 2 up-to-date; session 43081 exited 0. Existing Java deprecation and Gradle-9 compatibility notices did not fail the build.

Suites: backend preference 2; embedding lighting 3; engine ownership bytecode 5; instance adapter 2; light-section relationship 2; mesh data 2.

New tests verify actual matrix/sky-scale copying, scene -1 retention, scene-0 composed-pose selection, nested scene inheritance/override, same-absolute-section scene reassignment and removal, descendant invalidation, current-scene upload ordering, and removal of the forced visualization fallback. The bytecode tests check compiled production call structure; they do not execute native rendering.

Evidence: `../Evidence/flywheel-sable-embedding/` contains suite XML, source/header SHA-256, generated header, the fixed Sable source contracts, and Java verification result.

## Remaining boundary

- Runtime Mixin application on the custom nested embedding and original Sable consumers is pending game/runtime verification.
- Native scene-aware light upload/sampling and engine-origin correctness require the parent's native/GPU evidence.
- Real moving sublevel/contraption visuals and BER duplication must be assessed in a live world; unit tests do not prove pixels.
- This implementation retains original light collector bytes and errors rather than generating synthetic lighting. Unexpected Sable sublevel without its level container throws a specific error.
