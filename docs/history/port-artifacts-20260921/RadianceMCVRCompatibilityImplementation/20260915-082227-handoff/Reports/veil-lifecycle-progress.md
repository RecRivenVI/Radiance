# Veil lifecycle root integration (in progress)

Root now owns prior FBO module; nine FBO registrations retained. Restored real VeilRenderer construction and DynamicBufferManager texture allocations; removed four blanket-cancelling VeilForgeClient/events/stage/NeoForgePlatform mixins through Recycle Bin. Added Vulkan capabilityLimit and typed Veil limits; screenQuad uses worker VeilShaderBridge real draw. No game has run and this latest Java/native set is not yet built.

AFTER_LEVEL renderPost now queued until GameRenderer fuseWorld; pure AfterWorldRender helper tests written. Earlier-stage main-output consumers/bloom require remaining ordering review, not marked complete.

Removed unsupported hardcoded FBO color-attachment limit 1 and its trivial assertion test (Recycle Bin). Real device-backed limit now used; existing native MRT GPU/contract tests retain actual multi-attachment evidence. Historical worker 5-test pass remains historical only. Depth-only AdvancedFbo now selects read-buffer NONE. MainColor/MainDepth aliases can resolve actual per-frame FBO attachments (sampler aliases still use separate mirrors); latest attachment addition pending build.

Native normal/viewport prior checkpoint: public-core-normal-viewport.log PASS for core + shaders; instancing-contract-run.log PASS. Flywheel worker now owns instancing/common appearance/vertex instance shader/light descriptor after this checkpoint.

Pending: worker shader bridge completion, Java/JNI compile and native incremental, real renderer reload/default/custom targets, Veil dynamic buffer outputs, direct-GL consumers, MainTarget/readback, stencil precision, final A/B package and user visual acceptance.
