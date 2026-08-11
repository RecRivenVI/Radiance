#include "core/render/frame_retention.hpp"
#include "core/vulkan/sbt_hit_table.hpp"

#include <algorithm>
#include <array>
#include <iostream>
#include <random>
#include <string_view>

namespace {
size_t assertions = 0;
void require(bool condition, std::string_view message) {
    ++assertions;
    if (!condition) throw std::runtime_error(std::string(message));
}
template <class Error, class Action>
void rejects(Action &&action, std::string_view message) {
    bool rejected = false;
    try {
        action();
    } catch (const Error &) { rejected = true; }
    require(rejected, message);
}

struct Buffer {
    size_t serial;
    std::vector<uint8_t> bytes;
};
using Cache = mcvr::HitTableCache<Buffer>;

struct Recording {
    mcvr::FrameRetention<std::shared_ptr<Buffer>> inFlight;
    size_t creates = 0, records = 0;
    bool failPrepare = false, failRetain = false, failRecord = false;
    // One raygen, two misses, three hit groups, with nontrivial handle padding.
    std::vector<uint8_t> handles{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
    std::vector<std::shared_ptr<Buffer>> pendingRecording;

    Cache::Selection submit(Cache &cache, const std::vector<uint32_t> &indices) {
        return submitLayout(cache, std::make_shared<const std::vector<uint32_t>>(indices));
    }
    Cache::Selection submitLayout(Cache &cache, const std::shared_ptr<const std::vector<uint32_t>> &indices) {
        return cache.acquire(
            indices,
            [&] {
                if (failPrepare) throw std::bad_alloc();
                auto bytes = mcvr::packHitTable(*indices, handles, 3, 8, 3, 3);
                ++creates;
                return std::make_shared<Buffer>(Buffer{creates, std::move(bytes)});
            },
            [&](const std::shared_ptr<Buffer> &buffer) {
                require(!pendingRecording.empty() && pendingRecording.back() == buffer, "retain before recording");
                ++records;
                if (failRecord) throw std::runtime_error("injected record failure");
            },
            [&](const std::shared_ptr<Buffer> &buffer) {
                if (failRetain) throw std::bad_alloc();
                inFlight.retain(buffer);
                pendingRecording.push_back(buffer);
            });
    }
    void begin(uint64_t frame, uint64_t completed) {
        pendingRecording.clear(); // only the real frame-retention queue owns submitted versions now
        inFlight.beginFrame(frame, completed, 3);
    }
};

void packing() {
    std::mt19937 random(0x534254);
    for (size_t sample = 0; sample < 500; ++sample) {
        const size_t handleSize = 1 + random() % 64, stride = handleSize + random() % 64;
        const size_t firstHit = 1 + random() % 8, hitCount = 1 + random() % 32;
        std::vector<uint8_t> handles((firstHit + hitCount) * handleSize);
        for (auto &byte : handles) byte = static_cast<uint8_t>(random());
        std::vector<uint32_t> indices(1 + random() % 512);
        for (auto &index : indices) index = random() % hitCount;
        const auto packed = mcvr::packHitTable(indices, handles, handleSize, stride, firstHit, hitCount);
        require(packed.size() == indices.size() * stride, "exact record count and stride");
        for (size_t record = 0; record < indices.size(); ++record) {
            const auto begin = packed.begin() + record * stride;
            require(std::equal(begin, begin + handleSize, handles.begin() + (firstHit + indices[record]) * handleSize),
                    "each handle equals its actual pipeline group");
            require(std::all_of(begin + handleSize, begin + stride, [](auto byte) { return byte == 0; }),
                    "record padding remains zero");
        }
    }
    const std::array<uint8_t, 8> handles{};
    const std::array<uint32_t, 1> valid{0}, invalid{1};
    rejects<std::invalid_argument>([&] { mcvr::packHitTable({}, handles, 4, 8, 1, 1); }, "empty records");
    rejects<std::invalid_argument>([&] { mcvr::packHitTable(valid, handles, 0, 8, 1, 1); }, "zero handle size");
    rejects<std::invalid_argument>([&] { mcvr::packHitTable(valid, handles, 4, 3, 1, 1); }, "too-small stride");
    rejects<std::invalid_argument>([&] { mcvr::packHitTable(valid, handles, 3, 8, 1, 1); }, "incomplete palette");
    rejects<std::out_of_range>([&] { mcvr::packHitTable(valid, handles, 4, 8, 3, 1); }, "palette base overflow");
    rejects<std::out_of_range>([&] { mcvr::packHitTable(valid, handles, 4, 8, 1, 2); }, "palette count overflow");
    rejects<std::out_of_range>([&] { mcvr::packHitTable(invalid, handles, 4, 8, 1, 1); }, "hit index overflow");
    rejects<std::length_error>([] { mcvr::hitTableBytes(std::numeric_limits<size_t>::max(), 2); }, "byte overflow");
    rejects<std::invalid_argument>([] { mcvr::hitTableBytes(1, 0); }, "zero stride");
}

void versionsAndRetirement() {
    Recording recording;
    Cache cache;
    const std::vector<uint32_t> a{2, 0, 1, 2}, b{2, 1, 0, 2};
    recording.begin(1, 0);
    std::weak_ptr<Buffer> firstA, firstB;
    std::vector<uint8_t> firstBytes;
    {
        auto first = recording.submit(cache, a);
        firstA = first.buffer;
        firstBytes = first.buffer->bytes;
        require(first.uploaded && recording.records == 1, "first use uploads once");
        auto reused = recording.submit(cache, a);
        require(!reused.uploaded && reused.buffer == first.buffer && recording.creates == 1,
                "exact hit skips all work");
        auto second = recording.submit(cache, b);
        firstB = second.buffer;
        require(second.uploaded && second.buffer != first.buffer,
                "same byte count with reordered groups is a new version");
        require(first.buffer->bytes == firstBytes && second.buffer->bytes != firstBytes,
                "old recorded table is immutable");
        auto third = recording.submit(cache, a);
        require(third.uploaded && third.buffer != first.buffer && third.buffer != second.buffer,
                "A/B/A in one frame never overwrites a recorded version or grows a historical cache");
        require(third.buffer->bytes == firstBytes, "returning to A preserves exact bytes");
        auto shorter = recording.submit(cache, {2, 0});
        require(shorter.uploaded && shorter.buffer->bytes.size() == 16, "size change produces the exact new region");
    }
    recording.begin(2, 0);
    auto reused = recording.submit(cache, {2, 0});
    require(!reused.uploaded && recording.records == 4, "cross-frame unchanged content skips upload");
    recording.begin(100, 0);
    require(!firstA.expired() && !firstB.expired(), "many CPU frames cannot retire incomplete GPU work");
    recording.begin(101, 1);
    require(firstA.expired() && firstB.expired(), "completed frame retires replaced versions");

    // New SBT/pipeline, pass and frame-slot objects must not share a cache even
    // with identical numeric group IDs. This also models shader-pack rebuilding.
    Cache otherPipeline, otherPass, otherFrame;
    auto oldBytes = reused.buffer->bytes;
    for (auto &byte : recording.handles) byte ^= 0xFF;
    for (auto *other : {&otherPipeline, &otherPass, &otherFrame}) {
        auto fresh = recording.submit(*other, {2, 0});
        require(fresh.uploaded && fresh.buffer != reused.buffer && fresh.buffer->bytes != oldBytes,
                "pipeline/pass/frame cache identity is isolated");
    }
    std::weak_ptr<Buffer> last;
    {
        Cache reloaded;
        auto fresh = recording.submit(reloaded, a);
        last = fresh.buffer;
    }
    recording.begin(102, 1);
    require(!last.expired(), "destroying or reloading the SBT cannot destroy in-flight buffers");
    recording.begin(105, 101);
    require(last.expired(), "reload's old buffer retires after actual completion");
}

void failures() {
    Recording recording;
    Cache cache;
    recording.begin(1, 0);
    const std::vector<uint32_t> a{0, 1}, b{1, 0};
    auto first = recording.submit(cache, a);
    auto shared = std::make_shared<const std::vector<uint32_t>>(a);
    auto sharedFirst = recording.submitLayout(cache, shared);
    for (size_t i = 0; i < 100; ++i) {
        const auto selected = recording.submitLayout(cache, shared);
        require(!selected.uploaded && selected.buffer == sharedFirst.buffer,
                "accepted immutable layout reuses its buffer across consumers");
    }
    rejects<std::invalid_argument>([&] { recording.submitLayout(cache, nullptr); }, "null layout is rejected");
    for (auto failure : {&recording.failPrepare, &recording.failRetain, &recording.failRecord}) {
        *failure = true;
        rejects<std::exception>([&] { recording.submit(cache, b); }, "failure propagates");
        *failure = false;
        auto old = recording.submit(cache, a);
        require(!old.uploaded && old.buffer == first.buffer, "failed candidate does not poison the last valid cache");
    }
    rejects<std::out_of_range>([&] { recording.submit(cache, {0, 3}); },
                               "invalid group never records an out-of-bounds read");
    rejects<std::invalid_argument>([&] { recording.submit(cache, {}); }, "empty input remains rejected");
    auto old = recording.submit(cache, a);
    require(!old.uploaded && old.buffer == first.buffer, "bad input preserves the old version");
    recording.failRetain = true;
    const auto records = recording.records;
    rejects<std::bad_alloc>([&] { recording.submit(cache, a); }, "cache-hit retention failure propagates");
    require(records == recording.records, "retention failure cannot record work");
    recording.failRetain = false;
    Cache empty;
    rejects<std::logic_error>(
        [&] { empty.acquire(shared, [] { return std::shared_ptr<Buffer>{}; }, [](auto) {}, [](auto) {}); },
        "null preparation fails closed");
}
} // namespace

int main() {
    packing();
    versionsAndRetirement();
    failures();
    std::cout << "SBT packing/cache/retirement assertions: " << assertions << '\n';
}
