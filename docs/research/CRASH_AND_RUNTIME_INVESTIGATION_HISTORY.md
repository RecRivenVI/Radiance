# Native crash and runtime investigation history

Observed on: 2026-08-08 through 2026-09-17.
Sources: five completed Codex tasks, the retained crash-investigation artifacts, Radiance
`f9dd73bb0ab3463d952e0c720db06ac4010f36ce`, and MCVR
`5150670796380bf128fecec551c864f480bb05f9`.
Status: archived historical synthesis; the retained pipeline-layout lifetime change is implemented,
but the NVIDIA driver mechanism remains unexplained.

## Question and scope

This record consolidates the native-crash evidence that was spread across the initial port,
compatibility implementation, real-client acceptance, and focused crash-investigation tasks. It
also records which old experiments were later superseded. It does not claim a new runtime test and
does not make old branch names, artifact hashes, or workspace layouts current again.

The investigations encountered several different failure classes. They must remain separate:

| Failure class | Representative symptom | Evidentiary meaning |
| --- | --- | --- |
| Java or Mixin startup failure | target/shadow mismatch before native rendering begins | Java integration defect; no Vulkan root-cause implication |
| Native CPU access violation | `EXCEPTION_ACCESS_VIOLATION` in `nvoglv64.dll` below `vkCmdDrawIndexed` | driver entered invalid state or consumed invalid/lifetime-sensitive input; not itself proof of a Vulkan rule violation |
| Vulkan device loss | `VK_ERROR_DEVICE_LOST`, sometimes with NVIDIA device-fault data | asynchronous GPU failure; the fence wait or submit that reports it is usually an observation point |
| Native C++ exception | for example an uncaught initialization or descriptor exception | application/native control-flow failure; diagnose separately from driver AVs |
| Audio/OpenAL or launcher failure | unrelated subsystem error or wrapper exit | not rendering readiness or a renderer crash unless correlated evidence says otherwise |

## Driver access violation and descriptor lifetime

Real-client runs reproduced at least two nearby NVIDIA-driver access-violation forms while the
driver was processing indexed draws: one read `0xffffffffffffffff` near
`nvoglv64.dll+0xf1c729`, and another read `0x104` near `nvoglv64.dll+0xf1c708`. Visible triggers
included title text, the receiving screen, subtitles, and hotbar items. Those labels identify where
the failure became observable; they do not establish that text or UI content caused it.

Two synchronization corrections were independently valid but did not eliminate the access
violation:

- the depth/stencil transition and aspect handling correction;
- the world-to-HUD depth handoff correction.

The color synchronization correction was also retained on its own merits. None of these should be
reverted merely because they were insufficient to solve the driver crash.

The descriptor experiment evolved in three stages:

1. E-02 kept the complete descriptor table alive. The first survival was only a clue, because one
   validation-enabled or timing-shifted run cannot prove a root cause.
2. Repeated controlled runs later produced a strong A/B relationship: the collected baseline
   configurations crashed 7/7 times, while whole-table E-02 survived 6/6 bounded runs.
3. The retained minimal change keeps the `VkPipelineLayout`, its descriptor-set layouts, and their
   `Device` ownership alive for the lifetime of `DynamicGraphicsPipeline`. That version survived
   3/3 300-second automated world runs and a validation-plus-synchronization run of roughly ten
   minutes. A normal-close run also exited cleanly.

The canonical run table and hashes are in the
[E-02 retest report](../history/port-artifacts-20260921/RadianceCrashInvestigation/20260917-PID15936/e02-retest-20260917/EXPERIMENT.md).
The current MCVR baseline contains the pipeline-layout ownership token. Descriptor binding also
retains the resource currently assigned to each slot; rebinding replaces that slot's retained
owner. This source behavior must not be described as retaining every historical resource generation.

The result is a repeatedly effective lifetime workaround, not a demonstrated specification
violation. Vulkan maintenance4 does not generally require a pipeline layout to remain alive after
pipeline creation, so the evidence does not explain why the NVIDIA driver is sensitive to this
lifetime. Do not restore the broader whole-table retention unless new evidence requires it.

## Device loss and DLSS Ray Reconstruction

A separate investigation reproduced NVIDIA GPU MMU page-fault/device-loss behavior in both MCVR
and NVIDIA's official Vulkan Ray Reconstruction sample while the machine was in the bad state. It
also reproduced without RTSS, OBS, and NVIDIA capture overlays, so those tools were not necessary
conditions.

One attempted 591.59 downgrade was invalid as a version comparison because Windows updated the
driver to 591.86 during the run. After a clean reinstall and reboot, driver 610.88 ran two official
RR samples for about 10 hours 57 minutes and Radiance for about 10 hours 46 minutes without a
device loss. This makes damaged or inconsistent driver/install/runtime state the strongest
historical explanation for that episode. It does not prove that 610.88 is universally safe or that
Radiance and MCVR contain no device-loss bugs.

Automatic device-loss recovery was investigated and found incomplete. Recreating only selected
Vulkan objects cannot safely recover Java/native world ownership, Streamline state, swapchain
ownership, resource generations, and third-party integrations. The partial recovery path was
removed. Current work should fail closed, preserve fault evidence, and avoid claiming recovery
until a complete lifecycle design exists.

## Diagnostic lessons

- Use the repository `run/` instance for agent-controlled diagnostics and timestamp each run. The
  user's Prism instance is for explicit manual acceptance; do not create or rewrite a Prism root as
  an automation shortcut.
- Record artifact hashes, native DLL/JAR pairing, configuration, world-entry evidence, elapsed
  in-world time, and exit class. A matching hash proves identity, not launch or visual acceptance.
- Separate diagnostics, behavior-changing experiments, and production fixes. Forced waits,
  serialization, skipped draws, or extended lifetimes are localization tools until their semantics
  are justified.
- Validation layers can change timing. A long validation-enabled survival is useful evidence but
  cannot alone close a timing-sensitive crash.
- Bound expensive tracing. A Vulkan API-dump attempt produced roughly 12.25 GB of output, consumed
  about 8.6 GiB of working set, and the layer itself failed. Prefer narrow frame ranges, trigger
  gates, stable call-site identifiers, and compact counters.
- Fourteen headless/fixture groups did not reproduce the real-client driver crash. Treat those
  passes as contract coverage, not as substitutes for a displayed client exercising the failing
  path.
- Do not call a compatibility delivery accepted merely because Java, native, shader, JNI, and
  packaging gates pass. The first full client acceptance can still expose startup, driver, or
  presentation failures.

## Independent validation findings

The validation work also captured issues that were not shown to cause the driver AV:

- two 12-byte `vkCmdCopyBuffer` write-after-write hazards;
- an LDR color-attachment transition whose source scope omitted
  `COLOR_ATTACHMENT_OUTPUT`, with the producer in tone mapping and the later consumer in post
  rendering;
- vertex/index buffer usage VUIDs for buffers missing the required usage flags.

These remain separately tracked in the
[Known Vulkan validation findings](../ROADMAP.md#known-vulkan-validation-findings). They require
producer attribution and controlled baseline comparison before being grouped with any crash root
cause.

## Evidence boundary

This document is a static synthesis of historical runtime evidence and the named current source
baselines. No new client, GPU, or visual test was performed for this extraction. The retained
minimal lifetime change has strong bounded A/B/A evidence; the underlying NVIDIA-driver behavior,
long-duration stability across mod combinations and dimensions, and the independent validation
findings remain open.
