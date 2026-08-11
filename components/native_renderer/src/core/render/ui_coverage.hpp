#pragma once

#include <algorithm>
#include <vulkan/vulkan_core.h>

namespace mcvr::ui {

// Values are shared with DrawTargetContract.GuiRole at the JNI boundary.
enum class GuiRole : uint32_t { Foreground = 0, BackgroundEffect = 1, WorldReplacement = 2 };

inline bool
backgroundModulation(VkBlendFactor source, VkBlendFactor destination, VkBlendOp operation, bool logicOp) noexcept {
    return !logicOp && operation == VK_BLEND_OP_ADD && source == VK_BLEND_FACTOR_ZERO &&
           (destination == VK_BLEND_FACTOR_SRC_COLOR || destination == VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR);
}

inline bool
backgroundInversion(VkBlendFactor source, VkBlendFactor destination, VkBlendOp operation, bool logicOp) noexcept {
    return !logicOp && operation == VK_BLEND_OP_ADD && source == VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR &&
           destination == VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
}

inline void coverageEquation(VkColorBlendEquationEXT &equation, bool logicOp, bool backgroundEffect = false) noexcept {
    const bool modulation =
        backgroundEffect || backgroundModulation(equation.srcColorBlendFactor, equation.dstColorBlendFactor,
                                                 equation.colorBlendOp, logicOp);
    equation.srcAlphaBlendFactor = modulation ? VK_BLEND_FACTOR_ZERO : VK_BLEND_FACTOR_ONE;
    equation.dstAlphaBlendFactor = modulation ? VK_BLEND_FACTOR_ONE : VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    equation.alphaBlendOp = VK_BLEND_OP_ADD;
}

inline bool
sourceOverColor(VkBlendFactor source, VkBlendFactor destination, VkBlendOp operation, bool logicOp) noexcept {
    return !logicOp && operation == VK_BLEND_OP_ADD &&
           (source == VK_BLEND_FACTOR_SRC_ALPHA || source == VK_BLEND_FACTOR_ONE) &&
           destination == VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
}

enum class DrawComposition {
    Attachment,
    Replace,
    SourceOver,
    BackgroundInversion,
    BackgroundModulation,
    OtherBackground
};

// Classify the captured state, never a shader name or GUI producer. Off-screen
// attachments keep source alpha; the main target uses alpha as UI coverage.
inline DrawComposition
drawComposition(bool attachment, bool blend, bool logicOp, const VkColorBlendEquationEXT &equation) noexcept {
    if (attachment) return DrawComposition::Attachment;
    if (!blend && !logicOp) return DrawComposition::Replace;
    if (sourceOverColor(equation.srcColorBlendFactor, equation.dstColorBlendFactor, equation.colorBlendOp, logicOp))
        return DrawComposition::SourceOver;
    if (blend &&
        backgroundInversion(equation.srcColorBlendFactor, equation.dstColorBlendFactor, equation.colorBlendOp, logicOp))
        return DrawComposition::BackgroundInversion;
    if (blend && backgroundModulation(equation.srcColorBlendFactor, equation.dstColorBlendFactor, equation.colorBlendOp,
                                      logicOp))
        return DrawComposition::BackgroundModulation;
    return DrawComposition::OtherBackground;
}

// Porter-Duff source-over coverage. Color blending remains controlled by the
// intercepted Minecraft state; this value is the independent FG UI mask.
constexpr float accumulateCoverage(float destination, float source) noexcept {
    destination = std::clamp(destination, 0.0f, 1.0f);
    source = std::clamp(source, 0.0f, 1.0f);
    return source + destination * (1.0f - source);
}

} // namespace mcvr::ui
