#include "core/render/line_geometry.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>

using namespace mcvr::lines;

static void require(bool value, const char *reason) {
    if (!value) throw std::runtime_error(reason);
}

static double cornerError(const std::array<glm::dvec3, 8> &reference,
                          const std::array<glm::dvec3, 8> &actual,
                          const glm::dmat4 &transform) {
    double error = 0;
    for (const auto &p : actual) {
        double best = 1e100;
        for (const auto &q : reference)
            best = std::min(best, glm::length(p - glm::dvec3(transform * glm::dvec4(q, 1))));
        error = std::max(error, best);
    }
    return error;
}

int main(int argc, char **argv) {
    try {
        require(segments(0, 8) == std::vector<std::pair<uint32_t, uint32_t>>{{0, 2}, {4, 6}}, "LINES duplicates");
        require(segments(1, 6) == std::vector<std::pair<uint32_t, uint32_t>>{{0, 2}, {2, 4}}, "LINE_STRIP duplicates");
        require(segments(2, 5) == std::vector<std::pair<uint32_t, uint32_t>>{{0, 1}, {2, 3}}, "DEBUG_LINES pairs");
        require(segments(3, 3) == std::vector<std::pair<uint32_t, uint32_t>>{{0, 1}, {1, 2}}, "DEBUG_LINE_STRIP pairs");
        for (int mode = 0; mode < 4; ++mode)
            for (uint32_t count = 0; count < 17; ++count)
                for (auto [a, b] : segments(mode, count)) require(a < count && b < count, "partial input bounds");

        const glm::dvec3 start(.2, .3, .4), end(.4, 1.2, .7);
        auto [ok, initial] = prism(start, end, .0125, {});
        require(ok, "initial prism");
        for (double degrees : {0., 20., 45., 80., 91., 180.}) {
            for (double mirror : {1., -1.}) {
                auto pose = glm::rotate(glm::dmat4(1), glm::radians(degrees), glm::normalize(glm::dvec3(.3, .7, 1)));
                pose = glm::rotate(pose, .7, glm::dvec3(1, 0, 0));
                pose = glm::scale(pose, glm::dvec3(mirror, 1, 1));
                SourceVertex source{};
                source.frameX = glm::vec3(pose[0]);
                source.frameY = glm::vec3(pose[1]);
                source.frameZ = glm::vec3(pose[2]);
                auto frame = capturedFrame(source, {});
                auto [valid, moved] =
                    prism(glm::dvec3(pose * glm::dvec4(start, 1)), glm::dvec3(pose * glm::dvec4(end, 1)), .0125, frame);
                require(valid && cornerError(initial, moved, pose) < 1e-8, "owner/part/mirror rotation equivalence");
            }
        }
        auto rotation = glm::rotate(glm::dmat4(1), .35, glm::dvec3(0, 0, 1));
        auto [badValid, bad] =
            prism(glm::dvec3(rotation * glm::dvec4(start, 1)), glm::dvec3(rotation * glm::dvec4(end, 1)), .0125, {});
        require(badValid && cornerError(initial, bad, rotation) > .001, "old missing-frame failure must be detectable");
        require(!prism(start, start, .0125, {}).first, "degenerate segment");
        require(!prism(start, end, 0, {}).first, "invalid width");
        require(!prism({std::numeric_limits<double>::quiet_NaN(), 0, 0}, end, .0125, {}).first, "NaN input");
        SourceVertex raw{};
        Frame owner{glm::dvec3(rotation[0]), glm::dvec3(rotation[1]), glm::dvec3(rotation[2])};
        require(capturedFrame(raw, owner).x == owner.x, "raw input retains owner fallback");

        SourceVertex shear{};
        shear.frameX = {2, .3f, 0};
        shear.frameY = {0, 3, .2f};
        shear.frameZ = {0, 0, -4};
        auto orthogonal = capturedFrame(shear, {});
        require(std::abs(glm::dot(orthogonal.x, orthogonal.y)) < 1e-12, "nonuniform/shear basis orthogonalized");
        require(std::abs(glm::length(orthogonal.x) - 1) < 1e-12, "physical width uses unit directions");
        require(glm::dot(glm::cross(orthogonal.x, orthogonal.y), orthogonal.z) < 0, "mirror parity retained");
        shear.reference = {2, 1, 3};
        shear.flags = explicitReference;
        auto authored = drawingFrame(shear, {});
        require(glm::length(authored.y - glm::normalize(glm::dvec3(2, 1, 3))) < 1e-12,
                "explicit reference has its own normalized field");
        shear.normal = 0xffffffffu;
        require(drawingFrame(shear, {}).y == authored.y, "source tangent cannot change the explicit reference");

        if (argc == 2) {
            std::ifstream input(argv[1], std::ios::binary);
            uint32_t header[4]{};
            input.read(reinterpret_cast<char *>(header), sizeof(header));
            require(header[0] == 0x31454e4c && header[1] == 14 && header[2] == sizeof(SourceVertex) && header[3] == 4,
                    "actual Java source ABI");
            std::array<SourceVertex, 4> vertices{};
            input.read(reinterpret_cast<char *>(vertices.data()), sizeof(vertices));
            require(bool(input), "complete Java input");
            auto frame = capturedFrame(vertices[0], {});
            glm::dmat4 pose(1);
            pose[0] = glm::dvec4(frame.x, 0);
            pose[1] = glm::dvec4(frame.y, 0);
            pose[2] = glm::dvec4(frame.z, 0);
            require((vertices[0].color >> 24) == 102, "source fractional alpha retained");
            for (const auto [a, b] : segments(0, vertices.size())) {
                auto [valid, points] = prism(vertices[a].position, vertices[b].position, .0125, frame);
                require(valid && cornerError(initial, points, pose) < 1e-6, "Java capture to actual native extrusion");
            }
            std::cout << "Actual Java storage fixture/native prism cross-check passed\n";
        }
        std::cout << "Line modes, full owner/part frame, mirrored pose and fallback passed\n";
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
