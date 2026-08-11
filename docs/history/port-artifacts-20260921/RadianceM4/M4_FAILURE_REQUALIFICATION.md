# Radiance M4 failure requalification

Status: `REJECTED_IMPLEMENTATION`  
Observed by USER: approximately 1 FPS and widespread GUI artifacts  
Target/profile: `1.21.1-neoforge / PRIMARY S1 / 2560x1440`  
Failed process: PID 21744, started 2026-08-24 12:08:46 +08:00

## Preserved evidence

- stdout: `D:\Workspaces\Artifacts\RadianceM4\m4-qualification-run12.stdout.log`
- stderr: `D:\Workspaces\Artifacts\RadianceM4\m4-qualification-run12.stderr.log`
- copied native diagnostic: `D:\Workspaces\Artifacts\RadianceM4\m4-user-fail-native.log`
- copied diagnostic SHA-256: `C1F9C7411660B00070A41FB7CDC275904F7F01BBDED07A1FA063B981EB9CB861`
- live diagnostic SHA-256 before shutdown: `9BECD0E204F5F9FBDFFBA15D01D264E8798BF4BA12574F3E9CDE17DEEFD5B141`
- USER observation: `GUI_FRAME_RATE=FAIL_APPROX_1_FPS`, `GUI_VISUAL_CORRECTNESS=FAIL_WIDESPREAD_ARTIFACTS`.

Only the necessary logs were preserved. No runtime directory, options, configs, saves or user data were copied or cleaned.

## Measured performance evidence

The last complete run segment begins at copied-log line 55,057.

| Sample | Timestamp (UTC) | Derived interval rate |
|---|---|---|
| frame 128 | 04:10:23.0055667 | — |
| frame 256 | 04:12:03.5973960 | 1.27 FPS over 128 frames |
| frame 512 | 04:15:24.3565282 | 1.28 FPS over 256 frames |

The sustained frame rate independently matches the USER's approximately 1 FPS observation.

The same run reached at least:

- 131,072 base-level `NativeImage._upload` calls;
- 524,288 mip metadata `NativeImage._upload` calls;
- 16,384 sampled `BufferUploader._drawWithShader` calls before later unsampled calls;
- 65,536 `_bindTexture` state calls;
- 512 acquired/submitted/presented frames.

## Root causes established from source and runtime evidence

1. **CPU rasterizer masquerading as Vulkan translation.** M4 executes `m4_raster_triangle`, `m4_raster_line` and `m4_fast_axis_aligned_quad` into CPU-backed `OfficialTexture.pixels`. Vulkan only receives the resulting image for transfer/present. No M4 Vulkan graphics pipeline or descriptor model exists.
2. **Full-frame CPU scan/upload.** Each presented M4 frame scans millions of pixels through `m4_non_background_pixels`, converts/uploads a 2560×1440 image and logs the result.
3. **Normal-frame global device stalls.** `upload_official_rgba` calls `vkDeviceWaitIdle`, maps/unmaps staging memory and uploads the full image. This prevents frames-in-flight and overlaps neither CPU work nor transfers.
4. **Per-semantic reflective JNI boundary.** `RadianceM4Bridge` invokes the SERVICE endpoint through `Method.invoke` for intercepted calls; the endpoint then enters JNI. High-frequency draw/state/upload paths therefore pay reflection and JNI costs individually.
5. **Synchronous per-event diagnostics.** Native `diagnostic()` opens an append-mode `std::ofstream` for every event. The failing build emits one `M4_BLIT` event per frame and high-volume sampled symbol records.
6. **Incomplete semantic implementation.** The generic CPU rasterizer does not prove Minecraft shader behavior, precise vertex layout/index semantics, Vulkan clip-space conversion, blend/depth/stencil/cull, sampler/mipmap/sRGB/alpha, atlas/font, framebuffer/blit or resource-lifetime equivalence.
7. **Shader translation is absent in the failed implementation.** Program compilation/linking success and generic draw behavior are synthesized; actual official shader semantics are not translated into cached Vulkan graphics pipelines.
8. **Mipmap handling is knowingly lossy.** Non-base levels are recorded as metadata with base-level sampling rather than uploaded and sampled with qualified mip behavior.
9. **Vulkan validation evidence is absent.** The failed runtime did not install a Vulkan validation layer/debug messenger. Therefore `NO_VALIDATION_ERRORS` cannot be claimed.

These are system-level architecture and semantic faults. They are not a single-screen or widget bug, and call coverage cannot compensate for them.

## Runtime ownership result

PID 21744 passed all ownership checks: repository, Radiance identity, `1.21.1-neoforge` with NeoForge 21.1.248, PRIMARY S1 game directory, M4 milestone, native diagnostic and this thread's launch record. It was then stopped. PIDs 29116, 43672 and 90672 belonging to MoBends remained present and untouched.

## Requalification direction

The failed M4 translator is frozen as evidence and must not be extended. The replacement follows `Artifacts/RadianceMigration/RADIANCE_DECISION_LEDGER.md` and the N0→N3 migration order. A new title-screen runtime is prohibited until actual Vulkan primitive fixtures, performance trace, M3 regression and build gates pass.

