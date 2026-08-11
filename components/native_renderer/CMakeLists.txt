cmake_minimum_required(VERSION 3.25)

option(MCVR_BUILD_TESTS "Build MCVR first-party contract tests" ON)
option(MCVR_ENABLE_FFX_UPSCALER "Enable FidelityFX Upscaler support" ON)
option(MCVR_ENABLE_NRD "Enable NRD denoiser support" ON)

# FidelityFX forces CMAKE_GENERATOR_PLATFORM=x64 while configuring its Vulkan
# backend. NRD's nested ShaderMake configure then forwards that Visual Studio
# platform selection to Ninja, which rejects it. Keep the supported Windows
# build path explicit and fail before dependency configuration becomes costly.
if(CMAKE_HOST_WIN32
        AND CMAKE_GENERATOR MATCHES "^Ninja"
        AND MCVR_ENABLE_FFX_UPSCALER
        AND MCVR_ENABLE_NRD)
    message(FATAL_ERROR
        "Windows builds with both FidelityFX and NRD require a Visual Studio generator. "
        "Reconfigure in a fresh build directory with, for example, "
        "-G \"Visual Studio 17 2022\" -A x64.")
endif()

project(minecraft-vulkan-renderer LANGUAGES C CXX)
include(CTest)

set(RADIANCE_TARGET_DIR "${PROJECT_SOURCE_DIR}/../../versions/1.21.1-neoforge" CACHE PATH "Radiance Target source used by cross-language contract tests")
set(MCVR_JNI_INCLUDE_DIR "${PROJECT_SOURCE_DIR}/jni")
if(NOT IS_DIRECTORY "${MCVR_JNI_INCLUDE_DIR}")
    message(FATAL_ERROR "Radiance JNI header directory does not exist: ${MCVR_JNI_INCLUDE_DIR}")
endif()
file(GLOB MCVR_JNI_HEADERS "${MCVR_JNI_INCLUDE_DIR}/*.h")
if(NOT MCVR_JNI_HEADERS)
    message(FATAL_ERROR "No generated Radiance JNI headers were found in ${MCVR_JNI_INCLUDE_DIR}")
endif()
list(LENGTH MCVR_JNI_HEADERS MCVR_JNI_HEADER_COUNT)
message(STATUS "Radiance JNI contract: ${MCVR_JNI_HEADER_COUNT} headers")

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 23)
if(MSVC)
    add_compile_definitions($<$<CONFIG:Debug>:DEBUG>)
else()
    if(CMAKE_BUILD_TYPE STREQUAL "Debug")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC -g -O0")
        add_compile_definitions(DEBUG)
    else()
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC -O2")
    endif()
endif()

set(MCVR_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/src ${MCVR_JNI_INCLUDE_DIR})
message(STATUS "Include dir: ${MCVR_INCLUDE_DIR}")

set(MCVR_INSTALL_BIN_DIR ${CMAKE_BINARY_DIR}/bin)
message(STATUS "Install bin dir: ${MCVR_INSTALL_BIN_DIR}")

set(MCVR_INSTALL_LIB_DIR ${CMAKE_INSTALL_PREFIX})
message(STATUS "Install lib dir: ${MCVR_INSTALL_LIB_DIR}")

include_directories(${MCVR_INCLUDE_DIR})

# GLFW
add_subdirectory(third_party/glfw EXCLUDE_FROM_ALL)
set(GLFW_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/glfw/include)

# Volk
set(VOLK_BUILD_TESTS OFF CACHE BOOL "Disable tests")
set(VOLK_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples")
add_subdirectory(third_party/volk EXCLUDE_FROM_ALL)
if(WIN32)
    target_compile_definitions(volk PRIVATE VK_USE_PLATFORM_WIN32_KHR)
endif()
set(VOLK_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/volk)

# VMA
add_subdirectory(third_party/vma EXCLUDE_FROM_ALL)
set(VMA_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/vma/include)

# GLM
set(GLM_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/glm)

# VK.XML
set(VK_XML_PATH ${PROJECT_SOURCE_DIR}/third_party/vulkan_headers/registry/vk.xml)

# STB
set(STB_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/stb)

# tinyexpr
add_library(tinyexpr STATIC third_party/tinyexpr/tinyexpr.c)
set_target_properties(tinyexpr PROPERTIES POSITION_INDEPENDENT_CODE ON)
set(TINYEXPR_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/tinyexpr)

# DLSS
set(MCVR_DLSS_ROOT "${PROJECT_SOURCE_DIR}/third_party/DLSS")
set(MCVR_DLSS_INCLUDE_DIR "${MCVR_DLSS_ROOT}/include")
set(MCVR_DLSS_LIBRARY_DIR "${MCVR_DLSS_ROOT}/lib")
if(NOT EXISTS "${MCVR_DLSS_INCLUDE_DIR}/nvsdk_ngx_vk.h")
    message(FATAL_ERROR "The NVIDIA DLSS SDK submodule is incomplete: ${MCVR_DLSS_INCLUDE_DIR}")
endif()

add_library(ngx INTERFACE)
target_include_directories(ngx INTERFACE "${MCVR_DLSS_INCLUDE_DIR}")
include(cmake/Streamline.cmake)

# JNI
find_package(Java 21 EXACT REQUIRED COMPONENTS Development)
find_package(JNI REQUIRED)
message(STATUS "${JNI_INCLUDE_DIRS}")
message(STATUS "${JNI_LIBRARIES}")

option(USE_AMD "Enable AMD specific optimizations/features" ON)
if(USE_AMD)
    message(STATUS "AMD Support Enabled")
else()
    message(STATUS "AMD Support Disabled")
endif()

set(MCVR_BUILT_IN_SHADER_PACKS vanilla-pt advanced)
message(STATUS "Built-in shader packs: ${MCVR_BUILT_IN_SHADER_PACKS}")

# FidelityFX Upscaler (FSR3 Upscaler only)
if(MCVR_ENABLE_FFX_UPSCALER)
    message(STATUS "FidelityFX Upscaler Enabled")

    set(FFX_API_BACKEND VK_X64 CACHE STRING "FidelityFX API backend" FORCE)

    # Disable everything except FSR3 Upscaler
    set(FFX_DENOISER OFF CACHE BOOL "Build FFX Denoiser" FORCE)
    set(FFX_ALL OFF CACHE BOOL "Build all FFX components" FORCE)
    set(FFX_SSSR OFF CACHE BOOL "Build FFX SSSR" FORCE)
    set(FFX_FSR3 OFF CACHE BOOL "Build FFX FSR3" FORCE) # Full FSR3 (FrameGen) disabled
    set(FFX_FSR3UPSCALER ON CACHE BOOL "Build FFX FSR3 Upscaler" FORCE)
    set(FFX_FI OFF CACHE BOOL "Build FFX Frame Interpolation" FORCE)
    set(FFX_OF OFF CACHE BOOL "Build FFX Optical Flow" FORCE)

    set(FFX_AUTO_COMPILE_SHADERS ON CACHE BOOL "Compile shaders automatically as a prebuild step." FORCE)

    # Use ffx-api which includes sdk internally, providing the necessary API symbols (ffxQuery)
    set(FFX_BIN_PATH ${CMAKE_BINARY_DIR}/ffx_sdk CACHE STRING "FFX binary output path" FORCE)
    add_subdirectory(third_party/FidelityFX-SDK/ffx-api EXCLUDE_FROM_ALL)

    if(TARGET ffx_backend_vk_x64)
        target_include_directories(ffx_backend_vk_x64 PRIVATE ${VOLK_INCLUDE_DIR})
    endif()

    # Note: ffx_denoiser_x64 target will NOT be created, so don't link against it.
else()
    message(STATUS "FidelityFX Upscaler Disabled")
endif()

# NRD Denoiser
if(MCVR_ENABLE_NRD)
    message(STATUS "NRD Denoiser Enabled")

    set(NRD_STATIC_LIBRARY ON CACHE BOOL "Build NRD as a static library" FORCE)
    set(NRD_DISABLE_SHADER_COMPILATION ON CACHE BOOL "Disable NRD shader compilation" FORCE)
    set(NRD_SHADERS_PATH "${PROJECT_SOURCE_DIR}/third_party/nrd/_Shaders" CACHE STRING "NRD shader headers path" FORCE)
    set(NRD_NORMAL_ENCODING "2" CACHE STRING "Normal encoding variant (0-4, matches nrd::NormalEncoding)" FORCE)
    set(NRD_ROUGHNESS_ENCODING "1" CACHE STRING "Roughness encoding variant (0-2, matches nrd::RoughnessEncoding)" FORCE)

    add_subdirectory(third_party/nrd EXCLUDE_FROM_ALL)

    set(NRD_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/nrd/Include)

    if(TARGET NRD)
        target_include_directories(NRD PRIVATE "${NRD_SHADERS_PATH}")
    endif()
else()
    message(STATUS "NRD Denoiser Disabled")
endif()

# Intel XeSS
option(MCVR_ENABLE_XESS "Enable Intel XeSS support" ON)
if(MCVR_ENABLE_XESS)
    set(XESS_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/third_party/xess/inc)
    if(EXISTS "${XESS_INCLUDE_DIR}/xess/xess.h")
        message(STATUS "Intel XeSS Enabled")
    else()
        message(WARNING "MCVR_ENABLE_XESS is ON but extern/xess is missing; building without XeSS backend.")
        set(MCVR_ENABLE_XESS OFF)
    endif()
else()
    message(STATUS "Intel XeSS Disabled")
endif()

# minizip
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries" FORCE)
set(MZ_BUILD_TESTS OFF CACHE BOOL "Disable minizip tests" FORCE)
set(MZ_BUILD_UNIT_TESTS OFF CACHE BOOL "Disable minizip unit tests" FORCE)
set(MZ_SANITIZER OFF CACHE STRING "Disable minizip sanitizers" FORCE)
set(MZ_FETCH_ZLIB ON CACHE BOOL "Fetch zlib automatically" FORCE)
set(MZ_ZSTD OFF CACHE BOOL "Disable zstd in minizip-ng due to upstream target mismatch" FORCE)
set(BZIP2_REPOSITORY "https://gitlab.com/bzip2/bzip2.git" CACHE STRING
    "Pinned mirror used when minizip-ng fetches bzip2" FORCE)
set(BZIP2_TAG "6a8690fc8d26c815e798c588f796eabe9d684cf0" CACHE STRING
    "Pinned bzip2 1.0.8 commit used by minizip-ng" FORCE)
# Stable releases, never moving master/stable branches.
set(ZLIB_TAG "2.3.3" CACHE STRING "zlib-ng stable release" FORCE)
set(LIBLZMA_TAG "v5.8.4" CACHE STRING "XZ stable release" FORCE)
set(PPMD_TAG "26.03" CACHE STRING "7-Zip PPMd stable release" FORCE)
add_subdirectory(third_party/minizip-ng EXCLUDE_FROM_ALL)
set_target_properties(minizip PROPERTIES POSITION_INDEPENDENT_CODE ON)

# nlohmann json
add_subdirectory(third_party/json EXCLUDE_FROM_ALL)

add_subdirectory(src)

if(MCVR_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
