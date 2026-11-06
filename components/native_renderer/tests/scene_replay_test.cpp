#include "core/diagnostics/scene_replay_contract.hpp"
#include "core/diagnostics/scene_replay_camera.hpp"
#include "core/diagnostics/scene_snapshot.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <array>
#include <iostream>
#include <limits>
#include <functional>
#include <fstream>

int main() {
    using namespace mcvr::replay;
    try {
        auto require = [](bool ok) {
            if (!ok) throw std::runtime_error("assertion failed");
        };
        auto rejects = [&](std::function<void()> f) {
            bool failed = false;
            try {
                f();
            } catch (const std::runtime_error &) { failed = true; }
            require(failed);
        };
        const auto zipPath = std::filesystem::current_path() / "scene-replay-contract.zip";
        std::vector<uint8_t> blob(8192);
        for (size_t i = 0; i < blob.size(); ++i) blob[i] = uint8_t(i % 127);
        {
            SnapshotZip archive(zipPath);
            archive.add("buffer-0.bin", blob);
            archive.add("buffer-1.bin", std::span<const uint8_t>(blob).first(127));
            archive.close();
        }
        {
            SnapshotReader reader(zipPath);
            require(reader.read("buffer-1.bin", 127) == std::vector<uint8_t>(blob.begin(), blob.begin() + 127));
            require(reader.read("buffer-0.bin", blob.size()) == blob);
            rejects([&] { reader.read("buffer-*.bin", blob.size()); });
        }
        rejects([&] { readSnapshotBlob(zipPath, "missing.bin", blob.size()); });
        rejects([&] { readSnapshotBlob(zipPath, "buffer-0.bin", blob.size() - 1); });
        {
            std::fstream file(zipPath, std::ios::in | std::ios::out | std::ios::binary);
            std::vector<char> bytes((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
            const std::array<char, 4> signature{'P', 'K', 1, 2};
            const auto entry = std::search(bytes.begin(), bytes.end(), signature.begin(), signature.end());
            require(entry != bytes.end() && bytes.end() - entry > 20);
            const auto crcOffset = std::distance(bytes.begin(), entry) + 16;
            file.clear();
            file.seekp(crcOffset);
            file.put(char(bytes[size_t(crcOffset)] ^ 1));
        }
        rejects([&] { readSnapshotBlob(zipPath, "buffer-0.bin", blob.size()); });
        std::vector<AddressRange> ranges{{0x1000, 64, 0x9000}, {0x2000, 128, 0xb000}};
        struct Buffer {
            uint64_t bytes;
            uint64_t size() const {
                return bytes;
            }
        };
        auto live = std::make_shared<Buffer>(Buffer{256});
        auto retired = std::make_shared<Buffer>(Buffer{16});
        std::map<uint64_t, std::weak_ptr<Buffer>> owners{{0x1000, live}, {0x1040, retired}};
        retired.reset();
        const auto [base, retained] = resolveOwner(owners, 0x1080);
        require(base == 0x1000 && retained == live);
        rejects([&] { resolveOwner(owners, 0x1100); });
        RegistryPruneBudget prune;
        unsigned walks = 0;
        for (unsigned i = 0; i < 100000; ++i)
            if (prune.due(20000)) ++walks;
        require(walks == 100000 / 4096);
        require(relocate(ranges, 0x1020, 32) == 0x9020);
        require(relocate(ranges, 0x207f, 1) == 0xb07f);
        require(relocate(ranges, 0, 0) == 0);
        rejects([&] { relocate(ranges, 0x1040); });
        rejects([&] { relocate(ranges, 0x1030, 32); });
        rejects([&] { relocate(ranges, 0x3000); });
        rejects([&] { relocate({{1, 16, std::numeric_limits<uint64_t>::max() - 2}}, 5); });
        rejects([&] { relocate({{1, 16, std::numeric_limits<uint64_t>::max() - 2}}, 2, 4); });
        uint64_t bytes = 0;
        addBytes(bytes, captureByteLimit);
        rejects([&] { addBytes(bytes, 1); });
        require(bytes == captureByteLimit);
        require(captureBudget(nullptr) == captureByteLimit);
        require(captureBudget("4096") == 4ull * 1024 * 1024 * 1024);
        for (auto value : {"-1", "0", "8193", "4096MiB", "999999999999999999999"})
            rejects([&] { captureBudget(value); });
        bytes = captureByteLimit;
        addBytes(bytes, captureByteLimit, captureBudget("4096"));
        require(bytes == 2 * captureByteLimit);
        bytes = maximumCaptureBytes;
        rejects([&] { addBytes(bytes, 1, maximumCaptureBytes); });
        require(bytes == maximumCaptureBytes);
        const glm::dvec3 origin(30000000.5, 512, -30000000.5);
        ReplayCamera camera(origin, glm::mat4(1), glm::mat4(1));
        CameraInput input;
        input.forward = 1;
        camera.update(input, 0.1);
        require(glm::length(camera.position() - origin - glm::dvec3(0, 0, -2)) < 1e-8);
        const auto previousOffset = camera.offset();
        input.right = 1;
        camera.update(input, 0.1);
        require(std::abs(glm::length(camera.offset() - previousOffset) - 2.0) < 1e-8);
        // The exact production transform helper feeds both TLAS translation and
        // last-object-to-world history. Static global positions stay unchanged.
        const auto captured = glm::translate(glm::mat4(1), glm::vec3(10, -500, -50));
        const auto current = relativeTransform(captured, camera.offset());
        const auto previous = relativeTransform(captured, previousOffset);
        require(glm::length(glm::dvec3(current[3]) + camera.position() - origin - glm::dvec3(captured[3])) < 1e-5);
        require(glm::length(glm::dvec3(previous[3]) - glm::dvec3(current[3]) - (camera.offset() - previousOffset)) <
                1e-5);
        input = {};
        input.reset = true;
        camera.update(input, 0.1);
        require(camera.position() == origin && camera.view() == glm::mat4(1));
        input = {};
        input.lookX = 100;
        input.lookY = -120;
        camera.update(input, 0);
        require(camera.position() == origin);
        const auto rotated = camera.view();
        require(std::abs(glm::determinant(rotated) - 1.f) < 1e-5);
        input = {};
        input.forward = 1;
        camera.update(input, 0.1);
        const auto expectedDirection = glm::dvec3(glm::inverse(rotated) * glm::vec4(0, 0, -1, 0));
        require(glm::length(camera.offset() - 2.0 * expectedDirection) < 1e-5);
        // Vertical starting view from the real pressure capture has no Euler
        // singularity; view effects stay relative to the new rotation.
        const auto vertical = glm::rotate(glm::mat4(1), glm::radians(90.f), glm::vec3(1, 0, 0));
        const auto effect = glm::translate(glm::mat4(1), glm::vec3(0.1f, 0, 0));
        ReplayCamera verticalCamera(origin, vertical, effect * vertical);
        input = {};
        input.lookX = 240;
        input.lookY = -160;
        verticalCamera.update(input, 0.1);
        require(std::abs(glm::determinant(verticalCamera.view()) - 1.f) < 1e-5);
        require(verticalCamera.effectedView() == effect * verticalCamera.view());
        input = {};
        input.up = 1;
        input.fast = true;
        verticalCamera.update(input, 10);
        require(std::abs(verticalCamera.offset().y - 16) < 1e-8); // stall limited to 100 ms
        input = {};
        input.reset = true;
        verticalCamera.update(input, 0);
        require(verticalCamera.view() == vertical && verticalCamera.position() == origin);
        require(verticalCamera.effectedView() == effect * vertical);
        std::cout << "Scene replay relocation and readback budget checks passed\n";
        return 0;
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
