# F3 presentation FPS — 2026-09-18

User confirmed via RTSS that FG is effective and Minecraft reports base rendered FPS. This is user runtime acceptance of generation/display benefit, not an agent-measured scanout trace. User requested FG-inclusive FPS in F3.

Implementation:
- Preserve vanilla FPS line; add localized Radiance output line near the top: `Radiance: 156 fps 输出（渲染 78 + FG 78）`.
- Native presentation counter records successful/suboptimal Vulkan presents. In an FG pair, first accepted output is generated and second is retained real. With no generated output, first accepted output is real. Counters do not multiply FPS based on the option toggle.
- One-second rate window; one atomic snapshot packs rendered/generated integer rates and crosses JNI without allocations. Counter is independent of temporary timing logs; Java avoids JNI before renderer initialization/after close. Reset on renderer initialization.
- Display is application presentation rate, not hardware scanout telemetry. Settings changes may briefly mix samples within a one-second window. Inactive/unsupported/reset FG produces no artificial generated count. Original FPS/frame-time diagnostics are unchanged.
- Only text/statistics added. No changes to FG calculation, GUI separation, presentation ordering, synchronization, user configuration or instance process state.

Files: native presentation_rates.hpp, render_framework.cpp, RendererProxy JNI cpp; Java RendererProxy.java, DebugScreenOverlayMixins.java, mixin registration, en_us/zh_cn translations. Prior versions of edited files saved as *.before in this directory.

Validation: full prepareRuntime/distributedJar build passed in 2m04s. Generated JNI header contains new method. Packaged inner game JAR contains registered mixin and both translated strings. Embedded DLL hash matches native build and contains JNI export name. No live F3/mixin runtime acceptance performed; user starts and checks visuals.

Deployed both authorized Prism and repository test instance, hashes match; no processes started/stopped and no settings changed. JAR SHA256 EC73804C0189F360088203F8E1B36C35062FB52D32BEB9177B50CFF4E5EA56A0; DLL SHA256 4D4A826CB83D72C240187A714D57AAC06A80D2075093BAD58924F0B11FD73842. See deployment.json. Previous JARs preserved as before-f3-0.jar / before-f3-1.jar. All edits remain unstaged/uncommitted.
