#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>
#include <vulkan/vulkan_core.h>

struct GLFWwindow;
namespace mcvr::display {
enum class Encoding : uint32_t { Srgb, Pq, ScRgb };
struct Monitor {
    uintptr_t identity = 0;
    bool hdrActive = false;
    float minimumNits = 0.005f;
    float peakNits = 600.0f;
    float sdrWhiteNits = 200.0f;
};
Monitor monitor(GLFWwindow *window);
struct Profile {
    VkSurfaceFormatKHR surface{VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR};
    Encoding encoding = Encoding::Srgb;
    float referenceWhite = 200;
    float uiWhite = 200;
    float peakNits = 200;
    bool hdr() const {
        return encoding != Encoding::Srgb;
    }
};
inline Profile select(const std::vector<VkSurfaceFormatKHR> &formats,
                      bool enabled,
                      const Monitor &monitor,
                      float referenceWhite,
                      float uiWhite,
                      float requestedPeak) {
    Profile result;
    result.referenceWhite = std::clamp(std::isfinite(referenceWhite) ? referenceWhite : 200.f, 80.f, 400.f);
    const float selectedUiWhite = uiWhite <= 0 ? monitor.sdrWhiteNits : uiWhite;
    result.uiWhite = std::clamp(std::isfinite(selectedUiWhite) ? selectedUiWhite : 200.f, 80.f, 1000.f);
    if (enabled && monitor.hdrActive) {
        // HDR10 remains usable with Streamline FG; scRGB is the independent fallback.
        for (auto format :
             {VK_FORMAT_A2B10G10R10_UNORM_PACK32, VK_FORMAT_A2R10G10B10_UNORM_PACK32, VK_FORMAT_R16G16B16A16_SFLOAT}) {
            const auto space = format == VK_FORMAT_R16G16B16A16_SFLOAT ? VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT :
                                                                         VK_COLOR_SPACE_HDR10_ST2084_EXT;
            for (auto candidate : formats)
                if (candidate.format == format && candidate.colorSpace == space) {
                    result.surface = candidate;
                    result.encoding = format == VK_FORMAT_R16G16B16A16_SFLOAT ? Encoding::ScRgb : Encoding::Pq;
                    const float peak =
                        requestedPeak > 0 && std::isfinite(requestedPeak) ? requestedPeak : monitor.peakNits;
                    result.peakNits = std::clamp(std::isfinite(peak) ? peak : 600.f,
                                                 std::max(result.referenceWhite, result.uiWhite), 10000.f);
                    return result;
                }
        }
    }
    for (auto format :
         {VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_R8G8B8A8_SRGB, VK_FORMAT_B8G8R8A8_SRGB}) {
        for (auto candidate : formats)
            if ((candidate.format == format || (formats.size() == 1 && candidate.format == VK_FORMAT_UNDEFINED)) &&
                candidate.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                result.surface = {format, candidate.colorSpace};
                return result;
            }
    }
    // Surface implementations must expose a usable format; retain their advertised encoding.
    for (auto candidate : formats)
        if (candidate.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            result.surface = candidate;
            return result;
        }
    if (!formats.empty()) result.surface = formats.front();
    return result;
}
} // namespace mcvr::display
