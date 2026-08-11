#include <windows.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <GFSDK_Aftermath_GpuCrashDump.h>
#include <GFSDK_Aftermath_GpuCrashDumpDecoding.h>
#include "sdk_path.hpp"
#include "bounded_wait.hpp"
#include "capture_output.hpp"
#include <memory>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <thread>

namespace {
static_assert(GFSDK_Aftermath_CrashDump_Status_CollectingDataFailed == 2);
static_assert(GFSDK_Aftermath_CrashDump_Status_Finished == 4);
HMODULE sdk{};
PFN_GFSDK_Aftermath_DisableGpuCrashDumps disableDumps{};
PFN_GFSDK_Aftermath_GetCrashDumpStatus getStatus{};
PFN_GFSDK_Aftermath_GetShaderDebugInfoIdentifier shaderId{};
PFN_GFSDK_Aftermath_GetShaderHashSpirv shaderHash{};
std::unique_ptr<local_capture::Output> output;
std::atomic<bool> active{}, waited{};
std::atomic<uint32_t> lastStatus{};
std::atomic<uint32_t> lastQuery{};
uint32_t deviceFlags = VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV |
                       VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_DEBUG_INFO_BIT_NV;
std::filesystem::path directory;
void GFSDK_AFTERMATH_CALL onDump(const void *data, uint32_t size, void *) noexcept {
    try {
        static std::atomic<uint32_t> dumps{0};
        char name[100];
        std::snprintf(name, sizeof(name), "gpu-%lu-%u.nv-gpudmp", GetCurrentProcessId(), ++dumps);
        const bool saved = output->save(local_capture::Kind::dump, name, data, size);
        std::fprintf(stderr, "[AftermathAgent] dump %s file=%s bytes=%u\n", saved ? "saved" : "budget-rejected", name,
                     size);
    } catch (...) { std::fprintf(stderr, "[AftermathAgent] dump callback I/O failed\n"); }
}
void GFSDK_AFTERMATH_CALL onShader(const void *data, uint32_t size, void *) noexcept {
    try {
        GFSDK_Aftermath_ShaderDebugInfoIdentifier id{};
        if (shaderId(GFSDK_Aftermath_Version_API, data, size, &id) != GFSDK_Aftermath_Result_Success) {
            std::fprintf(stderr, "[AftermathAgent] shader debug identifier failed\n");
            return;
        }
        char name[100];
        std::snprintf(name, sizeof(name), "shader-%016llx-%016llx.nvdbg", id.id[0], id.id[1]);
        if (!output->save(local_capture::Kind::shader, name, data, size))
            std::fprintf(stderr, "[AftermathAgent] shader debug budget exhausted\n");
    } catch (...) { std::fprintf(stderr, "[AftermathAgent] shader callback I/O failed\n"); }
}
void GFSDK_AFTERMATH_CALL description(PFN_GFSDK_Aftermath_AddGpuCrashDumpDescription add, void *) noexcept {
    add(GFSDK_Aftermath_GpuCrashDumpDescriptionKey_ApplicationName, "Radiance isolated GPU diagnosis");
    add(GFSDK_Aftermath_GpuCrashDumpDescriptionKey_ApplicationVersion, "local-agent-2");
}
} // namespace

extern "C" __declspec(dllexport) uint32_t RadianceGpuCaptureFlags(uint32_t abi) noexcept {
    // Use SDK enums: automatic checkpoints and extra shader-error faulting are excluded.
    return abi == 1 && active.load() ? deviceFlags : 0u;
}
extern "C" __declspec(dllexport) uint64_t RadianceGpuCaptureShaderHash(uint32_t abi,
                                                                       const uint32_t *code,
                                                                       uint32_t size) noexcept {
    if (abi != 1 || !active.load() || !code || !size || size % 4 || !shaderHash) return 0;
    GFSDK_Aftermath_SpirvCode shader{code, size};
    GFSDK_Aftermath_ShaderBinaryHash hash{};
    return shaderHash(GFSDK_Aftermath_Version_API, &shader, &hash) == GFSDK_Aftermath_Result_Success ? hash.hash : 0;
}
extern "C" __declspec(dllexport) uint32_t RadianceGpuCaptureWait(uint32_t milliseconds) noexcept {
    if (!active.load() || waited.exchange(true)) return lastStatus.load();
    try {
        lastStatus = local_capture::wait(
            milliseconds,
            [] {
                GFSDK_Aftermath_CrashDump_Status status = GFSDK_Aftermath_CrashDump_Status_Unknown;
                const auto result = getStatus(&status);
                lastQuery = uint32_t(result);
                return local_capture::Status{uint32_t(status), result == GFSDK_Aftermath_Result_Success};
            },
            [] {
                return std::chrono::duration_cast<std::chrono::milliseconds>(
                           std::chrono::steady_clock::now().time_since_epoch())
                    .count();
            },
            [](uint32_t ms) { std::this_thread::sleep_for(std::chrono::milliseconds(ms)); });
        std::fprintf(stderr,
                     "[AftermathAgent] bounded pre-release wait status=%u query=0x%x budgetMs=%u; no GPU idle wait\n",
                     lastStatus.load(), lastQuery.load(), std::min(milliseconds, 5000u));
    } catch (...) { std::fprintf(stderr, "[AftermathAgent] status wait failed\n"); }
    return lastStatus.load();
}
extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *, char *options, void *) noexcept {
    try {
        if (!options || !*options) return JNI_ERR;
        const char *resourceOnly = std::getenv("MCVR_AFTERMATH_RESOURCE_ONLY");
        if (resourceOnly && std::string_view(resourceOnly) == "1")
            deviceFlags = VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV;
        directory = std::filesystem::absolute(std::filesystem::u8path(options)) /
                    (std::to_string(std::chrono::duration_cast<std::chrono::microseconds>(
                                        std::chrono::system_clock::now().time_since_epoch())
                                        .count()) +
                     "-pid" + std::to_string(GetCurrentProcessId()));
        output = std::make_unique<local_capture::Output>(directory);
        sdk = LoadLibraryExW(sdkPath, nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        if (!sdk) {
            std::fprintf(stderr, "[AftermathAgent] SDK load failed WindowsError=%lu\n", GetLastError());
            return JNI_ERR;
        }
        auto enable =
            (PFN_GFSDK_Aftermath_EnableGpuCrashDumps)GetProcAddress(sdk, "GFSDK_Aftermath_EnableGpuCrashDumps");
        disableDumps =
            (PFN_GFSDK_Aftermath_DisableGpuCrashDumps)GetProcAddress(sdk, "GFSDK_Aftermath_DisableGpuCrashDumps");
        getStatus = (PFN_GFSDK_Aftermath_GetCrashDumpStatus)GetProcAddress(sdk, "GFSDK_Aftermath_GetCrashDumpStatus");
        shaderId = (PFN_GFSDK_Aftermath_GetShaderDebugInfoIdentifier)GetProcAddress(
            sdk, "GFSDK_Aftermath_GetShaderDebugInfoIdentifier");
        shaderHash = (PFN_GFSDK_Aftermath_GetShaderHashSpirv)GetProcAddress(sdk, "GFSDK_Aftermath_GetShaderHashSpirv");
        if (!enable || !disableDumps || !getStatus || !shaderId || !shaderHash) return JNI_ERR;
        auto result = enable(GFSDK_Aftermath_Version_API, GFSDK_Aftermath_GpuCrashDumpWatchedApiFlags_Vulkan,
                             GFSDK_Aftermath_GpuCrashDumpFeatureFlags_DeferDebugInfoCallbacks, onDump, onShader,
                             description, nullptr, nullptr);
        active = result == GFSDK_Aftermath_Result_Success;
        std::fprintf(stderr, "[AftermathAgent] Agent_OnLoad before SERVICE pid=%lu enable=0x%x deviceFlags=%u\n",
                     GetCurrentProcessId(), unsigned(result), deviceFlags);
        const auto outputPath = directory.u8string();
        std::fprintf(
            stderr,
            "[AftermathAgent] output=%s deferredShaderDebug=true automaticCallStacks=false extraShaderErrors=false\n",
            reinterpret_cast<const char *>(outputPath.c_str()));
        return active.load() ? JNI_OK : JNI_ERR;
    } catch (...) {
        std::fprintf(stderr, "[AftermathAgent] initialization failed\n");
        return JNI_ERR;
    }
}
extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *) noexcept {
    if (active.exchange(false) && disableDumps) {
        auto result = disableDumps();
        const auto debug = output->statistics(local_capture::Kind::shader);
        const auto dumps = output->statistics(local_capture::Kind::dump);
        std::fprintf(stderr,
                     "[AftermathAgent] disabled result=0x%x debugFiles=%u debugBytes=%llu dumps=%u rejected=%u\n",
                     unsigned(result), debug.files, (unsigned long long)debug.bytes, dumps.files,
                     debug.rejected + dumps.rejected);
    }
    // Keep the DLL mapped until process exit, including in callback/error paths.
}
