# F3+1 profiler pie scope fix

Evidence: Prism crash-2026-09-18_17.16.33-client.txt (copied as crash.txt). Java UnsupportedOperationException from RenderCaptureContract / RasterDrawBridge, `VertexBuffer raster draw in OUTSIDE[outside]`, called by Minecraft.renderFpsMeter at line 1538. No new hs_err for this event. Direct failure is missing GUI scope, not FPS/C getter or a native driver crash.

Minecraft.runTick draws the profiler pie after GameRenderer.render returns and its GUI scope closes. The pie's triangle fan/strip draws were consequently rejected. It also defers text into GuiGraphics and flushes after renderFpsMeter.

Single source fix: MinecraftClientMixins wraps renderFpsMeter in a dedicated GUI scope, calls the original method, then flushes its deferred text before closing. The original caller's later flush is empty. Try-with-resources restores scope on exception. Global rejection of unscoped/world draws remains intact. No replacement chart, FPS/C changes, FG changes, or native changes.

Validation: distributedJar build succeeded in 17s; existing RenderCaptureContractTest 5/5 passed (scope authorization/nesting/exception cleanup). Packaged class contains new wrapper. These checks do not prove transformed F3+1 runtime/visual success; user must retry. No game launched/restarted or configs changed.

Deployed both authorized instances and verified hashes (deployment.json). Previous JARs backed up. New JAR SHA256 64862BDD6EC66797170D9F08D03ABCB85FD150E9A9E571F15E43C5688C9F239A; unchanged DLL SHA256 352B47B6DF2981F607FDD9D0AE311E62BE5DF63FA5E14D2CDF994065D802EEB5. Changes uncommitted and unstaged.
