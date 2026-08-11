#include "core/render/display_profile.hpp"
#include <stdexcept>
#include <limits>
static void check(bool value) {
    if (!value) throw std::runtime_error("Display format/monitor/luminance policy failed");
}
int main() {
    using namespace mcvr::display;
    const VkSurfaceFormatKHR sdr{VK_FORMAT_B8G8R8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR};
    const VkSurfaceFormatKHR pq{VK_FORMAT_A2B10G10R10_UNORM_PACK32, VK_COLOR_SPACE_HDR10_ST2084_EXT};
    const VkSurfaceFormatKHR rgbPq{VK_FORMAT_A2R10G10B10_UNORM_PACK32, VK_COLOR_SPACE_HDR10_ST2084_EXT};
    const VkSurfaceFormatKHR scrgb{VK_FORMAT_R16G16B16A16_SFLOAT, VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT};
    Monitor on{1, true, 0.005f, 800}, off{1, false};
    check(select({sdr, pq}, true, on, 200, 160, 0).encoding == Encoding::Pq);
    check(select({sdr, pq}, true, off, 200, 160, 0).surface.format == sdr.format);
    check(!select({sdr, pq}, false, on, 200, 160, 0).hdr());
    check(select({sdr, rgbPq, scrgb}, true, on, 200, 160, 0).encoding == Encoding::Pq);
    check(select({sdr, scrgb}, true, on, 200, 160, 0).encoding == Encoding::ScRgb);
    auto profile = select({sdr, pq}, true, on, 200, 160, 0);
    check(profile.peakNits == 800 && profile.referenceWhite == 200 && profile.uiWhite == 160);
    check(select({sdr, pq}, true, on, 200, 160, 1200).peakNits == 1200);
    check(select({sdr, pq}, true, on, 400, 160, 100).peakNits == 400);
    check(!select({sdr, {VK_FORMAT_R16G16B16A16_SFLOAT, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR}}, true, on, 200, 200, 0)
               .hdr());
    auto fallback = select({{VK_FORMAT_UNDEFINED, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR}}, true, on, 200, 200, 0);
    check(fallback.surface.format == VK_FORMAT_R8G8B8A8_UNORM && !fallback.hdr());
    on.sdrWhiteNits = 480;
    check(select({sdr, pq}, true, on, 200, 0, 0).uiWhite == 480);
    check(select({sdr, pq}, true, on, 200, 0, 0).referenceWhite == 200);
    check(select({sdr, pq}, true, on, 200, 120, 0).uiWhite == 120);
    on.sdrWhiteNits = 320;
    check(select({sdr, pq}, true, on, 200, 0, 0).uiWhite == 320);
    on.sdrWhiteNits = std::numeric_limits<float>::quiet_NaN();
    check(select({sdr, pq}, true, on, 200, 0, 0).uiWhite == 200);
    check(select({sdr, pq}, true, on, 200, 1000, 800).peakNits == 1000);
}
