#ifndef QUERY_POLICY_HPP
#define QUERY_POLICY_HPP
#include "render_abi.hpp"
#ifdef __cplusplus
#    pragma once
#    include <cstdint>
namespace mcvr::query {
using uint = uint32_t;
#    define ABI_QUERY_PREFIX(name) mcvr::abi::name
#    define QUERY_CONST inline constexpr
#    define QUERY_FN constexpr
#else
#    define ABI_QUERY_PREFIX(name) name
#    define QUERY_CONST const
#    define QUERY_FN
#endif

QUERY_CONST uint queryDirect = 0u;
QUERY_CONST uint queryTransport = 1u;
QUERY_CONST uint queryPriority = 2u;
QUERY_CONST uint queryWorld = ABI_QUERY_PREFIX(QUERY_WORLD);
QUERY_CONST uint querySelf = ABI_QUERY_PREFIX(QUERY_SELF);
QUERY_CONST uint queryOverlay = ABI_QUERY_PREFIX(QUERY_PRIORITY);
QUERY_CONST uint queryReceiveOnly = ABI_QUERY_PREFIX(QUERY_HAND);
QUERY_CONST uint queryWeather = ABI_QUERY_PREFIX(QUERY_WEATHER);
QUERY_CONST uint queryParticle = ABI_QUERY_PREFIX(QUERY_PARTICLE);
QUERY_CONST uint queryCloud = ABI_QUERY_PREFIX(QUERY_CLOUD);

QUERY_FN uint queryMask(uint purpose, bool firstPerson) {
    if (purpose == queryPriority) return queryOverlay;
    uint world = queryWorld | queryWeather | queryParticle | queryCloud;
    if (purpose == queryTransport) return world | querySelf | queryOverlay;
    return world | queryReceiveOnly | (firstPerson ? 0u : querySelf);
}
QUERY_FN uint advanceQuery(uint purpose, uint lobe) {
    return purpose == queryDirect && lobe == 2u ? queryDirect : queryTransport;
}
QUERY_FN bool queryIncludes(uint geometry, uint purpose, bool firstPerson) {
    return (geometry & queryMask(purpose, firstPerson)) != 0u;
}

#undef ABI_QUERY_PREFIX
#undef QUERY_CONST
#undef QUERY_FN
#ifdef __cplusplus
} // namespace mcvr::query
#endif
#endif
