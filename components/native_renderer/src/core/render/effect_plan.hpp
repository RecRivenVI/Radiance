#pragma once
#include <cstdint>
#include <vector>
#include <array>
#include <stdexcept>
#include <algorithm>
#include <cmath>
enum OverlayPostPipelineType {
    BLUR,
    DIAGRAM,
    CREEPER,
    SPIDER,
    INVERT,
    SPIDER_BLUR_MAIN_H15,
    SPIDER_BLUR_TEMP_V15,
    SPIDER_BLUR_MAIN_H7,
    SPIDER_BLUR_TEMP_V7,
    MAX_OVERLAY_POST_PIPELINE_TYPE,
};

namespace mcvr::effects {
enum class Stage : uint8_t { WorldPost, GuiPost };
enum class Filter : uint8_t { Nearest, Linear };
struct ImageSize {
    uint32_t divisorX = 1, divisorY = 1;
};
struct Pass {
    uint32_t program;
    uint32_t output;
    std::vector<uint32_t> inputs;
    uint32_t uniformOffset = UINT32_MAX;
    Filter filter = Filter::Linear;
    std::array<float, 4> parameters{};
};
struct Plan {
    std::vector<Pass> passes;
    Stage stage = Stage::GuiPost;
    Filter filter = Filter::Linear;
    std::vector<ImageSize> targets{{}, {}, {}};
    uint32_t output = 1;
    bool fullScreenBlur = false;
};
inline void validate(const Plan &plan) {
    if (plan.passes.empty() || plan.passes.size() > 32 || plan.targets.empty() || plan.targets.size() > 8)
        throw std::invalid_argument("Screen effect pass/target budget");
    if (plan.stage != Stage::WorldPost && plan.stage != Stage::GuiPost)
        throw std::invalid_argument("Invalid effect stage");
    if (plan.fullScreenBlur && plan.stage != Stage::GuiPost)
        throw std::invalid_argument("Full-screen menu blur is a GUI role");
    std::vector<bool> available(plan.targets.size() + 1, false);
    available[0] = true;
    for (auto size : plan.targets)
        if (!size.divisorX || !size.divisorY) throw std::invalid_argument("Invalid effect image size");
    for (const auto &pass : plan.passes) {
        if (pass.output == 0 || pass.output >= available.size() || pass.inputs.empty() || pass.inputs.size() > 3 ||
            (pass.program < MAX_OVERLAY_POST_PIPELINE_TYPE && pass.program == DIAGRAM) ||
            (pass.filter != Filter::Nearest && pass.filter != Filter::Linear))
            throw std::invalid_argument("Invalid screen effect program/target");
        for (auto input : pass.inputs)
            if (input >= available.size() || input == pass.output || !available[input])
                throw std::invalid_argument("Effect input precedes its producer or aliases its output");
        for (float parameter : pass.parameters)
            if (!std::isfinite(parameter)) throw std::invalid_argument("Non-finite effect parameter");
        available[pass.output] = true;
    }
    if (!plan.output || plan.output >= available.size() || !available[plan.output])
        throw std::invalid_argument("Effect must publish a produced image");
}
inline Plan entity(OverlayPostPipelineType type) {
    if (type == CREEPER || type == INVERT) return {{{uint32_t(type), 1, {0}, 0}}, Stage::WorldPost};
    if (type != SPIDER) throw std::invalid_argument("Unsupported entity effect program");
    return {{{SPIDER_BLUR_MAIN_H15, 1, {0}},
             {SPIDER_BLUR_TEMP_V15, 2, {1}},
             {SPIDER_BLUR_MAIN_H7, 1, {0}},
             {SPIDER_BLUR_TEMP_V7, 3, {1}},
             {SPIDER, 1, {0, 2, 3}}},
            Stage::WorldPost};
}
inline Plan single(OverlayPostPipelineType type, uint32_t offset) {
    return {{{uint32_t(type), 1, {0}, offset}}, Stage::GuiPost};
}
} // namespace mcvr::effects
