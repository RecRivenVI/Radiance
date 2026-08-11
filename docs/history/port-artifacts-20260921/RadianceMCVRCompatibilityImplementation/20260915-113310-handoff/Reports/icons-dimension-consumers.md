# Fixed NeoForge mod-list logos / dimension renderSky consumers

## Conclusions

- NeoForge **21.1.250** mod-list logos have a real anonymous `DynamicTexture.upload()` override. It bypasses the ordinary DynamicTexture tracker injection but still calls the common `NativeImage._upload` path. Current Radiance already recovers its actual target through the mirrored bound texture and performs the real Vulkan upload. **No disconnected fixed logo-upload consumer was found.** Visual PNG/filter correctness remains runtime evidence, not a new source gap.
- The fixed Create 6.0.10 / Sable 2.0.5 / Aeronautics-Simulated-Offroad 1.3.2 / Flywheel 1.0.6 / Veil 4.3.2 combination has **no discovered registered custom IDimensionSpecialEffectsExtension.renderSky implementation**. NeoForge's default returns false. Radiance's SKYBOX bridge exists, but no actual installed custom sky draw was established. Do not expand this into a requirement for arbitrary uninstalled dimension mods.

This was a bounded read-only source/package inspection. Only this report was written; no code, tests, build, UI or game execution was performed.

## Actual logo chain

Fixed reference: `D:/Workspaces/References/minecraft-references/neoforge-21.1.250/src/net/neoforged/neoforge/client/gui/ModListScreen.java:383-402`.

Selecting a mod with a logo opens its real pack root resource, calls `NativeImage.read(InputStream)`, constructs an anonymous `DynamicTexture(logo)`, and registers it with `TextureManager.register("modlogo", ...)`. Its overridden upload performs:

```java
this.bind();
NativeImage td = this.getPixels();
td.upload(0, 0, 0, 0, 0, td.getWidth(), td.getHeight(),
    selectedMod.getLogoBlur(), false, false, false);
```

The original 1.21.1 DynamicTexture constructor calls `TextureUtil.prepareImage(getId(), width, height)` and virtual `upload()` (or queues that same preparation/upload on the render thread). Thus the override really runs; assuming the vanilla `DynamicTexture.upload()` method injection handles it would be incorrect.

Current source coverage:

1. `vulkan_render_integration/AbstractTextureMixins.java` supplies the actual ID through Vulkan TextureUtil; `TextureUtilMixins.java:16,27` replaces ID allocation and the common prepare-image overload with `TextureProxy.generateTextureId/prepareImage`.
2. `AbstractTexture.bind -> RenderSystem.bindTexture -> GlStateManager._bindTexture` enters `GlStateManagerMixins.java:28`, which mirrors the actual per-unit binding in TextureProxy. The terminal GL call at lines130-136 is redirected under NO_API.
3. `vanilla_resource_tracker/NativeImageMixins.java:21` initializes an untracked image target ID to -1. `NativeImageBackedTextureMixins` ordinarily assigns the target inside vanilla `DynamicTexture.upload`; the anonymous override bypasses that particular method, leaving this image untracked.
4. The actual common Vulkan `NativeImageMixins.java:43-100` intercepts `_upload(IIIIIIIZZZZ)V` after allocation validation. For target <0 it calls **`TextureProxy.boundTexture()`**, rejects absence, records the recovered target and performs auxiliary processing, source `logoBlur` filtering, wrap mode, and `TextureProxy.queueUpload` with the real PNG pixels/region/level. That recovery is already present in current source and specifically documents legal logo subclasses. No success-without-upload fallback was added here.
5. JNI `MCVR/src/core/middleware/com_radiance_client_proxy_vulkan_TextureProxy.cpp:65-83` forwards that data to `Textures::queueUpload`. `MCVR/src/core/render/textures.cpp:450` copies source bytes into upload cache (`append`); its cache implementation at947 performs the actual memcpy. The CPU source is not merely retained by raw pointer until a later render.
6. TextureManager registration tracker maps the actual ResourceLocation to the created ID. `ModListScreen.java:184-189` later calls `GuiGraphics.blitInscribed` on that resource. PNG decode, dimension and blur parameters remain the original consumer inputs.

ModListWidget's version-status icons are ordinary `neoforge:textures/gui/version_check_icons.png` resource blits, rather than another direct image-upload implementation. The fixed mod-list screen's only found NativeImage/DynamicTexture logo path is the one above. Missing visual acceptance remains WP-V02 runtime work; the source does not justify another logo bridge.

## Installed dimension consumer check

Fixed NeoForge `client/extensions/IDimensionSpecialEffectsExtension.java:37-40` defines the default `renderSky` returning false. The corresponding fixed vanilla effects use that default unless a mod registers/overrides it.

Searched the installed-version reference source trees for `IDimensionSpecialEffectsExtension`, `RegisterDimensionSpecialEffectsEvent`, and custom `renderSky` declarations: Create6.0.10, Sable2.0.5, Aeronautics bundled1.3.2 (including Simulated/Offroad), Flywheel1.0.6-neoforge, Ponder1.0.82 and Veil4.3.2. No custom effect registration/override was found. Veil's sole renderSky source match is a Fabric vanilla LevelRenderer stage-injection target, not a NeoForge custom effect implementation.

Also scanned every class entry in the fixed-version compatibility nested JAR directory for those interface/event/method symbols: no match. Scanned actual fixed main Gradle dependency JARs `create-6.0.10+mc1.21.1.jar`, `sable-2.0.5+mc1.21.1.jar`, `create-aeronautics-1.3.2+mc1.21.1.jar`: each had **zero matching class entries**. This corroborates the absence of a fixed producer in those installed mods rather than inferring from Radiance alone.

Current Radiance `WorldRendererMixins.java:230-244` invokes the real level effects callback. `DimensionSpecialEffectsCompatibility.java:27-45` scopes a custom renderSky draw into the existing SKYBOX target/coordinates and commits accepted meshes. Vanilla sky is suppressed only when the custom callback returns true **and** accepted actual world meshes >0. With the fixed default callback there is no custom SKYBOX draw to validate or repair. Other dimensions/uninstalled mods are outside this finding; live default sky appearance remains its separate runtime boundary.
