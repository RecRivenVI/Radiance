#include <windows.h>
#include <jni.h>
#include <vulkan/vulkan_core.h>
#include <GFSDK_Aftermath_GpuCrashDump.h>
#include <GFSDK_Aftermath_GpuCrashDumpDecoding.h>
#include "sdk_path.hpp"
#include "bounded_wait.hpp"
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
std::atomic<bool> active{}, waited{};
std::atomic<uint32_t> lastStatus{};
uint32_t deviceFlags = VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV |
    VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_DEBUG_INFO_BIT_NV;
std::filesystem::path directory;
std::mutex outputMutex;
uint64_t debugBytes{};
uint32_t dumps{}, debugFiles{};
void write(const char *name, const void *data, uint32_t size) {
    std::ofstream out(directory/name, std::ios::binary);
    out.write(static_cast<const char*>(data), size);
    out.close();
    if (!out) throw std::runtime_error("diagnostic file write failed");
}
void GFSDK_AFTERMATH_CALL onDump(const void *data, uint32_t size, void*) noexcept {
    try {
        std::lock_guard lock(outputMutex);
        if (++dumps > 4 || size > 128u*1024*1024) {
            std::fprintf(stderr,"[AftermathAgent] dump exceeds bounded capture budget\n");return;
        }
        char name[100];std::snprintf(name,sizeof(name),"gpu-%lu-%u.nv-gpudmp",GetCurrentProcessId(),dumps);
        write(name,data,size);
        std::fprintf(stderr,"[AftermathAgent] dump saved file=%s bytes=%u\n",name,size);
    } catch (...) { std::fprintf(stderr,"[AftermathAgent] dump callback I/O failed\n"); }
}
void GFSDK_AFTERMATH_CALL onShader(const void *data, uint32_t size, void*) noexcept {
    try {
        std::lock_guard lock(outputMutex);
        if (debugFiles >= 4096 || size > 16u*1024*1024 || debugBytes + size > 512ull*1024*1024) {
            std::fprintf(stderr,"[AftermathAgent] shader debug budget exhausted\n");return;
        }
        GFSDK_Aftermath_ShaderDebugInfoIdentifier id{};
        if (shaderId(GFSDK_Aftermath_Version_API,data,size,&id)!=GFSDK_Aftermath_Result_Success) {
            std::fprintf(stderr,"[AftermathAgent] shader debug identifier failed\n");return;
        }
        char name[100];std::snprintf(name,sizeof(name),"shader-%016llx-%016llx.nvdbg",id.id[0],id.id[1]);
        write(name,data,size);debugBytes+=size;++debugFiles;
    } catch (...) { std::fprintf(stderr,"[AftermathAgent] shader callback I/O failed\n"); }
}
void GFSDK_AFTERMATH_CALL description(PFN_GFSDK_Aftermath_AddGpuCrashDumpDescription add,void*) noexcept {
    add(GFSDK_Aftermath_GpuCrashDumpDescriptionKey_ApplicationName,"Radiance isolated GPU diagnosis");
    add(GFSDK_Aftermath_GpuCrashDumpDescriptionKey_ApplicationVersion,"local-agent-1");
}
}

extern "C" __declspec(dllexport) uint32_t RadianceGpuCaptureFlags(uint32_t abi) noexcept {
    // Use SDK enums: automatic checkpoints and extra shader-error faulting are excluded.
    return abi==1 && active.load() ? deviceFlags : 0u;
}
extern "C" __declspec(dllexport) uint32_t RadianceGpuCaptureWait(uint32_t milliseconds) noexcept {
    if (!active.load() || waited.exchange(true)) return lastStatus.load();
    try {
        lastStatus=local_capture::wait(milliseconds, [] {
            GFSDK_Aftermath_CrashDump_Status status=GFSDK_Aftermath_CrashDump_Status_Unknown;
            const auto result=getStatus(&status);
            return local_capture::Status{uint32_t(status),result==GFSDK_Aftermath_Result_Success};
        }, [] { return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
        }, [](uint32_t ms) { std::this_thread::sleep_for(std::chrono::milliseconds(ms)); });
        std::fprintf(stderr,"[AftermathAgent] bounded pre-release wait status=%u budgetMs=%u\n",lastStatus.load(),std::min(milliseconds,5000u));
    } catch (...) { std::fprintf(stderr,"[AftermathAgent] status wait failed\n"); }
    return lastStatus.load();
}
extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM*, char *options, void*) noexcept {
    try {
        if (!options || !*options) return JNI_ERR;
        const char *resourceOnly=std::getenv("MCVR_AFTERMATH_RESOURCE_ONLY");
        if(resourceOnly && std::string_view(resourceOnly)=="1")
            deviceFlags=VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV;
        directory=std::filesystem::absolute(std::filesystem::u8path(options));
        std::filesystem::create_directories(directory);
        sdk=LoadLibraryExW(sdkPath,nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        if(!sdk)return JNI_ERR;
        auto enable=(PFN_GFSDK_Aftermath_EnableGpuCrashDumps)GetProcAddress(sdk,"GFSDK_Aftermath_EnableGpuCrashDumps");
        disableDumps=(PFN_GFSDK_Aftermath_DisableGpuCrashDumps)GetProcAddress(sdk,"GFSDK_Aftermath_DisableGpuCrashDumps");
        getStatus=(PFN_GFSDK_Aftermath_GetCrashDumpStatus)GetProcAddress(sdk,"GFSDK_Aftermath_GetCrashDumpStatus");
        shaderId=(PFN_GFSDK_Aftermath_GetShaderDebugInfoIdentifier)GetProcAddress(sdk,"GFSDK_Aftermath_GetShaderDebugInfoIdentifier");
        if(!enable||!disableDumps||!getStatus||!shaderId)return JNI_ERR;
        auto result=enable(GFSDK_Aftermath_Version_API,GFSDK_Aftermath_GpuCrashDumpWatchedApiFlags_Vulkan,
            GFSDK_Aftermath_GpuCrashDumpFeatureFlags_Default,onDump,onShader,description,nullptr,nullptr);
        active=result==GFSDK_Aftermath_Result_Success;
        std::fprintf(stderr,"[AftermathAgent] Agent_OnLoad before SERVICE pid=%lu enable=0x%x deviceFlags=%u\n",GetCurrentProcessId(),unsigned(result),deviceFlags);
        return active.load()?JNI_OK:JNI_ERR;
    } catch (...) { std::fprintf(stderr,"[AftermathAgent] initialization failed\n");return JNI_ERR; }
}
extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM*) noexcept {
    if(active.exchange(false)&&disableDumps) {
        auto result=disableDumps();
        std::fprintf(stderr,"[AftermathAgent] disabled result=0x%x debugFiles=%u bytes=%llu\n",unsigned(result),debugFiles,debugBytes);
    }
    // Keep the DLL mapped until process exit, including in callback/error paths.
}
