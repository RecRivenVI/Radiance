#ifndef RENDER_ABI_HPP
#define RENDER_ABI_HPP
#ifdef __cplusplus
#    include <cstdint>
namespace mcvr::abi {
#    define RENDER_ABI(name, value) inline constexpr uint32_t name = value;
#else
#    define RENDER_ABI(name, value) const uint name = value;
#endif
#include "render_abi.def"
#undef RENDER_ABI
#ifdef __cplusplus
inline constexpr uint32_t values[]{
#    define RENDER_ABI(name, value) name,
#    include "render_abi.def"
#    undef RENDER_ABI
};
} // namespace mcvr::abi
#endif
#endif
