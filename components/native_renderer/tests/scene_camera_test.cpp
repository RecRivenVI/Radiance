#include "core/render/scene_camera.hpp"
#include "core/render/view_frame.hpp"
#include "core/render/frame_clock.hpp"
#include <array>
#include <glm/gtc/matrix_transform.hpp>
#include <iostream>

int main() {
    try {
        using namespace mcvr::view;
        FrameClock clock, secondView;
        auto at = [](int64_t ms) { return FrameClock::Clock::time_point(std::chrono::milliseconds(ms)); };
        if (std::abs(clock.sample(at(0)) - 16.67f) > .001f) throw std::runtime_error("clock first frame");
        for (int i = 1; i <= 30; ++i)
            if (std::abs(clock.sample(at(i * 16)) - 16.0f) > .001f)
                throw std::runtime_error("slot rotation multiplied real frame interval");
        secondView.sample(at(0));
        if (std::abs(secondView.sample(at(20)) - 20.f) > .001f) throw std::runtime_error("view clocks were shared");
        FrameClock fallback;
        fallback.sample(at(0));
        if (std::abs(fallback.sample(at(101)) - 16.67f) > .001f)
            throw std::runtime_error("existing clock fallback changed");
        fallback.reset();
        if (std::abs(fallback.sample(at(200)) - 16.67f) > .001f) throw std::runtime_error("clock reset changed");
        // Counterexample: the removed per-slot ownership measures 48ms for 16ms frames.
        std::array<FrameClock, 3> oldSlots;
        for (int i = 0; i < 6; ++i) {
            const float old = oldSlots[i % 3].sample(at(i * 16));
            if (i >= 3 && std::abs(old - 48.f) > .001f)
                throw std::runtime_error("clock ownership counterexample not reached");
        }
        CameraHistory a, b;
        const glm::vec3 front(0, 0, -1);
        auto legacy = [](bool &first, glm::vec3 &lastPos, glm::vec3 &lastDir, glm::vec3 pos, glm::vec3 dir) {
            if (first) {
                first = false;
                lastPos = pos;
                lastDir = dir;
                return true;
            }
            const bool result =
                glm::length(pos - lastPos) > 1.0f || glm::dot(glm::normalize(dir), glm::normalize(lastDir)) < .866f;
            lastPos = pos;
            lastDir = dir;
            return result;
        };
        bool first = true;
        glm::vec3 lp(0), ld = front;
        for (int i = 0; i < 300; ++i) {
            glm::vec3 pos(float(i % 4) * .5f, float(i % 7) * .1f, 0);
            auto dir = glm::rotate(glm::mat4(1), float(i % 11) * .15f, glm::vec3(0, 1, 0)) * glm::vec4(front, 0);
            bool old = legacy(first, lp, ld, pos, glm::vec3(dir));
            if ((a.evaluate(pos, glm::vec3(dir), 1.0f, .866f) != ResetReason::NONE) != old)
                throw std::runtime_error("camera reset shadow comparison failed");
        }
        if (b.evaluate(glm::vec3(0), front, 1.0f, .866f) != ResetReason::FIRST_FRAME)
            throw std::runtime_error("interleaved view inherited another camera history");
        if (b.evaluate(glm::vec3(1, 0, 0), front, 1.0f, .866f) != ResetReason::NONE)
            throw std::runtime_error("strict translation boundary changed");
        if (b.evaluate(glm::vec3(2.0001f, 0, 0), front, 1.0f, .866f) != ResetReason::CAMERA_TRANSLATION)
            throw std::runtime_error("camera translation reason lost");
        b.invalidate(ResetReason::RESOURCE_RELOAD);
        if (b.evaluate(glm::vec3(0), front, 1.0f, .866f) != ResetReason::RESOURCE_RELOAD)
            throw std::runtime_error("reload reason lost");
        b.invalidate(ResetReason::RESIZE);
        if (b.evaluate(glm::vec3(0), front, 1.0f, .866f) != ResetReason::RESIZE)
            throw std::runtime_error("resize reason lost");
        b.invalidate(ResetReason::SDK_FAILURE);
        if (b.evaluate(glm::vec3(0), front, 1.0f, .866f) != ResetReason::SDK_FAILURE)
            throw std::runtime_error("SDK failure did not reset the next frame");
        vk::Data::WorldUBO current{}, previous{};
        current.cameraJitter = {.25f, -.125f};
        previous.cameraJitter = {-.5f, .375f};
        current.cameraProjMat = glm::perspective(1.0f, 16.f / 9.f, .1f, 10000.f);
        for (uint32_t slot = 0; slot < 6; ++slot) {
            Frame frame{slot, viewForSlot(slot, 3), {960, 540}, {1920, 1080}, current, &previous};
            frame.validate();
            if (&frame.current != &current || frame.previous != &previous || frame.view != slot / 3 ||
                frame.inputSize != glm::uvec2(960, 540) || frame.outputSize != glm::uvec2(1920, 1080) ||
                frame.current.cameraJitter != current.cameraJitter)
                throw std::runtime_error("physical frame changed camera facts or ownership");
        }
        bool invalid = false;
        try {
            Frame{0, 0, {0, 540}, {1920, 1080}, current, nullptr}.validate();
        } catch (const std::invalid_argument &) { invalid = true; }
        if (!invalid) throw std::runtime_error("zero physical extent accepted");

        for (const auto scale : {glm::vec3(30, -30, 30), glm::vec3(21, -17, 35), glm::vec3(1)}) {
            auto view = glm::translate(glm::mat4(1), glm::vec3(300, 250, -12000)) * glm::scale(glm::mat4(1), scale) *
                        glm::rotate(glm::mat4(1), 0.61f, glm::normalize(glm::vec3(1, 2, 0.5)));
            auto projection = glm::ortho(0.f, 800.f, 600.f, 0.f, 100.f, 20000.f);
            const auto oldView = view, oldProjection = projection;
            normalizeSceneCamera(view, projection);
            const auto basis = glm::mat3(view);
            if (std::abs(glm::determinant(basis) - 1.f) > 1e-5f)
                throw std::runtime_error("View is not a proper rotation");
            const auto orthogonality = glm::transpose(basis) * basis;
            for (int c = 0; c < 3; ++c)
                for (int r = 0; r < 3; ++r)
                    if (std::abs(orthogonality[c][r] - (c == r ? 1.f : 0.f)) > 1e-5f)
                        throw std::runtime_error("View retains scale or shear");
            if (glm::length(glm::inverse(view)[3] - glm::inverse(oldView)[3]) > .003f)
                throw std::runtime_error("Camera center changed");
            for (int i = -10; i <= 10; ++i) {
                const glm::vec4 point(i, i * .37f, i * -.91f, 1);
                if (glm::length(projection * view * point - oldProjection * oldView * point) > 1e-5f)
                    throw std::runtime_error("Clip position changed");
            }
            // Identical unprojected near/far points preserve orthographic rays.
            for (float depth : {-1.f, 1.f}) {
                const glm::vec4 clip(.3f, -.7f, depth, 1);
                auto a = glm::inverse(projection * view) * clip;
                auto b = glm::inverse(oldProjection * oldView) * clip;
                if (glm::length(a / a.w - b / b.w) > .01f) throw std::runtime_error("Unprojected ray changed");
            }
        }
        std::cout << "Rigid view, clip positions, camera center and rays preserved\n";
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
