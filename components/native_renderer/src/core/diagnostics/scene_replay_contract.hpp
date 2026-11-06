#pragma once

#include <algorithm>
#include <cstdint>
#include <limits>
#include <stdexcept>
#include <vector>
#include <map>
#include <memory>
#include <string>

namespace mcvr::replay {
struct RegistryPruneBudget {
    uint32_t calls = 0;
    bool due(size_t liveAndExpiredEntries) {
        if (++calls < 4096) return false;
        calls = 0;
        return liveAndExpiredEntries > 8192;
    }
};
// Retired Vulkan address ranges can overlap a later, larger allocation. An
// expired nearer key must not hide the live buffer containing an interior BDA.
template <class Buffer>
std::pair<uint64_t, std::shared_ptr<Buffer>> resolveOwner(const std::map<uint64_t, std::weak_ptr<Buffer>> &owners,
                                                          uint64_t address) {
    auto it = owners.upper_bound(address);
    while (it != owners.begin()) {
        --it;
        auto owner = it->second.lock();
        if (owner && address - it->first < owner->size()) return {it->first, std::move(owner)};
    }
    throw std::runtime_error("No live scene buffer contains address " + std::to_string(address));
}
// Snapshot addresses are keys only. Never submit them to a different Vulkan device.
struct AddressRange {
    uint64_t oldBase, size, newBase;
};
// Captured buffer ranges are disjoint and sorted by oldBase before relocation.
inline uint64_t relocate(const std::vector<AddressRange> &ranges, uint64_t address, uint64_t bytes = 1) {
    if (address == 0 && bytes == 0) return 0;
    const auto next = std::ranges::upper_bound(ranges, address, {}, &AddressRange::oldBase);
    if (next != ranges.begin()) {
        const auto &r = *(next - 1);
        const auto offset = address - r.oldBase;
        if (offset <= r.size && bytes <= r.size - offset &&
            offset <= std::numeric_limits<uint64_t>::max() - r.newBase &&
            bytes <= std::numeric_limits<uint64_t>::max() - r.newBase - offset)
            return r.newBase + offset;
    }
    throw std::runtime_error("Scene snapshot contains an unresolved/out-of-range device address");
}
inline constexpr uint64_t captureByteLimit = 512ull * 1024 * 1024;
inline constexpr uint64_t maximumCaptureBytes = 8ull * 1024 * 1024 * 1024;
inline uint64_t captureBudget(const char *mib) {
    if (!mib || !*mib) return captureByteLimit;
    uint64_t value = 0;
    for (const char *p = mib; *p; ++p) {
        if (*p < '0' || *p > '9' || value > 8192)
            throw std::runtime_error("Invalid RADIANCE_SCENE_CAPTURE_MAX_MIB (64..8192)");
        value = value * 10 + (*p - '0');
    }
    if (value < 64 || value > 8192) throw std::runtime_error("Invalid RADIANCE_SCENE_CAPTURE_MAX_MIB (64..8192)");
    return value * 1024 * 1024;
}
inline void addBytes(uint64_t &total, uint64_t bytes, uint64_t limit = captureByteLimit) {
    if (limit > maximumCaptureBytes || total > limit || bytes > limit - total)
        throw std::runtime_error("Scene snapshot exceeds readback budget (bytes=" + std::to_string(limit) + ")");
    total += bytes;
}
} // namespace mcvr::replay
