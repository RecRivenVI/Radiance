# Ponder in-mod GPU readback — 2026-09-18

RenderDoc changed the target path (NGX extension query failed and RR was skipped), so user requested internal capture instead.

Opt-in diagnostic: create radiance-ponder-capture.request in the game's working directory AFTER a stable Ponder scene is ready. Only Ponder DLSSModule render consumes it. Request renamed into radiance-ponder-captures/<timestamp>/request.accepted; no deletion. Records eight successive RR evaluations for the selected scene buffers, once per process. If scene is abandoned early, capture stays partial; never silently switches scenes.

Copies before RR: HDR input, diffuse/specular albedo, normal/roughness, pixel motion, linear depth, specular hit distance, first hit depth. Copies after RR: HDR output. Every image uses original native bytes (images.tsv gives name, dimensions, VkFormat and byte count). Current/previous CPU WorldUBO camera matrices and jitter, plus per-evaluation NGX result are recorded. Existing separate RR diagnostic retains its bounded reset/handle logging; per-capture reset is not separately recorded.

Uses transfer-src usage already present on DeviceLocalImage. Full-range image barriers transition to transfer source and back to the original tracked layout. Copies into independent host-visible transfer-destination buffers. Transfer-to-host barrier followed by a per-capture GPU event. Capture object, source images, staging buffers and event are retained by the existing frame-fence retainer. Deferred destructor reads only when sealed and GPU event SET; aborted/unsubmitted captures produce INCOMPLETE. No queueWaitIdle or separate queue submit added. File writes currently run on retirement/render thread and can cause diagnostic stutter; not a performance benchmark.

Source: new MCVR src/core/diagnostics/ponder_capture.hpp and changes in src/core/render/modules/world/dlss/dlss_module.cpp. Before/after archived. No commits/staging.

Validation: full prepareRuntime distributedJar build PASS (1m51s). Analyzer py_compile PASS; synthetic half-float fixture generated previews/stats; INCOMPLETE and truncated buffer rejection PASS. These are not real GPU-readback validation. Real eight-frame acceptance pending user scene selection.

Analysis: python analyze.py <capture timestamp directory>. Generates PNG previews and analysis.json with finite-value counts, percentiles, foreground motion/normal lengths and consecutive-frame mean differences. Preview mappings are explanatory; temporal differences include animation, jitter and sample noise, not a denoising quality score.

Deployed source/Prism/repository test JAR SHA256 verified equal: A85B74C367676909D34B83230367CCCD3C66B95236185EB3C4567A1091298299.

Repository test instance: D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319/full; Java PID 50204. Restored saved DLSS Balanced pipeline solely in this isolated test instance. No RenderDoc module loaded. Request not yet armed.

## Actual GPU capture result
Eight frames completed, nine image buffers per frame, all status.txt COMPLETE; analyzer validated byte sizes and generated previews/statistics. All per-frame NGX results are 1 (success). No NaN/Inf reported in captured images. Foreground motion magnitudes are about 0.00023–0.00156 pixels, normals have length 0.99951–1.0.
Visual inspection of frame 1: raw input visibly noisy, RR output substantially cleaner with reconstructed detail. This demonstrates real RR image processing in this repository scene, not merely successful CPU API calls. It does not establish correct downstream tone mapping/compositing or equivalence with the user's earlier Prism scene. Input/output temporal whole-frame differences are not quoted as a quality ratio because resolutions differ and background dominates.
Next boundary: downstream image selection/tone mapping/Ponder alpha composite, plus scene/config differences versus manual acceptance. No product edits in this capture/analysis turn. Capture is exhausted and will not retrigger in this process.

Raw data and previews: D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319\full\radiance-ponder-captures\17897139762846594

## Rearm correction
User requested another capture while Java PID 50204 was still running. The first diagnostic version permanently latched accepted=true and frame>=8, so it cannot capture again in that process. No new capture was made, and previous files are not relabeled as current.
Changed the source to permit a fresh explicit request after a completed burst or destruction of the selected scene. Acceptance still moves the request into a unique output directory; no automatic repeat capture. Each burst remains eight frames. Existing process is deliberately preserved because restarting would lose the current Ponder scene; corrected code requires restart.

Rearm build PASS and both deployed JAR hashes verified: 2C896642D00D5D3807967277CA1800D1740765CB51CD29644C269AB16B1AF092. Running process not restarted; next launch required.


User requested restart. Old test JVM already absent. Started rearm version PID 59084 in D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319/full. JAR 2C896642D00D5D3807967277CA1800D1740765CB51CD29644C269AB16B1AF092; no RenderDoc module; NGX RR initialization present. Logs rearm-20260918-145148-stdout.log and rearm-20260918-145148-stderr.log. Capture request not armed.

## Complex scene capture
User prepared complex Ponder scene; triggered in PID 59084. Capture directory: D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319\full\radiance-ponder-captures\17897143885713154
Eight frames COMPLETE, analyzer passed byte validation, all RR return values 1. No non-finite image values reported. About 214926–214967 foreground pixels at input resolution. Motion maxima vary from 1.33 to 26.59 pixels; cannot attribute to error without separating real gear/scene animation. Output visibly smooths raw input noise, but remains soft with mottled/noisy shadows, particularly rear/right shadows and mechanical detail. This is evidence of incomplete quality, not a successful quality fix. No matched NRD-FSR capture yet. First-frame input/output previews and raw eight-frame buffers retained. Capture stopped after eight frames; next explicit request can rearm without restart.
## NRD-FSR capture entry correction
User switched the complex scene to NRD-FSR. Live pipeline.yaml and CREATE log confirm RT -> NRD -> FSR -> tone mapping -> post render. Existing binary only hooked RR, so NO NRD-FSR capture was made in that running binary.
Added FSR input/output capture using the same bounded request mechanism. In NRD-FSR, input-color is already denoised NRD HDR output, not raw noisy PT. Output-color is post-FSR HDR before tone mapping. Also captures linear depth, raw motion, normal/roughness, first-hit depth and actual converted FSR device depth/motion. backend.txt explicitly labels semantics; FSR does not write a fake RR success result. dispatch.txt notes the wrapper returns void; GPU completion alone does not prove dispatch success, so inspect runtime errors. Existing analyzer supports these formats. No rendering algorithm or quality setting changed.
New build requires restart; current scene preserved. Saved complex-scene-nrd-fsr-pipeline.yaml for configuration comparison.

FSR hook build PASS (1m4s), deployed JAR SHA256 verified on both targets: A7A6272753287F93EDBD8285F72296DB4E774704B39C5F26FD443B1614354A64. Running game preserved; restart required. No NRD-FSR image captured yet.


User requested launch after FSR hook deployment. Old test JVM absent. Started PID 69044 in D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319/full with saved rt_nrd_fsr configuration; JAR A7A6272753287F93EDBD8285F72296DB4E774704B39C5F26FD443B1614354A64 verified. No RenderDoc module. Request not armed. Logs: fsr-capture-20260918-145752-stdout.log / fsr-capture-20260918-145752-stderr.log.

## NRD-FSR complex scene GPU result
Capture: D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance\run\ponder-readback-20260918-144319\full\radiance-ponder-captures\17897147355730481
All eight frames COMPLETE; FSR backend labels confirmed. Input (NRD output) 1706x960; output 2560x1440. Previous RR complex capture input was 1485x835 (FSR input has approximately 32.1% more pixels). Viewed output has much smoother shadows than RR, but shadow contrast/shape also differs. Not an equal-input-resolution quantitative denoiser comparison.
Analyzer initially failed serializing Inf in temporal differences, due fsr-device-depth abnormal values. Fixed analysis only: pairwise finite mask and float64 differences; original raw values and nonfinite counts remain retained.
Actual fsr-device-depth: frames 1/3/5/7 all zero; frames 2/4/6/8 each have 36274 nonfinite values, extreme finite values about -3.3965e38. This warrants independent validation of the depth resource/descriptor/conversion/capture path; no root cause inferred. Other first-frame captured images finite. No rendering code altered in this capture turn.
