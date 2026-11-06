#ifndef PRIMITIVE_POLICY_HPP
#define PRIMITIVE_POLICY_HPP
#ifdef __cplusplus
#    include <cstdint>
namespace mcvr::topology {
inline constexpr uint32_t sameMaterialSheet = 4u;
}
#else
const uint sameMaterialSheet = 4u;
#endif
#endif
