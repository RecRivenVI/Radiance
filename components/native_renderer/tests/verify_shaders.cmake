if(NOT DEFINED MCVR_SHADER_DIR OR NOT DEFINED MCVR_INSTALL_DIR OR NOT DEFINED MCVR_SOURCE_DIR)
    message(FATAL_ERROR "Shader verification requires explicit shader, install and source directories")
endif()
file(GLOB_RECURSE SPIRV_FILES "${MCVR_SHADER_DIR}/*.spv")
if(NOT SPIRV_FILES)
    message(FATAL_ERROR "No compiled SPIR-V shaders found under ${MCVR_SHADER_DIR}")
endif()

foreach(SPIRV_FILE IN LISTS SPIRV_FILES)
    file(SIZE "${SPIRV_FILE}" SPIRV_SIZE)
    if(SPIRV_SIZE EQUAL 0)
        message(FATAL_ERROR "Compiled shader is empty: ${SPIRV_FILE}")
    endif()
endforeach()

foreach(OBSOLETE_SPIRV IN ITEMS
    "${MCVR_SHADER_DIR}/world/post_render/world_post_star_frag.spv"
    "${MCVR_SHADER_DIR}/world/post_render/world_post_star_vert.spv"
    "${MCVR_SHADER_DIR}/world/post_render/world_post_frag.spv"
    "${MCVR_SHADER_DIR}/world/post_render/world_post_vert.spv"
    "${MCVR_SHADER_DIR}/world/post_render/world_post_text_frag.spv"
    "${MCVR_SHADER_DIR}/world/post_render/world_post_text_vert.spv"
    "${MCVR_SHADER_DIR}/world/post_render/light_map_frag.spv"
    "${MCVR_SHADER_DIR}/world/post_render/light_map_vert.spv"
)
    if(EXISTS "${OBSOLETE_SPIRV}")
        message(FATAL_ERROR "Obsolete non-fullscreen post-render shader survived cleanup: ${OBSOLETE_SPIRV}")
    endif()
endforeach()

foreach(PACK_NAME IN ITEMS vanilla-pt advanced)
    set(PACK_ARCHIVE "${MCVR_INSTALL_DIR}/shaders/world/ray_tracing/${PACK_NAME}.zip")
    if(NOT EXISTS "${PACK_ARCHIVE}")
        message(FATAL_ERROR "Missing built-in shader pack: ${PACK_ARCHIVE}")
    endif()
    execute_process(
        COMMAND "${CMAKE_COMMAND}" -E tar tf "${PACK_ARCHIVE}"
        RESULT_VARIABLE PACK_LIST_RESULT
        OUTPUT_VARIABLE PACK_CONTENTS
        ERROR_VARIABLE PACK_LIST_ERROR
    )
    if(NOT PACK_LIST_RESULT EQUAL 0)
        message(FATAL_ERROR "Cannot inspect ${PACK_ARCHIVE}: ${PACK_LIST_ERROR}")
    endif()
    foreach(REQUIRED_ENTRY IN ITEMS
        "priority/priority.rgen"
        "util/priority_image.glsl"
        "util/priority_surface.glsl"
        "priority/composite.comp"
    )
        string(FIND "${PACK_CONTENTS}" "${REQUIRED_ENTRY}" ENTRY_INDEX)
        if(ENTRY_INDEX EQUAL -1)
            message(FATAL_ERROR "${PACK_NAME} is missing priority geometry shader: ${REQUIRED_ENTRY}")
        endif()
    endforeach()
    if(PACK_CONTENTS MATCHES "post_render/(render_(star|text|outline)|post_outline)")
        message(FATAL_ERROR "${PACK_NAME} still packages a legacy non-fullscreen post-render shader")
    endif()
endforeach()
file(READ "${MCVR_SOURCE_DIR}/src/shader/util/ray.glsl" ray_state_source)
string(REGEX MATCH "rayStepLowMask = (0x[0-9A-Fa-f]+)u" bounce_match "${ray_state_source}")
if(NOT bounce_match)
    message(FATAL_ERROR "Missing low path step mask in ray.glsl")
endif()
math(EXPR ray_used_bits "${CMAKE_MATCH_1} | (3 << 12)")
foreach(state_file IN ITEMS ray.glsl ui_primary_visibility.glsl ray_medium.glsl)
    file(READ "${MCVR_SOURCE_DIR}/src/shader/util/${state_file}" state_source)
    string(REGEX MATCHALL "ray[A-Za-z]+Bit = 1u << [0-9]+u" state_flags "${state_source}")
    foreach(flag IN LISTS state_flags)
        string(REGEX MATCH "<< ([0-9]+)u" bit_index "${flag}")
        math(EXPR flag_mask "1 << ${CMAKE_MATCH_1}")
        math(EXPR collision "${flag_mask} & ${ray_used_bits}")
        if(collision)
            message(FATAL_ERROR "Overlapping ray state field: ${state_file}: ${flag}")
        endif()
        math(EXPR ray_used_bits "${ray_used_bits} | ${flag_mask}")
    endforeach()
endforeach()
