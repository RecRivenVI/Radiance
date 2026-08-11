# PID 49284 review

- Observed fatal: 2026-09-17 03:00:06, JVM elapsed 54.316056 seconds.
- Exact original signature: nvoglv64.dll+0xf1c729, read 0xffffffffffffffff, R9=-1.
- Java path: TitleScreen -> AbstractButton / scrolling text -> GuiGraphics.drawString -> BufferUploader -> ShaderProxy.draw.
- Native stack in this hs_err contains only the driver frame. The precise native call site is inferred from the current draw path and prior evidence, not resolved from a new dump.
- No minidump generated according to hs_err; no draw-state trace found in the instance.
- Actual core path: .radiance/runtime/c1b51149ce4fe6eda5386899a845824f22427787bc0587797a89e6e8160d2c3b/core.dll.
- Installed JAR SHA256: EB87746428ED649047FBFE61F4B944C7EBF3D1AB1A3FCD3057A800BCB6BD31C1.
- Loaded-path DLL and current build DLL SHA256: 7BE26FA2D828B393BDD9002DF950BE012A59D1C7BB3FF12FA9F813D5A86E4329.
- descriptor.cpp/.hpp hashes match the archived resource-lifetime-keepalive-v2 source manifest. This is a fresh Release rebuild, not the old archived v2 binary.
- Therefore the retained-resource implementation does not suffice to eliminate the original AV in this run. It does not establish that all lifetime paths are safe or that this fix is unnecessary.
- latest/debug logs have no matched VUID or SYNC-HAZARD; no affirmative validation-enabled evidence, so this is not a clean validation result.
- RTSS Vulkan layer and OBS hook are present in the DLL list; presence alone is not causal evidence.
- Optional absent-mod mixin warnings precede successful continuation into the title-screen render path; not the demonstrated fatal mechanism.

## Next evidence boundary
The faulting draw's pipeline, bound descriptor contents/indices, dynamic uniform offsets and recording state are unavailable. Inspect these together with lifecycle; do not declare lifetime-only or driver-only root cause. Use bounded rolling capture preserving final events and before/after draw markers rather than a startup-only event cap. No source modifications or client launch performed in this review.
