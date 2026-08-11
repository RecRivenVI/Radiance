#include "core/all_extern.hpp"
#include "core/render/display_profile.hpp"
#include <chrono>
#include <cwchar>
#ifdef _WIN32
#    include <dxgi1_6.h>
#    include <wrl/client.h>
#endif

#ifdef _WIN32
namespace {
float systemSdrWhite(HMONITOR target, float fallback) {
    MONITORINFOEXW monitorInfo{};
    monitorInfo.cbSize = sizeof(monitorInfo);
    if (!GetMonitorInfoW(target, reinterpret_cast<MONITORINFO *>(&monitorInfo))) return fallback;
    // Only active paths are needed. A hotplug between the two calls may resize the arrays.
    for (int attempt = 0; attempt < 2; ++attempt) {
        UINT32 pathCount = 0, modeCount = 0;
        if (GetDisplayConfigBufferSizes(QDC_ONLY_ACTIVE_PATHS, &pathCount, &modeCount) != ERROR_SUCCESS)
            return fallback;
        std::vector<DISPLAYCONFIG_PATH_INFO> paths(pathCount);
        std::vector<DISPLAYCONFIG_MODE_INFO> modes(modeCount);
        const auto status =
            QueryDisplayConfig(QDC_ONLY_ACTIVE_PATHS, &pathCount, paths.data(), &modeCount, modes.data(), nullptr);
        if (status == ERROR_INSUFFICIENT_BUFFER) continue;
        if (status != ERROR_SUCCESS) return fallback;
        paths.resize(pathCount);
        for (const auto &path : paths) {
            DISPLAYCONFIG_SOURCE_DEVICE_NAME source{};
            source.header = {DISPLAYCONFIG_DEVICE_INFO_GET_SOURCE_NAME, sizeof(source), path.sourceInfo.adapterId,
                             path.sourceInfo.id};
            if (DisplayConfigGetDeviceInfo(&source.header) != ERROR_SUCCESS ||
                std::wcscmp(source.viewGdiDeviceName, monitorInfo.szDevice) != 0)
                continue;
            DISPLAYCONFIG_SDR_WHITE_LEVEL white{};
            white.header = {DISPLAYCONFIG_DEVICE_INFO_GET_SDR_WHITE_LEVEL, sizeof(white), path.targetInfo.adapterId,
                            path.targetInfo.id};
            if (DisplayConfigGetDeviceInfo(&white.header) == ERROR_SUCCESS && white.SDRWhiteLevel > 0)
                return float(white.SDRWhiteLevel) * 80.f / 1000.f;
        }
        break;
    }
    return fallback;
}
} // namespace
#endif

mcvr::display::Monitor mcvr::display::monitor(GLFWwindow *window) {
    Monitor result;
#ifdef _WIN32
    if (!p_glfwGetWin32Window || !window) return result;
    const auto target = MonitorFromWindow(p_glfwGetWin32Window(window), MONITOR_DEFAULTTONEAREST);
    result.identity = reinterpret_cast<uintptr_t>(target);
    // Windows colour mode has no GLFW notification. Poll slowly; moving displays refreshes immediately.
    static Monitor cached;
    static std::chrono::steady_clock::time_point checked;
    const auto now = std::chrono::steady_clock::now();
    if (cached.identity == result.identity && now - checked < std::chrono::milliseconds(500)) return cached;
    checked = now;
    result.sdrWhiteNits = systemSdrWhite(target, cached.identity == result.identity ? cached.sdrWhiteNits : 200.f);
    // The system DLL and its factory entry point live for the renderer process.
    static const auto library = LoadLibraryExW(L"dxgi.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
    using CreateFactory = HRESULT(WINAPI *)(REFIID, void **);
    static const auto create =
        library ? reinterpret_cast<CreateFactory>(GetProcAddress(library, "CreateDXGIFactory1")) : nullptr;
    Microsoft::WRL::ComPtr<IDXGIFactory1> factory;
    if (!create || FAILED(create(IID_PPV_ARGS(&factory)))) return result;
    for (UINT adapterIndex = 0;; ++adapterIndex) {
        Microsoft::WRL::ComPtr<IDXGIAdapter1> adapter;
        if (FAILED(factory->EnumAdapters1(adapterIndex, &adapter))) break;
        if (!adapter) continue;
        for (UINT outputIndex = 0;; ++outputIndex) {
            Microsoft::WRL::ComPtr<IDXGIOutput> output;
            if (FAILED(adapter->EnumOutputs(outputIndex, &output))) break;
            DXGI_OUTPUT_DESC desc{};
            if (!output || FAILED(output->GetDesc(&desc)) || desc.Monitor != target) continue;
            Microsoft::WRL::ComPtr<IDXGIOutput6> output6;
            DXGI_OUTPUT_DESC1 hdr{};
            if (SUCCEEDED(output.As(&output6)) && SUCCEEDED(output6->GetDesc1(&hdr))) {
                result.hdrActive = hdr.ColorSpace == DXGI_COLOR_SPACE_RGB_FULL_G2084_NONE_P2020;
                if (std::isfinite(hdr.MaxLuminance) && hdr.MaxLuminance >= 80.f) result.peakNits = hdr.MaxLuminance;
                if (std::isfinite(hdr.MinLuminance) && hdr.MinLuminance >= 0.f) result.minimumNits = hdr.MinLuminance;
            }
            cached = result;
            return result;
        }
    }
#endif
    return result;
}
