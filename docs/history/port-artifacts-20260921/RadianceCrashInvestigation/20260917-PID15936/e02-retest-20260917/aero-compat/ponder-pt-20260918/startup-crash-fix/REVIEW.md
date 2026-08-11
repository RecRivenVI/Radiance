# Startup crash after Ponder PT deployment

Observed: 2026-09-18 12:49:48, PID 56784; hs_err and latest.log copied beside this report. Startup 16.4 seconds, resource loading; no Ponder PT dispatch. Native access violation in nvoglv64.dll, called from RendererProxy.submitCommandNative. This is not runtime evidence against the PT shader: no scene had executed it yet.

Definite source defect found: UIModuleContext::switchFramebufferDraw supplies color/depth attachments to FramebufferBuilder and calls build(device, pass, width, height). That overload hardcodes attachmentCount=0 and pAttachments=nullptr, contradicting the populated render pass. ui_framebuffer_blit.cpp uses the same overload with a populated builder. Catnip framebuffer initialization newly activates this startup path.

Fix: src/core/vulkan/framebuffer.cpp explicit-extent overload now forwards the collected attachment views. Truly empty builders remain attachmentless. No Ponder bypass, no trace toggle, no changes to PT shader, sampling, Java collector or main-world materials in this correction.

Causality boundary: the invalid framebuffer creation is proven by source; its responsibility for this particular driver access violation is a strong candidate, not confirmed until the corrected build is manually run. No driver root-cause claim.

Existing framebuffer_gpu_test uses raw Vulkan creation and does not exercise FramebufferBuilder; rerunning it would not establish regression coverage for this defect. Complete native/package build and package hashes are checked; in-game startup and Ponder acceptance remain pending. No game launch, commit, staging or push.

Files: framebuffer-before.cpp, crashed-Radiance.jar, hs_err_pid56784.log, latest.log, build.log. The jar backup is the failed 43102BE1... deployment, not the earlier accepted version (which remains in ../before/deployed-Radiance.jar).
Build: prepareRuntime distributedJar SUCCESS, 57s.
Deployment time: 2026-09-18T12:53:06.5780343+08:00
Source/deployed jar SHA256: D833C5989C475499433EB2CC6F236A82071919434825631AF8F01923A535310D
Built/embedded DLL SHA256: E9F32DEE4F722B16BCE4EE7DE8B05E504A54AFE7ECAD4FB5F8358F2017FC5D1D
Manual runtime verification pending.
