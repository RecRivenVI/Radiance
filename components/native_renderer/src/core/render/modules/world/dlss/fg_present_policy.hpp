#pragma once
#include <vulkan/vulkan_core.h>

namespace mcvr::dlss {
enum class GuiOffReason {
    None,
    UserDisabled,
    Unavailable,
    NoWorld,
    BackgroundReplaced,
    FullScreenBlur,
    InputsNotReady,
    UnsupportedComposition
};

struct GuiFrameState {
    bool backgroundReplaced = false;
    bool fullScreenBlur = false;
    bool unsupportedComposition = false;

    constexpr GuiOffReason decide(bool requested, bool available, bool worldRendered, bool inputsReady) const noexcept {
        if (!requested) return GuiOffReason::UserDisabled;
        if (!available) return GuiOffReason::Unavailable;
        if (!worldRendered) return GuiOffReason::NoWorld;
        if (backgroundReplaced) return GuiOffReason::BackgroundReplaced;
        if (fullScreenBlur) return GuiOffReason::FullScreenBlur;
        if (!inputsReady) return GuiOffReason::InputsNotReady;
        if (unsupportedComposition) return GuiOffReason::UnsupportedComposition;
        return GuiOffReason::None;
    }
};

constexpr const char *reasonName(GuiOffReason reason) noexcept {
    switch (reason) {
        case GuiOffReason::None: return "world-with-ui-alpha";
        case GuiOffReason::UserDisabled: return "user-disabled";
        case GuiOffReason::Unavailable: return "unavailable";
        case GuiOffReason::NoWorld: return "no-world";
        case GuiOffReason::BackgroundReplaced: return "background-replaced";
        case GuiOffReason::FullScreenBlur: return "full-screen-menu-blur";
        case GuiOffReason::InputsNotReady: return "inputs-not-ready";
        case GuiOffReason::UnsupportedComposition: return "unsupported-composition";
    }
    return "unknown";
}

inline void applyAsyncPresentResult(VkResult result, bool &failed, bool &reset, bool &checkSurface) noexcept {
    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
        reset = true;
        checkSurface = true;
    } else if (result < 0) {
        failed = true;
    }
}
} // namespace mcvr::dlss
