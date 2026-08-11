# Outliner lit geometry correction — 2026-09-18

Request: remove Outliner self illumination, retain checker texture/tint/transparency, inspect shape and width against upstream.

Source references: local ponder-1.0.87+mc1.21.1 Outline.java, AABBOutline.java, BlockClusterOutline.java, PonderRenderTypes.java; native entities.cpp cubeCornersFromFaceCenters.

Findings:
- Previous lineWidth max(1/64, width) truncated fade and enlarged narrow lines.
- Reconstructing a square prism from transformed endpoints loses the original cross-section rotation and scale. Native endpoint extension already used half width; that part was not a missing-length bug.
- Original AABB already halves non-highlight face alpha. Interception halved it again.
- Previous face interception discarded the pose normal matrix, reselected texture and culling independently of original rendering.

Implementation:
- Capture original Catnip QUADS into StorageVertexConsumerProvider with default emission 0; submit both strokes and faces through WorldMeshSink. No line reconstruction, width clamp, alpha remultiplication, or normal/texture reselection.
- Preserve original width (default 1/32), per-line width, cubic fade, cuboid half-width endpoint extension, pose-transformed vertices/normals and face UV/color/alpha. Original caller-selected RenderTypes preserved.
- WorldMeshSink exempts radiance/outliner/ submissions from unlit lightning material routing and shader-derived emission. Stroke mode remains opaque; face mode uses lit coverage (10), not refractive transmission or unlit mode 23. WORLD participation retained.
- Removed OutlinePrimitiveMixins registration; obsolete source moved to Windows Recycle Bin. Backup is in before/.
- Unsupported ItemOutline retains previous fallback. Existing generic entity_translucent and ghost routes were not altered. Ponder UI path outside this world capture unchanged.

Validation:
- Java 21 Gradle compileJava, test --tests com.radiance.client.render.WorldMeshSinkContractTest, distributedJar: BUILD SUCCESSFUL (22s).
- Existing 3 WorldMeshSink contract tests passed; these are NOT game visual tests or new geometry parity tests.
- git diff --check passed (line-ending warning only).
- Geometry parity established by removing reconstruction and directly consuming original emitters; raster/PT culling, sampling and visual appearance still need in-game acceptance.
- Native code/DLL unchanged. New JAR is build/libs/Radiance-0.1.5-alpha-neoforge-1.21.1.jar; SHA256 in artifact-hash.txt.
- Not deployed to Prism and no game launched. All source changes remain uncommitted.

Follow-up: user authorized deployment after every completed change. This JAR is now deployed and SHA256 matched; see deployment.txt. Previous deployed JAR backed up as before/deployed-Radiance.jar. No game launched.
