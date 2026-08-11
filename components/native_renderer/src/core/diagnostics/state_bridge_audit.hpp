#pragma once
#include "frame_profile.hpp"
#include <array>
#include <cstdlib>
#include <string_view>

// Opt-in diagnostic window only. Fixed counters, no per-request allocation/logging.
namespace mcvr::state_audit {
enum Kind {
    scissorEnable,
    scissor,
    viewport,
    blendEnable,
    blendConstants,
    logicEnable,
    blendFunc,
    blendOp,
    colorMask,
    logicOp,
    depthTest,
    depthWrite,
    stencilTest,
    depthFunc,
    stencilFrontFunc,
    stencilBackFunc,
    stencilFrontOp,
    stencilBackOp,
    stencilFrontMask,
    stencilBackMask,
    lineWidth,
    polygonMode,
    cullMode,
    frontFace,
    biasEnable,
    bias,
    clearColor,
    clearDepth,
    clearStencil,
    count
};
inline constexpr const char *names[count] = {"state-request/scissor-enable",     "state-request/scissor",
                                             "state-request/viewport",           "state-request/blend-enable",
                                             "state-request/blend-constants",    "state-request/logic-enable",
                                             "state-request/blend-func",         "state-request/blend-op",
                                             "state-request/color-mask",         "state-request/logic-op",
                                             "state-request/depth-test",         "state-request/depth-write",
                                             "state-request/stencil-test",       "state-request/depth-func",
                                             "state-request/stencil-front-func", "state-request/stencil-back-func",
                                             "state-request/stencil-front-op",   "state-request/stencil-back-op",
                                             "state-request/stencil-front-mask", "state-request/stencil-back-mask",
                                             "state-request/line-width",         "state-request/polygon-mode",
                                             "state-request/cull-mode",          "state-request/front-face",
                                             "state-request/bias-enable",        "state-request/bias",
                                             "state-request/clear-color",        "state-request/clear-depth",
                                             "state-request/clear-stencil"};
inline constexpr const char *changeNames[count] = {"state-change/scissor-enable",     "state-change/scissor",
                                                   "state-change/viewport",           "state-change/blend-enable",
                                                   "state-change/blend-constants",    "state-change/logic-enable",
                                                   "state-change/blend-func",         "state-change/blend-op",
                                                   "state-change/color-mask",         "state-change/logic-op",
                                                   "state-change/depth-test",         "state-change/depth-write",
                                                   "state-change/stencil-test",       "state-change/depth-func",
                                                   "state-change/stencil-front-func", "state-change/stencil-back-func",
                                                   "state-change/stencil-front-op",   "state-change/stencil-back-op",
                                                   "state-change/stencil-front-mask", "state-change/stencil-back-mask",
                                                   "state-change/line-width",         "state-change/polygon-mode",
                                                   "state-change/cull-mode",          "state-change/front-face",
                                                   "state-change/bias-enable",        "state-change/bias",
                                                   "state-change/clear-color",        "state-change/clear-depth",
                                                   "state-change/clear-stencil"};
struct Counts {
    std::array<uint64_t, count> requests{}, changes{}, idle{};
    uint64_t contexts = 0, queries = 0, framebufferBinds = 0, framebufferChanges = 0, activeEnds = 0;
};
inline constexpr const char *idleNames[count] = {"state-idle/scissor-enable",     "state-idle/scissor",
                                                 "state-idle/viewport",           "state-idle/blend-enable",
                                                 "state-idle/blend-constants",    "state-idle/logic-enable",
                                                 "state-idle/blend-func",         "state-idle/blend-op",
                                                 "state-idle/color-mask",         "state-idle/logic-op",
                                                 "state-idle/depth-test",         "state-idle/depth-write",
                                                 "state-idle/stencil-test",       "state-idle/depth-func",
                                                 "state-idle/stencil-front-func", "state-idle/stencil-back-func",
                                                 "state-idle/stencil-front-op",   "state-idle/stencil-back-op",
                                                 "state-idle/stencil-front-mask", "state-idle/stencil-back-mask",
                                                 "state-idle/line-width",         "state-idle/polygon-mode",
                                                 "state-idle/cull-mode",          "state-idle/front-face",
                                                 "state-idle/bias-enable",        "state-idle/bias",
                                                 "state-idle/clear-color",        "state-idle/clear-depth",
                                                 "state-idle/clear-stencil"};
inline thread_local Counts counters;
inline bool configured() noexcept {
    static const bool value = [] {
        const char *v = std::getenv("RADIANCE_STATE_AUDIT");
        return v && std::string_view(v) == "1";
    }();
    return value;
}
inline bool enabled() noexcept {
    return configured() && profile::enabled() && profile::frame != 0;
}
inline void record(Kind kind, bool changed, bool idle) noexcept {
    ++counters.requests[kind];
    counters.changes[kind] += changed;
    counters.idle[kind] += idle;
}
inline void context() noexcept {
    if (enabled()) ++counters.contexts;
}
inline void query() noexcept {
    if (enabled()) ++counters.queries;
}
inline void bind(bool changed, bool active) noexcept {
    if (!enabled()) return;
    ++counters.framebufferBinds;
    counters.framebufferChanges += changed;
    counters.activeEnds += active;
}
inline void flush() noexcept {
    if (!configured()) return;
    if (profile::enabled() && profile::frame != 0) {
        for (int i = 0; i < count; ++i) {
            // Domain 5: additive counts; the byte field is always zero.
            if (counters.requests[i]) profile::emit(profile::frame, 5, names[i], counters.requests[i], 0);
            if (counters.changes[i]) profile::emit(profile::frame, 5, changeNames[i], counters.changes[i], 0);
            if (counters.idle[i]) profile::emit(profile::frame, 5, idleNames[i], counters.idle[i], 0);
        }
        profile::emit(profile::frame, 5, "state-bridge/context-acquisitions", counters.contexts, 0);
        profile::emit(profile::frame, 5, "state-bridge/queries", counters.queries, 0);
        profile::emit(profile::frame, 5, "state-bridge/framebuffer-binds", counters.framebufferBinds, 0);
        profile::emit(profile::frame, 5, "state-bridge/framebuffer-changes", counters.framebufferChanges, 0);
        profile::emit(profile::frame, 5, "state-bridge/active-pass-ends", counters.activeEnds, 0);
    }
    counters = {};
}
} // namespace mcvr::state_audit
