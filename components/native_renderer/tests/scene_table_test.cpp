#include "core/render/frame_retention.hpp"
#include "core/render/scene_table.hpp"
#include <algorithm>
#include <array>
#include <iostream>
#include <random>
#include <string_view>

namespace {
size_t checks = 0;
void require(bool value, std::string_view message) {
    ++checks;
    if (!value) throw std::runtime_error(std::string(message));
}
template <class Action>
void rejects(Action action) {
    bool caught = false;
    try {
        action();
    } catch (const std::exception &) { caught = true; }
    require(caught, "invalid input/failure must propagate");
}
struct Buffer {
    std::vector<std::byte> bytes;
};
struct Recording {
    mcvr::FrameRetention<std::shared_ptr<Buffer>> frames;
    std::vector<std::shared_ptr<Buffer>> recorded;
    std::weak_ptr<Buffer> lastRetained;
    size_t preparations = 0;
    bool failPrepare = false, failRetain = false, failRecord = false, nullPrepare = false;
    mcvr::SceneTableCache<Buffer>::Selection submit(mcvr::SceneTableCache<Buffer> &cache,
                                                    const mcvr::SceneTable &data) {
        return cache.acquire(
            data,
            [&](const auto &input) {
                if (failPrepare) throw std::bad_alloc();
                ++preparations;
                if (nullPrepare) return std::shared_ptr<Buffer>{};
                auto result = std::make_shared<Buffer>();
                result->bytes.resize(input.size());
                input.write(result->bytes);
                return result;
            },
            [&](const auto &buffer) {
                require(lastRetained.lock() == buffer, "retain this exact version before recording");
                recorded.push_back(buffer);
                if (failRecord) throw std::runtime_error("record failed");
            },
            [&](const auto &buffer) {
                if (failRetain) throw std::bad_alloc();
                frames.retain(buffer);
                lastRetained = buffer;
            });
    }
};
} // namespace

int main() {
    try {
        std::mt19937 random(0x5c3e);
        for (size_t sample = 0; sample < 1000; ++sample) {
            auto stable = std::make_shared<std::vector<uint64_t>>(random() % 35);
            std::vector<uint64_t> changing(random() % 23);
            for (auto &v : changing) v = random();
            for (auto &v : *stable) v = random();
            const size_t split = random() % (changing.size() + 1);
            auto data = mcvr::sceneTable<uint64_t>(changing, split, *stable, stable);
            auto reference = changing;
            reference.insert(reference.begin() + split, stable->begin(), stable->end());
            const auto bytes = std::as_bytes(std::span(reference));
            std::vector<std::byte> packed(data.size());
            data.write(packed);
            require(std::equal(packed.begin(), packed.end(), bytes.begin(), bytes.end()), "exact ordered bytes");
            require(data.equals(bytes), "observer compares final upload");
            if (data.size())
                rejects([&] { data.write(std::span<std::byte>{}); });
            else
                data.write({});
            if (!packed.empty()) {
                packed[random() % packed.size()] ^= std::byte{1};
                require(!data.equals(packed), "same-size content edit detected");
            }
            rejects([&] { mcvr::sceneTable<uint64_t>(changing, changing.size() + 1); });
        }

        auto stable = std::make_shared<const std::vector<uint64_t>>(std::initializer_list<uint64_t>{10, 20, 30});
        std::weak_ptr<const std::vector<uint64_t>> weakOwner = stable;
        std::vector<uint64_t> dynamic{1, 2, 3, 4};
        auto data = mcvr::sceneTable<uint64_t>(dynamic, 2, *stable, stable);
        mcvr::SceneTableCache<Buffer> cache, anotherView;
        Recording recording;
        recording.frames.beginFrame(1, 0, 2);
        auto first = recording.submit(cache, data);
        require(first.uploaded, "cold creates GPU version");
        const auto originalBytes = first.buffer->bytes;
        require(!recording.submit(cache, data).uploaded && recording.preparations == 1, "unchanged skips all packing");
        require(recording.submit(anotherView, data).buffer != first.buffer, "independent view cache");
        dynamic[0] = 9;
        auto next = recording.submit(cache, data);
        require(next.uploaded && first.buffer != next.buffer && first.buffer->bytes == originalBytes,
                "same-frame new content never overwrites recorded buffer");
        std::swap(dynamic[0], dynamic[1]);
        require(recording.submit(cache, data).uploaded, "same-size prefix reorder invalidates");
        dynamic.back() = 77;
        require(recording.submit(cache, data).uploaded, "external suffix edit invalidates");

        for (int failure = 0; failure < 4; ++failure) {
            auto before = recording.submit(cache, data).buffer;
            dynamic[0] += 1;
            recording.failPrepare = failure == 0;
            recording.failRetain = failure == 1;
            recording.failRecord = failure == 2;
            recording.nullPrepare = failure == 3;
            rejects([&] { recording.submit(cache, data); });
            recording.failPrepare = recording.failRetain = recording.failRecord = recording.nullPrepare = false;
            dynamic[0] -= 1;
            require(recording.submit(cache, data).buffer == before, "failed transaction preserves previous content");
        }
        // Same owner, different borrowed range must not be considered identical.
        auto range = mcvr::sceneTable<uint64_t>(dynamic, 2, std::span(*stable).subspan(1), stable);
        require(recording.submit(cache, range).uploaded, "owner identity alone is not table identity");
        auto sameBytesNewOwner = std::make_shared<const std::vector<uint64_t>>(*stable);
        require(recording.submit(cache, mcvr::sceneTable<uint64_t>(dynamic, 2, *sameBytesNewOwner, sameBytesNewOwner))
                    .uploaded,
                "new publication retires old owner even with identical bytes");
        require(recording.submit(cache, mcvr::sceneTable<uint64_t>(dynamic, 1, *sameBytesNewOwner, sameBytesNewOwner))
                    .uploaded,
                "changing dynamic insertion boundary invalidates");
        auto replacement = std::make_shared<const std::vector<uint64_t>>(std::initializer_list<uint64_t>{10, 20, 31});
        require(recording.submit(cache, mcvr::sceneTable<uint64_t>(dynamic, 2, *replacement, replacement)).uploaded,
                "resource reload/replacement contents invalidate");
        require(recording.submit(cache, mcvr::sceneTable<uint64_t>(dynamic)).uploaded, "uncached-scene fallback");
        rejects([&] { mcvr::sceneTable<uint64_t>(dynamic, 0, *stable); });
        rejects([&] { recording.submit(cache, {}); });

        std::weak_ptr<Buffer> pending = first.buffer;
        first.buffer.reset();
        next.buffer.reset();
        recording.recorded.clear();
        cache.reset();
        anotherView.reset();
        require(!pending.expired(), "GPU-in-flight ownership independent of caches");
        recording.frames.beginFrame(2, 0, 2);
        require(!pending.expired(), "frame age alone cannot retire GPU resource");
        recording.frames.beginFrame(3, 1, 2);
        require(pending.expired(), "completion and frame age retire old version");
        data = {};
        range = {};
        stable.reset();
        require(weakOwner.expired(), "world leave drops immutable snapshot owner");
        require(recording.submit(cache, mcvr::sceneTable<uint64_t>(dynamic)).uploaded, "reset starts fresh identity");
        auto bypass = [&](const mcvr::SceneTable &input) {
            return cache.acquire(
                input,
                [&](const auto &v) {
                    auto b = std::make_shared<Buffer>();
                    b->bytes.resize(v.size());
                    v.write(b->bytes);
                    return b;
                },
                [&](const auto &b) { require(b->bytes.size() == input.size(), "bypass records complete table"); },
                [&](const auto &b) { recording.frames.retain(b); }, false);
        };
        auto bypassFirst = bypass(mcvr::sceneTable<uint64_t>(dynamic));
        auto bypassSecond = bypass(mcvr::sceneTable<uint64_t>(dynamic));
        require(bypassFirst.uploaded && bypassSecond.uploaded && bypassFirst.buffer != bypassSecond.buffer,
                "changing-table policy creates independent versions without comparison copies");
        require(recording.submit(cache, mcvr::sceneTable<uint64_t>(dynamic)).uploaded,
                "bypass clears stale reusable key");
        std::cout << checks << " scene-table checks passed\n";
        return 0;
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
