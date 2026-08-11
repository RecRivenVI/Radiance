#include "core/render/persistent_write_batch.hpp"
#include <array>
#include <cstring>
#include <memory>
#include <vector>
#include <iostream>

namespace {
void check(bool value) {
    if (!value) throw std::runtime_error("persistent write batch contract");
}
struct Buffer {
    std::vector<uint8_t> bytes;
    uint32_t usage = 7;
    explicit Buffer(uint32_t n, uint32_t flags = 7) : bytes(n), usage(flags) {}
    size_t size() const {
        return bytes.size();
    }
    uint32_t usageFlags() const {
        return usage;
    }
    void *mappedPtr() {
        return bytes.data();
    }
    void uploadToStagingBuffer(const void *data, uint32_t size, uint32_t offset) {
        if (offset > bytes.size() || size > bytes.size() - offset) throw std::out_of_range("fixture bounds");
        std::memcpy(bytes.data() + offset, data, size);
    }
};
struct Record {
    std::shared_ptr<Buffer> buffer;
    uint32_t size = 8, usage = 7;
    bool uploadReady = true;
    mcvr::render::PersistentWriteBatch::Ticket writeBatch = 0;
};
} // namespace
int main() {
    {
        std::vector<std::shared_ptr<Buffer>> pool;
        int allocations = 0;
        auto create = [&](uint32_t n, uint32_t usage) {
            ++allocations;
            return std::make_shared<Buffer>(n, usage);
        };
        auto first = create(8, 7);
        first->bytes[0] = 13;
        auto gpuOwner = first;
        mcvr::render::cachePersistentVersion(pool, first);
        first.reset();
        auto second = mcvr::render::acquirePersistentVersion(pool, 8, 7, create);
        check(allocations == 2 && second != gpuOwner && gpuOwner->bytes[0] == 13);
        auto *retired = gpuOwner.get();
        gpuOwner.reset(); // fence completion: only the pool owns the old allocation
        auto reused = mcvr::render::acquirePersistentVersion(pool, 8, 7, create);
        check(reused.get() == retired && allocations == 2 && pool.empty());
        mcvr::render::cachePersistentVersion(pool, reused);
        reused.reset();
        auto resized = mcvr::render::acquirePersistentVersion(pool, 16, 7, create);
        auto otherUsage = mcvr::render::acquirePersistentVersion(pool, 8, 8, create);
        check(allocations == 4 && resized->size() == 16 && otherUsage->usageFlags() == 8);
        bool allocationFailed = false;
        try {
            mcvr::render::acquirePersistentVersion(pool, 32, 7, [](uint32_t, uint32_t) -> std::shared_ptr<Buffer> {
                throw std::runtime_error("allocation failure");
            });
        } catch (const std::runtime_error &) { allocationFailed = true; }
        check(allocationFailed && pool.size() == 1 && pool.front()->bytes[0] == 13);
        for (int i = 0; i < 20; ++i) mcvr::render::cachePersistentVersion(pool, create(8, 7));
        check(pool.size() == 4);
    }
    using mcvr::render::PersistentWriteBatch;
    PersistentWriteBatch batch;
    Record a{std::make_shared<Buffer>(8)}, b{std::make_shared<Buffer>(8)};
    std::vector<std::shared_ptr<Buffer>> pending, retained;
    int allocations = 0, rolloverVisits = 0;
    bool rejectAllocation = false;
    auto create = [&](uint32_t n, uint32_t usage) {
        check(usage == 7);
        if (rejectAllocation) throw std::runtime_error("injected allocation failure");
        ++allocations;
        return std::make_shared<Buffer>(n);
    };
    auto retire = [&](const auto &old) { retained.push_back(old); };
    auto write = [&](Record &record, uint8_t *src, uint32_t n, uint32_t offset) {
        mcvr::render::stagePersistentRange(record, batch, pending, src, n, offset, create, retire);
    };
    auto flush = [&] {
        // Pending and frame-retained buffers are distinct from CPU write ownership.
        retained.insert(retained.end(), pending.begin(), pending.end());
        pending.clear();
        batch.close([&] {
            ++rolloverVisits;
            a.writeBatch = b.writeBatch = 0;
        });
    };
    std::array<uint8_t, 8> full{1, 2, 3, 4, 5, 6, 7, 8};
    a.buffer->uploadToStagingBuffer(full.data(), 8, 0);
    pending.push_back(a.buffer);
    batch.mark(a.writeBatch); // full-upload handoff
    auto first = a.buffer;
    uint8_t x = 40, y = 60;
    write(a, &x, 1, 3);
    write(a, &y, 1, 5);
    check(allocations == 0 && pending.size() == 1 && a.buffer == first);
    check(first->bytes == std::vector<uint8_t>({1, 2, 3, 40, 5, 60, 7, 8}));
    flush();
    write(a, &x, 1, 0);
    auto second = a.buffer;
    check(allocations == 1 && second != first && first->bytes[0] == 1 && second->bytes[0] == 40);
    write(a, &y, 1, 1);
    check(allocations == 1 && pending.size() == 1 && a.buffer == second);
    write(b, &x, 1, 2);
    check(allocations == 2 && pending.size() == 2 && b.buffer->bytes[2] == 40);
    flush();
    flush(); // repeated flush, including empty flush in one frame
    check(rolloverVisits == 0 && !batch.open(a.writeBatch) && !batch.open(b.writeBatch));
    rejectAllocation = true;
    bool failed = false;
    try {
        write(a, &y, 1, 0);
    } catch (const std::runtime_error &) { failed = true; }
    check(failed && a.buffer == second && pending.empty() && !batch.open(a.writeBatch));
    rejectAllocation = false;
    write(a, &y, 1, 0);
    check(a.buffer != second && second->bytes[0] == 40 && a.buffer->bytes[0] == 60);
    const int before = allocations;
    write(a, nullptr, 0, 8);
    check(allocations == before);
    failed = false;
    try {
        write(a, &x, 1, 8);
    } catch (const std::out_of_range &) { failed = true; }
    check(failed);
    failed = false;
    try {
        write(a, nullptr, 1, 0);
    } catch (const std::invalid_argument &) { failed = true; }
    check(failed);
    // Release/recreate an owner before its queued upload retires. Fresh state cannot
    // inherit openness, and the already queued/retained buffer remains alive.
    std::weak_ptr<Buffer> released = a.buffer;
    a = Record{std::make_shared<Buffer>(8)};
    check(!released.expired() && !batch.open(a.writeBatch));
    write(a, &x, 1, 7);
    check(a.buffer->bytes[0] == 0 && a.buffer->bytes[7] == 40);
    flush();
    check(!released.expired());
    retained.clear();
    check(released.expired());
    // Closing scales with batches, not idle object count; callback must stay untouched.
    for (int i = 0; i < 100000; ++i) batch.close([&] { ++rolloverVisits; });
    check(rolloverVisits == 0);
    PersistentWriteBatch wrap(std::numeric_limits<uint32_t>::max());
    a.writeBatch = 1;
    wrap.mark(b.writeBatch);
    wrap.close([&] {
        ++rolloverVisits;
        a.writeBatch = b.writeBatch = 0;
    });
    check(rolloverVisits == 1 && !wrap.open(a.writeBatch) && !wrap.open(b.writeBatch));
    wrap.mark(a.writeBatch);
    check(wrap.open(a.writeBatch));
    wrap.close(
        [&] {
            ++rolloverVisits;
            a.writeBatch = b.writeBatch = 0;
        },
        true);
    check(rolloverVisits == 2 && !wrap.open(a.writeBatch) && !wrap.open(b.writeBatch));
    wrap.mark(b.writeBatch);
    wrap.close([&] { ++rolloverVisits; }, false);
    check(rolloverVisits == 2 && !wrap.open(b.writeBatch));
    std::cout
        << "Persistent range staging: full/range merge, multi-flush, copy-on-write, unchanged old owners, failures, release/recreate, zero-size/bounds and batch rollover PASS\n";
}
