#include "core/render/chunk_scheduling.hpp"
#include <map>
#include <set>
#include <stdexcept>
#include <vector>
using namespace mcvr::chunkScheduling;
using namespace std::chrono_literals;
void require(bool b) {
    if (!b) throw std::runtime_error("chunk scheduling regression");
}
int main() {
    auto now = Clock::now();
    Key loading{0, now - 1ms, 0}, interaction{1, now, 999}, aged{0, now - 251ms, 99999};
    require(before(interaction, loading, now));
    require(!before(aged, interaction, now));
    require(before(loading, aged, now));
    auto below = positionKey(0, now, {56, 8, 8}, {62, 153, 7});
    auto far = positionKey(0, now, {184, 152, 8}, {62, 153, 7});
    require(before(far, below, now));
    require(positionKey(0, now, {56, 152, 8}, {62, 153, 7}).distance == 0);
    require(positionKey(0, now, {-8, 72, -24}, {-1, 75, -20}).distance == 0);
    require(ready(true, 1, 128, 0ms));
    require(!ready(false, 1, 128, 24ms));
    require(ready(false, 1, 128, 25ms));
    require(ready(false, 128, 128, 0ms));
    require(withinBudget(0, 0, 500ms));
    require(!withinBudget(1, uploadBudget, 0ms));
    require(!withinBudget(1, 0, preparationBudget));
    // Large geometry is admitted once, then no additional work is admitted in that round.
    require(!withinBudget(1, uploadBudget * 2, 0ms));
    UploadPacer pacer(now);
    require(remainingSections(0, 128) == 64);
    require(remainingSections(63, 128) == 1);
    require(remainingSections(64, 128) == 0);
    require(remainingSections(65, 4) == 0);
    require(remainingSections(32, 4) == 4);
    require(pacer.available(now) == uploadBudget);
    pacer.consumed(uploadBudget, now);
    require(pacer.available(now) == 0);
    require(pacer.available(now + 8ms) >= uploadBudget / 2 - 1);
    require(pacer.available(now + 17ms) == uploadBudget);
    pacer.consumed(uploadBudget * 2, now + 17ms);
    require(pacer.available(now + 25ms) == 0);
    require(pacer.available(now + 50ms) == uploadBudget);
    for (auto frame : {8ms, 16ms, 50ms, 100ms}) {
        require(ready(true, 1, 128, frame));
        if (frame >= 25ms) require(ready(false, 1, 128, frame));
    }
    std::set<int64_t> queue;
    std::map<int64_t, Key> keys;
    for (int64_t i = 0; i < 1000; ++i) {
        queue.insert(i);
        keys.emplace(i, Key{0, now, double(i)});
    }
    queue.insert(5000);
    keys.emplace(5000, Key{1, now, 1e9});
    auto key = [&](int64_t id) { return keys.at(id); };
    auto bytes = [](int64_t) { return 1024ull; };
    auto batch = selectBatch(queue, 4, now, key, bytes, 0);
    require(batch == std::vector<int64_t>({5000, 0, 1, 2}));
    // An early-ready distant spawn column cannot use an aged slot ahead of nearby loading.
    keys[999].queued = now - 251ms;
    batch = selectBatch(queue, 4, now, key, bytes, 0);
    require(batch == std::vector<int64_t>({5000, 0, 1, 2}));
    queue.erase(5000);
    require(selectBatch(queue, 4, now, key, bytes, 0) == std::vector<int64_t>({0, 1, 2, 3}));
    keys[0].valid = false;
    require(selectBatch(queue, 4, now, key, bytes, 0) == std::vector<int64_t>({1, 2, 3, 4}));
    keys[0].valid = true;
    queue.insert(5000);
    require(queue.size() == 1001); // selection does not silently drop deferred owners
    require(selectBatch(queue, 4, now, key, bytes, inFlightInputBudget).empty());
    require(selectBatch(queue, 4, now, key, bytes, inFlightInputBudget - 1024).size() == 1);
    auto huge = [](int64_t) { return inFlightInputBudget + 1; };
    require(selectBatch(queue, 4, now, key, huge, 0).size() == 1);
    require(selectBatch(queue, 4, now, key, huge, 1).empty());
    auto limited = selectBatch(queue, 4, now, key, bytes, 0, 0, 2048);
    require(limited.size() == 2 && queue.size() == 1001);
    require(selectBatch(queue, 4, now, key, bytes, 0, 0, 512).empty());
    keys[999].queued = now - 2s;
    auto oneHuge = [&](int64_t id) { return id == 999 ? inFlightInputBudget + 1 : 1024ull; };
    auto nearby = selectBatch(queue, 4, now, key, oneHuge, 1);
    require(!nearby.empty() && std::find(nearby.begin(), nearby.end(), 999) == nearby.end());
    auto oversizedOnly = std::set<int64_t>{999};
    require(selectBatch(oversizedOnly, 4, now, key, oneHuge, 1).empty());
    require(selectBatch(oversizedOnly, 4, now, key, oneHuge, 0) == std::vector<int64_t>{999});
    keys[999].queued = now - 251ms;
    // A thousand old background sections cannot bury a new interaction.
    for (auto &[id, k] : keys)
        if (id != 5000) k.queued = now - 1s;
    for (uint64_t offset = 0; offset < 4; ++offset) {
        batch = selectBatch(queue, 4, now, key, bytes, 0, offset);
        require(std::find(batch.begin(), batch.end(), 5000) != batch.end());
    }
    // Continuous fresh interaction, one-section batches: each lane makes bounded progress.
    uint64_t serial = 0;
    size_t backgroundDrained = 0, interactionsDrained = 0;
    queue.erase(5000);
    for (int n = 0; n < 400; ++n) {
        int64_t id = 10000 + n;
        queue.insert(id);
        keys[id] = Key{1, now, 0};
        auto selected = selectBatch(queue, 1, now, key, bytes, 0, serial);
        require(selected.size() == 1);
        serial += selected.size();
        for (auto chosen : selected) {
            if (keys[chosen].priority == 0)
                ++backgroundDrained;
            else
                ++interactionsDrained;
            require(queue.erase(chosen) == 1);
        }
    }
    require(backgroundDrained == 100);
    require(interactionsDrained == 300);
    // The production queue retains every deferred owner and drains after new input stops.
    size_t remaining = queue.size();
    while (!queue.empty()) {
        auto selected = selectBatch(queue, 4, now, key, bytes, 0, serial);
        require(!selected.empty());
        serial += selected.size();
        for (auto id : selected) require(queue.erase(id) == 1);
        remaining -= selected.size();
    }
    require(remaining == 0);

    // The world and independent spaces rotate within the same band; each source stays nearest-first.
    queue = {1, 2, 3, 4, 5};
    keys = {{1, {0, now, .01, 7}},
            {2, {0, now, .02, 7}},
            {3, {0, now, .9, 0}},
            {4, {0, now - 10s, 180, 4}},
            {5, {1, now, 1e9, 7}}};
    uint64_t cursor = UINT64_MAX;
    auto selected = selectBatch(queue, 4, now, key, bytes, 0, 0, uploadBudget, &cursor);
    require(selected == std::vector<int64_t>({5, 3, 1, 2}));
    queue.erase(3);
    keys[3] = {0, now, .8, 0};
    queue.insert(3);
    require(selectBatch(queue, 1, now, key, bytes, 0, 3, uploadBudget, &cursor) == std::vector<int64_t>{3});
    keys[4] = positionKey(0, now, {-8, 72, -24}, {-1, 75, -20}, {8, 8, 8}, 4);
    require(keys[4].distance == 0);
    keys[1].valid = keys[2].valid = keys[3].valid = false;
    require(selectBatch(queue, 1, now, key, bytes, 0, 3) == std::vector<int64_t>{4});
    keys[1].valid = true;
    auto uneven = [](int64_t id) { return id == 1 ? 2048ull : 1024ull; };
    require(selectBatch(queue, 1, now, key, uneven, inFlightInputBudget - 1024, 3) == std::vector<int64_t>{4});
    require(positionKey(0, now, {0, 0, 0}, {12, 0, 12}, {16, 8, 16}).distance == 0);
}
