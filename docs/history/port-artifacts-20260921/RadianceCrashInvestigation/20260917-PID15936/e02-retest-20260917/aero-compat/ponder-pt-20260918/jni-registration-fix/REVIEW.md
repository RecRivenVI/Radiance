# Ponder JNI registration correction, 2026-09-18

Evidence: latest.log and crash-2026-09-18_12.54.08-client.txt saved here. Startup and world entry succeeded (12:54:00 player joined); opening Ponder failed at 12:54:07 with UnsatisfiedLinkError for PonderPathTracer.trace. No new hs_err: latest native crash remains 12:49:48. This run passed the previous startup failure point, but did not execute PT native dispatch.

Cause: core.dll is owned by the bootstrap class loader. GAME methods require explicit BootstrapState.bindGameNatives registration via RadianceClient.GAME_NATIVE_OWNERS; the newly declared native method was absent from that list.

Correction:
- New com.radiance.client.proxy.vulkan.PonderProxy contains the native declaration, with no optional Catnip/Ponder dependencies.
- RadianceClient.GAME_NATIVE_OWNERS includes PonderProxy.
- PonderPathTracer calls PonderProxy.trace; removed its private native declaration.
- ponder_path_tracer.cpp exports Java_com_radiance_client_proxy_vulkan_PonderProxy_trace.

Using an independent proxy avoids eagerly loading optional Ponder classes when Ponder is absent. The previous framebuffer attachment correction stays in place. No rendering-quality change or trace toggle.

Validation: complete native and distributed jar build, javap signature and native export check, embedded DLL hash and deployed JAR equality. Runtime PT execution still awaits manual acceptance. No game launched by agent, no staging/commit/push.

Build: SUCCESSFUL in 1m 20s.
javap: PonderProxy.trace descriptor (JIJIIII)V; exported JNI symbol verified in native-export.txt.
Deployed: 2026-09-18T12:57:05.2363969+08:00
JAR SHA256: 0AA764F2343AF2E2958F21A041859ECE2AA55C9F56C923DA9FE9B3AF28C2C6F1
Built/embedded DLL SHA256: 5637890AE196DBC8D69AA80C6F2500A88E1193F044541893D9441F15F1807492
Runtime verification: pending user launch.
