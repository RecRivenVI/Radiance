file(GLOB_RECURSE SOURCE_FILES CONFIGURE_DEPENDS "*.cpp")

if(NOT MCVR_BUILD_SCENE_REPLAY)
    list(REMOVE_ITEM SOURCE_FILES "${CMAKE_CURRENT_SOURCE_DIR}/diagnostics/scene_replay.cpp")
endif()
add_library(core SHARED ${SOURCE_FILES})
if(MCVR_BUILD_SCENE_REPLAY)
    if(NOT WIN32)
        message(FATAL_ERROR "The standalone scene replay launcher currently requires Windows")
    endif()
    target_compile_definitions(core PUBLIC MCVR_BUILD_SCENE_REPLAY)
    add_executable(mcvr_scene_replay EXCLUDE_FROM_ALL "${PROJECT_SOURCE_DIR}/tools/scene_replay/main.cpp")
    target_compile_features(mcvr_scene_replay PRIVATE cxx_std_23)
    add_custom_target(mcvr_scene_replay_runtime
        COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_INSTALL_PREFIX}"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_FILE:core>" "${CMAKE_INSTALL_PREFIX}/scene_replay_core.dll"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_PDB_FILE:core>" "${CMAKE_INSTALL_PREFIX}/scene_replay_core.pdb"
        DEPENDS core mcvr_scene_replay)
endif()

include(FetchContent)
FetchContent_Declare(spirv_reflect
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Reflect.git
    GIT_TAG f3e261fda74ca81b44d1941931c6b7b5fe236d0e)
FetchContent_GetProperties(spirv_reflect)
if(NOT spirv_reflect_POPULATED)
    FetchContent_Populate(spirv_reflect)
endif()
set(MCVR_SPIRV_REFLECT_DIR "${spirv_reflect_SOURCE_DIR}")
add_library(mcvr_spirv_reflect STATIC "${MCVR_SPIRV_REFLECT_DIR}/spirv_reflect.c")
set_target_properties(mcvr_spirv_reflect PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(mcvr_spirv_reflect PUBLIC "${MCVR_SPIRV_REFLECT_DIR}")
target_link_libraries(core PRIVATE mcvr_spirv_reflect)

# Immutable sampling data is independent of the selected upscaler runtime.
file(DOWNLOAD
    "https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK/v1.1.4/sdk/src/components/sssr/samplerBlueNoiseErrorDistribution_128x128_OptimizedFor_2d2d2d2d_1spp.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/sampler_blue_noise.cpp"
    EXPECTED_HASH SHA256=0fd62a8f00e6e59b8c9aa8c2b4236eb361fdd2deda6f3ddf4be74c5de59623bc)
target_include_directories(core PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_link_libraries(core PUBLIC ngx ${JNI_LIBRARIES} VulkanMemoryAllocator volk::volk glfw minizip tinyexpr
                                   nlohmann_json::nlohmann_json)

if(DEFINED ENV{VULKAN_SDK} AND NOT "$ENV{VULKAN_SDK}" STREQUAL "")
    set(MCVR_VULKAN_SDK "$ENV{VULKAN_SDK}")
else()
    set(MCVR_VULKAN_SDK "/home/jiong/VulkanSDK/1.4.328.1")
endif()

find_path(SHADERC_INCLUDE_DIR shaderc/shaderc.hpp
    HINTS
    "${MCVR_VULKAN_SDK}/x86_64/include"
    "${MCVR_VULKAN_SDK}/include"
)
find_library(SHADERC_LIBRARY
    NAMES shaderc_combined shaderc_shared shaderc
    HINTS
    "${MCVR_VULKAN_SDK}/x86_64/lib"
    "${MCVR_VULKAN_SDK}/lib"
)

if(NOT SHADERC_INCLUDE_DIR OR NOT SHADERC_LIBRARY)
    message(FATAL_ERROR "shaderc was not found. Set VULKAN_SDK or install shaderc.")
endif()

target_include_directories(core PUBLIC ${MCVR_STREAMLINE_INCLUDE_DIR} ${GENTOOL_OUTPUT_DIR} ${VOLK_INCLUDE_DIR} ${GLFW_INCLUDE_DIR} ${VMA_INCLUDE_DIR} ${STB_INCLUDE_DIR} ${TINYEXPR_INCLUDE_DIR} ${JNI_INCLUDE_DIRS} ${GLM_INCLUDE_DIR} ${SHADERC_INCLUDE_DIR})
target_link_libraries(core PUBLIC ${SHADERC_LIBRARY})
target_compile_definitions(core PUBLIC VK_NO_PROTOTYPES CORE_LIB)
if(USE_AMD)
    target_compile_definitions(core PUBLIC USE_AMD)
endif()
if(MCVR_ENABLE_FFX_UPSCALER)
    target_link_libraries(core PUBLIC mcvr_ffx)
    target_compile_definitions(core PUBLIC MCVR_ENABLE_FFX_UPSCALER)
endif()
if(MCVR_ENABLE_NRD)
    target_link_libraries(core PUBLIC NRD)
    target_include_directories(core PUBLIC ${NRD_INCLUDE_DIR})
    target_compile_definitions(core PUBLIC NRD_STATIC_LIBRARY=1)
    target_compile_definitions(core PUBLIC MCVR_ENABLE_NRD)
endif()
if(MCVR_ENABLE_XESS)
    find_library(XESS_RUNTIME_LIB
        NAMES xess libxess
        PATHS
            "${PROJECT_SOURCE_DIR}/third_party/xess/lib"
            "${PROJECT_SOURCE_DIR}/third_party/xess/bin"
    )

    if(XESS_RUNTIME_LIB)
        target_link_libraries(core PUBLIC "${XESS_RUNTIME_LIB}")
        target_include_directories(core PUBLIC ${XESS_INCLUDE_DIR})
        target_compile_definitions(core PUBLIC MCVR_ENABLE_XESS)

        if(WIN32)
            file(GLOB XESS_RUNTIME_DLLS "${PROJECT_SOURCE_DIR}/third_party/xess/bin/libxess*.dll")
            if(XESS_RUNTIME_DLLS)
                install(FILES ${XESS_RUNTIME_DLLS} DESTINATION ${MCVR_INSTALL_LIB_DIR})
                install(FILES ${XESS_RUNTIME_DLLS} DESTINATION ${MCVR_INSTALL_BIN_DIR})
            else()
                message(WARNING "XeSS enabled and linked, but no runtime DLLs were found in extern/xess/bin.")
            endif()
        endif()
    else()
        message(WARNING "XeSS headers were found but no XeSS runtime library was found; building without XeSS backend.")
    endif()
endif()
if(MSVC)
    target_compile_definitions(core PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
    target_compile_options(core PRIVATE /utf-8)
endif()

set_target_properties(core PROPERTIES CXX_VISIBILITY_PRESET default VISIBILITY_INLINES_HIDDEN OFF)

install(TARGETS core DESTINATION ${MCVR_INSTALL_LIB_DIR})
install(TARGETS core DESTINATION ${MCVR_INSTALL_BIN_DIR})

# The component owns runtime licenses formerly kept in Target resources.
install(FILES "${PROJECT_SOURCE_DIR}/src/runtime/XESS_LICENCE.txt" DESTINATION ${MCVR_INSTALL_LIB_DIR} RENAME XESS_LICENCE.txt)
