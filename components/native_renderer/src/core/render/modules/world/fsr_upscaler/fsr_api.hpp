#pragma once

#ifdef MCVR_ENABLE_FFX_UPSCALER
#    ifdef _WIN32
#        include <ffx_api_loader.h>
namespace mcvr::fsr {
const ffxFunctions &functions();
bool apiAvailable();
} // namespace mcvr::fsr
// Route the SDK's inline C++ helpers through the explicitly loaded Vulkan DLL.
// This avoids process-wide DLL search path changes and a mandatory loader import.
#        define ffxCreateContext(...) mcvr::fsr::functions().CreateContext(__VA_ARGS__)
#        define ffxDestroyContext(...) mcvr::fsr::functions().DestroyContext(__VA_ARGS__)
#        define ffxConfigure(...) mcvr::fsr::functions().Configure(__VA_ARGS__)
#        define ffxQuery(...) mcvr::fsr::functions().Query(__VA_ARGS__)
#        define ffxDispatch(...) mcvr::fsr::functions().Dispatch(__VA_ARGS__)
#    else
namespace mcvr::fsr {
inline bool apiAvailable() {
    return true;
}
} // namespace mcvr::fsr
#    endif
#    include <ffx_api.hpp>
#    include <ffx_api_types.h>
#endif
