#include "core/render/ui_coverage.hpp"

#include <cmath>
#include <iostream>
#include <stdexcept>

namespace {
void expect(float actual, float expected, const char *label) {
    if (std::abs(actual - expected) > 1e-6f) throw std::runtime_error(std::string(label) + " coverage mismatch");
}
} // namespace

int main() {
    try {
        using Composition = mcvr::ui::DrawComposition;
        const auto classify = [](bool attachment, bool blend, bool logic, VkBlendFactor src, VkBlendFactor dst,
                                 VkBlendOp operation = VK_BLEND_OP_ADD) {
            return mcvr::ui::drawComposition(attachment, blend, logic, {src, dst, operation});
        };
        for (bool attachment : {false, true}) {
            const auto expectDraw = [&](Composition expected, bool blend, bool logic, VkBlendFactor src,
                                        VkBlendFactor dst, VkBlendOp operation = VK_BLEND_OP_ADD) {
                if (classify(attachment, blend, logic, src, dst, operation) !=
                    (attachment ? Composition::Attachment : expected))
                    throw std::runtime_error("GUI composition lost source state or attachment alpha");
            };
            expectDraw(Composition::Replace, false, false, VK_BLEND_FACTOR_SRC_ALPHA,
                       VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            expectDraw(Composition::SourceOver, true, false, VK_BLEND_FACTOR_SRC_ALPHA,
                       VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            expectDraw(Composition::SourceOver, true, false, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            expectDraw(Composition::BackgroundInversion, true, false, VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR,
                       VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR);
            expectDraw(Composition::BackgroundModulation, true, false, VK_BLEND_FACTOR_ZERO,
                       VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR);
            expectDraw(Composition::OtherBackground, true, false, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE);
            expectDraw(Composition::OtherBackground, true, false, VK_BLEND_FACTOR_SRC_ALPHA,
                       VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_OP_REVERSE_SUBTRACT);
            expectDraw(Composition::OtherBackground, false, true, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO);
        }
        expect(mcvr::ui::accumulateCoverage(0.0f, 0.0f), 0.0f, "world");
        expect(mcvr::ui::accumulateCoverage(0.0f, 0.5f), 0.5f, "half-transparent UI");
        expect(mcvr::ui::accumulateCoverage(0.5f, 0.5f), 0.75f, "overlapping UI");
        expect(mcvr::ui::accumulateCoverage(0.3f, 1.0f), 1.0f, "opaque UI");
        expect(mcvr::ui::accumulateCoverage(0.6f, 0.0f), 0.6f, "transparent draw");
        expect(mcvr::ui::accumulateCoverage(-1.0f, 2.0f), 1.0f, "clamping");
        if (!mcvr::ui::sourceOverColor(VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_OP_ADD,
                                       false))
            throw std::runtime_error("ordinary blend classification");
        if (mcvr::ui::sourceOverColor(VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR, VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR,
                                      VK_BLEND_OP_ADD, false))
            throw std::runtime_error("inversion incorrectly accepted as source-over");
        const float alpha = .5f, ui = .3f * alpha;
        for (float generatedBackground : {.1f, .9f}) {
            const float real = ui + (1 - alpha) * .2f;
            const float recovered = real - (1 - alpha) * .2f;
            expect(recovered + (1 - alpha) * generatedBackground, ui + (1 - alpha) * generatedBackground,
                   "recomposition changes only background");
        }
        VkColorBlendEquationEXT inverse{VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR, VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR,
                                        VK_BLEND_OP_ADD};
        mcvr::ui::coverageEquation(inverse, false);
        if (inverse.srcAlphaBlendFactor != VK_BLEND_FACTOR_ONE ||
            inverse.dstAlphaBlendFactor != VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
            throw std::runtime_error("inverse blend must write resolved opaque UI, not HUD-less");
        for (float source : {0.f, .3f, 1.f})
            for (float background : {.1f, .9f}) {
                const auto transform = [source](float x) { return source + (1 - 2 * source) * x; };
                const float resolved = transform(ui + (1 - alpha) * background);
                const float coverage = 1.f;
                expect(resolved + (1 - coverage) * background, transform(ui + (1 - alpha) * background),
                       "resolved inversion keeps original RGB with pure HUD-less");
            }
        VkColorBlendEquationEXT dim{VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_OP_ADD};
        mcvr::ui::coverageEquation(dim, false, true);
        if (dim.srcAlphaBlendFactor != VK_BLEND_FACTOR_ZERO || dim.dstAlphaBlendFactor != VK_BLEND_FACTOR_ONE)
            throw std::runtime_error("declared screen background must preserve existing HUD coverage");
        for (float hudAlpha : {0.f, .25f, .75f, 1.f})
            for (float background : {0.f, .4f, 1.f})
                for (float amount : {0.f, .3f, 1.f}) {
                    const float hud = .7f * hudAlpha;
                    const float sourceColor = .2f;
                    const float final = sourceColor * amount + (1 - amount) * (hud + (1 - hudAlpha) * background);
                    const float world = sourceColor * amount + (1 - amount) * background;
                    const float resolvedHud = sourceColor * amount * hudAlpha + (1 - amount) * hud;
                    expect(resolvedHud + (1 - hudAlpha) * world, final, "colored background keeps HUD order");
                    const float menuAlpha = .4f;
                    const float menu = .8f * menuAlpha;
                    const float combinedAlpha = mcvr::ui::accumulateCoverage(hudAlpha, menuAlpha);
                    expect(menu + (1 - menuAlpha) * resolvedHud + (1 - combinedAlpha) * world,
                           menu + (1 - menuAlpha) * final, "later menu remains ordinary coverage");
                }
        std::cout << "UI coverage source-over contract passed\n";
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
