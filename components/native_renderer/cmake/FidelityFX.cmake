# The official Windows Vulkan loader selects a provider supported by the device.
if(WIN32)
    set(MCVR_FFX_ROOT "${PROJECT_SOURCE_DIR}/third_party/FidelityFX-SDK/Kits/FidelityFX")
    add_library(mcvr_ffx INTERFACE)
    target_include_directories(mcvr_ffx INTERFACE
        "${MCVR_FFX_ROOT}/api/include" "${MCVR_FFX_ROOT}/upscalers/include")
    foreach(name amd_fidelityfx_loader_vk amd_fidelityfx_upscaler_vk)
        install(FILES "${MCVR_FFX_ROOT}/signedbin/${name}.dll" DESTINATION "${MCVR_INSTALL_LIB_DIR}/fsr")
        install(FILES "${MCVR_FFX_ROOT}/signedbin/${name}.dll" DESTINATION "${MCVR_INSTALL_BIN_DIR}/fsr")
    endforeach()
    install(FILES "${MCVR_FFX_ROOT}/docs/license.md"
        DESTINATION "${MCVR_INSTALL_LIB_DIR}/fsr" RENAME FidelityFX-LICENSE.txt)
else()
    # The official binary provider is Windows-only. Keep the tested portable
    # Vulkan implementation for Linux, rather than disabling that backend.
    include(FetchContent)
    FetchContent_Declare(ffx_portable_sdk
        GIT_REPOSITORY https://github.com/Interstellarss/FidelityFX-SDK.git
        GIT_TAG d08c34ca8a9d2d187abc1b8e5ed6b40f86482a9d)
    FetchContent_GetProperties(ffx_portable_sdk)
    if(NOT ffx_portable_sdk_POPULATED)
        FetchContent_Populate(ffx_portable_sdk)
    endif()
    set(FFX_API_BACKEND VK_X64 CACHE STRING "FidelityFX API backend" FORCE)
    foreach(feature DENOISER ALL SSSR FSR3 FI OF)
        set(FFX_${feature} OFF CACHE BOOL "Only the portable upscaler is used" FORCE)
    endforeach()
    set(FFX_FSR3UPSCALER ON CACHE BOOL "Build the portable upscaler" FORCE)
    set(FFX_AUTO_COMPILE_SHADERS ON CACHE BOOL "Compile portable shaders" FORCE)
    set(FFX_BIN_PATH "${CMAKE_BINARY_DIR}/ffx_sdk" CACHE STRING "FFX output path" FORCE)
    add_subdirectory("${ffx_portable_sdk_SOURCE_DIR}/ffx-api" "${CMAKE_BINARY_DIR}/ffx_portable" EXCLUDE_FROM_ALL)
    target_include_directories(ffx_backend_vk_x64 PRIVATE ${VOLK_INCLUDE_DIR})
    add_library(mcvr_ffx INTERFACE)
    target_include_directories(mcvr_ffx INTERFACE "${ffx_portable_sdk_SOURCE_DIR}/ffx-api/include/ffx_api")
    target_link_libraries(mcvr_ffx INTERFACE amd_fidelityfx_vk ffx_backend_vk_x64 ffx_fsr3upscaler_x64)
endif()
