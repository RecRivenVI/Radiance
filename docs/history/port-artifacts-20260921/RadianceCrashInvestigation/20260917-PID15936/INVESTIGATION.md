# PID15936 crash investigation

Baseline: 2026-09-17 runPackagedClient; Java 21.0.11; crash after 45.28s in nvoglv64.dll+0xf1c729, read -1, ShaderProxy.draw via GUI text. No MCVR rebuild. JAR/DLL and logs preserved with SHA256. Tracked worktree diffs preserved; these patches do not include untracked source files.

Authorized scope: diagnostic instrumentation and runs, with records. No commits or pushes.

Experiment T1: same binaries, existing opt-in Java and native draw-state tracing. MCVR_DRAW_STATE_TRACE_MAX_EVENTS=100000; FLUSH_EVERY=1; RADIANCE_DRAW_STATE_TRACE=1; Java max events=512. Use Gradle --no-daemon to isolate this launch. No renderer behavior or user options changed. Trace timing can perturb the failure. Bounded event logging; reaching the cap must not be interpreted as complete crash coverage.

Next diagnostic options: Vulkan core/synchronization validation; call-entry/return breadcrumbs around pipeline binding and indexed draw; resource create/destroy and frame-fence correlation; native crash dump with matching symbols; one-variable controlled experiments after evidence collection. A driver stack frame is not proof of driver fault.

T1 result: same driver AV reproduced. Binary hashes identical to baseline. Trace reached exactly 100000 events before crash; last event is NOT the faulting draw. Native and Java tracing confirmed active. Need rolling tail capture. T2 instrumentation: opt-in MCVR_DRAW_STATE_TRACE_ROLLING=1 rotates between path and path.1 at maxEvents, preserving one completed segment plus current. DRAW_RETURN marks return of vkCmdDrawIndexed wrapper; uniform preview expanded to 256 bytes for complete 192-byte text UBO. Pre-edit files backed up.

T2 build: cmake --build ../MCVR/build --config Release --target core --parallel passed (exit 0). Reused existing configured Visual Studio 18 2026 build. Only draw_state_trace.cpp and ui_module.cpp recompiled. git diff --check passed. Copied resulting core.dll/core.lib to Radiance src/main/resources; prior versions preserved in baseline. This is a diagnostic binary, not a bug fix.
T2 launch: same runPackagedClient with --no-daemon; ROLLING=1, MAX_EVENTS=20000 per segment, FLUSH_EVERY=1, Java tracing=1, max Java events=512. Paths: rolling-trace/draw-state.tsv and .tsv.1. Order segments by segment header, not filename. Return marker identifies previous DRAW on the same native thread/command buffer; it does not imply GPU completion. Baseline capped behavior remains when ROLLING is absent. Preview includes up to 256 bytes only while tracing is enabled.
Rollback: restore the two edited C++ files from pre-instrumentation and core.dll/core.lib from baseline, rebuild/repackage if needed. Do not reset either repository: prior unrelated modifications exist. All diagnostic environment variables are scoped to the launched shell.

T2 live result at 04:29:55: PID 28488 reached singleplayer; log shows integrated server start and Dev joined the game. Window responds. No crash at this checkpoint. Leave running for user reproduction. This is not a fix or stability acceptance: instrumentation and flushing perturb timing. Rolling segments passed segment 29, demonstrating capture beyond original cap. Build/repackage passed. Final crash status pending.

Next order: reproduce with this diagnostic build; inspect unmatched DRAW / DRAW_RETURN and complete UBO at failure; correlate live descriptor entries and dynamic offsets. If failure is suppressed, run same diagnostic binary with tracing disabled, then reduced flush frequency, preserving one-variable comparisons. Add resource lifecycle IDs and Vulkan core/sync validation as needed. Do not infer the faulting call from a capped trace or infer a driver bug from nvoglv64.dll alone.

T2 completed: previous process 28488 exited; Gradle BUILD SUCCESSFUL in 1m50s. User requested identical diagnostic version restart. Current JAR and DLL SHA256 match deployed-hashes.csv. Rerun directory: D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260917-PID15936\rolling-rerun-20260917-043115. Same environment and existing instance; no native rebuild.

T3 trace-off control: D:\Workspaces\Artifacts\RadianceCrashInvestigation\20260917-PID15936\trace-off-20260917-043359. Same diagnostic DLL/JAR, hashes verified before launch. All six trace environment variables removed only from launch shell. Same packaged-client instance and no-daemon launch. Exclude distributedJar and preparePackagedClient to prevent binary rebuilding/replacement; verifyDistributedJar remains enabled. Previous PID 13452 no longer exists. No cache clearing or game configuration changes.

T3 result: PID62636 crashed in native code with tracing disabled. hashes-before.csv and hashes-after.csv identical for both DLL and JAR. hs_err and completed game logs preserved in control directory. Same binary can crash without logging; recompile alone is not sufficient to prevent failure. Trace runtime behavior is implicated as a suppressing factor, not proof of a specific race or resource-lifetime defect.

Static review recorded in SUSPECTS.md. No renderer edits or new launch in this review. Cache-state drift identified as a remaining control variable.

Experiment bind-noop completed: native build/package checks passed, runtime original AV reproduced (PID51628, 25.46s). Details and rollback in experiment-bind-noop/EXPERIMENT.md. Six-line experimental change remains unstaged/uncommitted.

Trace split completed. Native-only crashed in in-world hotbar item rendering after40.66s, read -1. Java-only crashed on title screen after22.40s, read0x2000000000. Same DLL/JAR and confirmed same cache input; cache timestamp restored. See trace-split/EXPERIMENT.md. No source changes.

2026-09-17 lifecycle follow-up: see lifecycle/EXPERIMENT.md. Added sequence-linked nonrolling bounded descriptor lifecycle journal, preserving bind-noop. Native-only PID59768 reproduces title text driver AV after22.30s; final descriptor table complete and not destroyed, sampled slots61/19 present, dynamic UBO bounds fit. Same binary custom-trace-off with standard Khronos validation PID58520 enters world, observed95s, normal close BUILD SUCCESSFUL, no VUID logged. Not a fix. RTSS layer present in both, recorded as a future process-scoped comparison candidate.

Early-window-off single run: earlywindow-off/EXPERIMENT.md. Only earlyWindowControl changed to false, same DLL/JAR, no custom tracing or validation. PID48812 crash after21.17s, TitleScreen text ShaderProxy.draw -> nvoglv64.dll+0xf1c729 reading-1. Bootstrap confirms disabled. Config retained false.

Sampler-direct experiment: sampler-direct/EXPERIMENT.md. Bypassed built-in sampler wrapping, kept external path and high-bit ID decoding. Verified new generated shader. PID61624 same TitleScreen ShaderProxy.draw driver+0xf1c729 read-1 at32.437141s. Trial source and instance JAR restored exactly. early window true. Wrapper removal insufficient; next candidate descriptor layout/scope expansion remains.

Descriptor layout isolation: descriptor-stages/EXPERIMENT.md (stage visibility only) PID61916 same driver/title path read-1 after34.883538s. descriptor-bindings/EXPERIMENT.md (bindings2..5 removed, ALL_GRAPHICS retained, dependent post pipelines guarded/skipped) PID16964 same driver/title path read0x2000000000 after27.780608s, no guard triggered. Neither separate rollback prevents crash. Both source and deployed baseline DLL/LIB/JAR restored exactly. Do not use experimental build outputs without rebuilding. Next candidate is dynamic UI pipeline creation wrapper/cache/feedback.

Dynamic UI cache/feedback bypass: dynamic-pipeline-direct/EXPERIMENT.md. Invalid initial incremental build PID28852 excluded (stale prior ui_module.obj, diagnosed and corrected). Valid rebuilt run PID54760 still TitleScreen ShaderProxy.draw -> nvoglv64+0xf1c729 read-1 after32.526263s. Direct dynamic vkCreateGraphicsPipelines with null cache/no feedback insufficient. Source/deployed binaries restored exactly; baseline native objects rebuilt after touching restored source to prevent stale timestamp reuse. No new fix retained.
