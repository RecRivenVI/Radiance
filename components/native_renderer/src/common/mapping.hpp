#include "render_abi.hpp"
#ifdef __cplusplus
#    pragma once
#    include <cstddef>
#    include <glm/glm.hpp>
#    define T_VEC2 glm::vec2
#    define T_IVEC2 glm::ivec2
#    define T_VEC3 glm::vec3
#    define T_VEC4 glm::vec4
#    define T_IVEC4 glm::ivec4
#    define T_MAT4 glm::mat4
#    define T_FLOAT float
#    define T_UINT uint32_t
#    define T_INT int32_t
#    define T_BOOL bool
#    define T_DVEC4 glm::dvec4
#else
#    extension GL_EXT_shader_explicit_arithmetic_types_float64 : require
#    define T_VEC2 vec2
#    define T_IVEC2 ivec2
#    define T_VEC3 vec3
#    define T_VEC4 vec4
#    define T_IVEC4 ivec4
#    define T_MAT4 mat4
#    define T_FLOAT float
#    define T_UINT uint
#    define T_INT int
#    define T_BOOL bool
#    define T_DVEC4 dvec4
#    define WORLD_MASK QUERY_WORLD
#    define PLAYER_MASK QUERY_SELF
#    define PRIORITY_MASK QUERY_PRIORITY
#    define HAND_MASK QUERY_HAND
#    define WEATHER_MASK QUERY_WEATHER
#    define PARTICLE_MASK QUERY_PARTICLE
#    define CLOUD_MASK QUERY_CLOUD
#    define BOAT_WATER_MASK QUERY_BOAT_WATER_MASK
#endif
