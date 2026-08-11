# Current root readback and sampling integration (not yet compiled)

- FrameworkContext tracks worldRenderRequired/worldRendered/frameSubmitted. fuseWorld marks complete-world dependency; flushForReadback submits and waits upload/world/overlay command segments, leaves swapchain acquire/present semaphore for final submit, resets recording for continuation. It must not run incomplete-world PT before fuse request and must not double-render world after a flush.
- NativeImage texture download recognizes MainColor alias, flushes current FBO work before GPU readback and returns bottom-left rows for attachment textures.
- New FramebufferProxy.readPixels(x,y,w,h,format,type,destination) is tightly packed GL row order; implementation in progress. This is needed to replace diagram alpha occupancy approximation and NativeImage depth readback.
- TextureProxy.isFramebufferTexture is implemented; shader worker owns per-sampler FBO Y orientation correction (ordinary upload textures unchanged), including Veil bridge and Minecraft translated shader.
- Native/Java latest set is NOT yet compiled. Last passing core remains public-core-normal-viewport.log before these changes. Do not run a client.

Implementation now written: Framework.readPixels copies real READ FBO color/depth/stencil data after flush, converts supported byte/half/float formats into requested tightly packed GL rows; DiagramScreen original aabbInFramebuffer now retains its actual GPU alpha test via redirected glReadPixels, replacing projected-geometry approximation. NativeImage.downloadDepthBuffer redirected to real native read. Latest additions pending compile and tests.

Checks: readback-integrated-build.log core+shaders PASS; first combined command then failed in worker instancing test missing include (fixed subsequently). readback-contract-run.log PASS row/alpha/D16/half conversion; instancing-light-contract-run.log PASS 224/6592 source ABI. Independent Astra xhigh readback_review running; initial findings confirmed stale mid-frame descriptors and old UBO versions missing uniform upload, awaiting final bounded report before fixing.
