# Superseded M3 probe artifacts

The custom-provider/custom-Radiance-UI M3 scope and inventory in this directory are rejected and superseded.

Current authority is:

- `D:\Workspaces\Artifacts\RadianceM3Official\M3_OFFICIAL_EARLY_WINDOW_CORRECTION_REPORT.md`
- `D:\Workspaces\Artifacts\RadianceM3Official\m3-official-runtime-closure.json`

The old statement that official `DisplayWindow` was unreachable because a custom provider was selected is withdrawn. The current implementation keeps `fmlearlywindow` enabled, runs official `DisplayWindow`, and intercepts its GL drawing at the bridge/native boundary for native Vulkan translation.
