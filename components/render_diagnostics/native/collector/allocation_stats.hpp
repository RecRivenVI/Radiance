#pragma once

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

namespace mcvr::diag {
inline std::atomic<uint64_t> dropped{0};

// Optional allocation diagnostics: attributes VMA buffer/image creations to the middleware
// call (JNI operation) that triggered them, tracks per-source live bytes and reports
// per-second deltas so leaked allocation sources become visible.

struct AllocTagStats {
    uint64_t liveCount = 0;
    uint64_t liveBytes = 0;
    uint64_t createdCount = 0;
    uint64_t createdBytes = 0;
};

inline std::mutex &allocTraceMutex() {
    static auto *mutex = new std::mutex;
    return *mutex;
}

inline thread_local const char *allocTraceTag = nullptr;

inline std::unordered_map<std::string, AllocTagStats> &allocTraceStats() {
    static auto *stats = new std::unordered_map<std::string, AllocTagStats>;
    return *stats;
}

inline std::unordered_map<std::string, AllocTagStats> &allocTracePreviousStats() {
    static auto *stats = new std::unordered_map<std::string, AllocTagStats>;
    return *stats;
}

inline void setAllocTraceTag(const char *tag) {
    allocTraceTag = tag;
}

inline const char *currentAllocTraceTag() {
    return allocTraceTag == nullptr ? "internal" : allocTraceTag;
}

struct AllocTraceTagScope {
    const char *previous;
    explicit AllocTraceTagScope(const char *tag) : previous(allocTraceTag) {
        allocTraceTag = tag;
    }
    ~AllocTraceTagScope() {
        allocTraceTag = previous;
    }
};

// Returns the captured source tag; store it next to the resource so destruction can be
// attributed to the same source even when the current tag has changed.
inline const char *recordAllocCreate(const char *kind, uint64_t bytes) noexcept {
    const char *tag = currentAllocTraceTag();
    try {
        std::lock_guard<std::mutex> lock(allocTraceMutex());
        auto key = std::string(kind) + '@' + tag;
        if (allocTraceStats().size() >= 512 && !allocTraceStats().contains(key)) { ++dropped; return nullptr; }
        auto &stats = allocTraceStats()[key];
        stats.liveCount++;
        stats.liveBytes += bytes;
        stats.createdCount++;
        stats.createdBytes += bytes;
    } catch (...) { ++dropped;
    }
    return tag;
}

inline void recordAllocDestroy(const char *kind, uint64_t bytes, const char *tag) noexcept {
    try {
        std::lock_guard<std::mutex> lock(allocTraceMutex());
        auto found = allocTraceStats().find(std::string(kind) + '@' + (tag == nullptr ? "internal" : tag));
        if (found == allocTraceStats().end()) return;
        auto &stats = found->second;
        if (stats.liveCount > 0) stats.liveCount--;
        if (stats.liveBytes >= bytes) stats.liveBytes -= bytes;
    } catch (...) { ++dropped;
    }
}

inline double allocTraceMegabytes(uint64_t bytes) {
    return static_cast<double>(bytes) / (1024.0 * 1024.0);
}

inline std::string allocTraceSummaryPerSecond() {
    std::lock_guard<std::mutex> lock(allocTraceMutex());

    auto &stats = allocTraceStats();
    auto &previous = allocTracePreviousStats();
    const bool first = previous.empty();

    struct Row {
        std::string kind;
        std::string tag;
        int64_t liveDelta = 0;
        uint64_t createdDelta = 0;
        uint64_t createdCount = 0;
        uint64_t liveBytes = 0;
    };
    std::vector<Row> rows;
    rows.reserve(stats.size());
    for (const auto &[key, value] : stats) {
        AllocTagStats before{};
        if (auto found = previous.find(key); found != previous.end()) before = found->second;
        Row row;
        const auto at = key.find('@');
        row.kind = key.substr(0, at);
        row.tag = key.substr(at + 1);
        row.liveDelta = static_cast<int64_t>(value.liveBytes) - static_cast<int64_t>(before.liveBytes);
        row.createdDelta = value.createdBytes - before.createdBytes;
        row.createdCount = value.createdCount - before.createdCount;
        row.liveBytes = value.liveBytes;
        rows.push_back(std::move(row));
        previous[key] = value;
    }

    // Aggregate live bytes per kind for the compact live= summary.
    std::unordered_map<std::string, std::pair<uint64_t, int64_t>> kindTotals;
    for (const auto &row : rows) {
        auto &total = kindTotals[row.kind];
        total.first += row.liveBytes;
        total.second += row.liveDelta;
    }

    std::ostringstream out;
    out.setf(std::ios::fixed);
    out.precision(1);

    out << "live";
    for (const auto &[kind, total] : kindTotals) {
        out << ' ' << kind << '=' << allocTraceMegabytes(total.first) << "MB";
        if (!first) {
            const double deltaMb = allocTraceMegabytes(static_cast<uint64_t>(
                total.second < 0 ? -total.second : total.second));
            out << "(d=" << (total.second >= 0 ? "+" : "-") << deltaMb << "MB/window)";
        }
    }

    if (!first) {
        std::vector<Row> allocations = rows;
        std::sort(allocations.begin(), allocations.end(), [](const Row &a, const Row &b) {
            return a.createdDelta > b.createdDelta;
        });
        out << " alloc/window:";
        bool any = false;
        for (size_t i = 0; i < allocations.size() && i < 4; ++i) {
            if (allocations[i].createdDelta == 0) break;
            any = true;
            out << ' ' << allocations[i].kind << '@' << allocations[i].tag << "=+"
                << allocations[i].createdCount << '/'
                << allocTraceMegabytes(allocations[i].createdDelta) << "MB";
        }
        if (!any) out << " none";

        std::vector<Row> leaks = rows;
        std::sort(leaks.begin(), leaks.end(), [](const Row &a, const Row &b) {
            return a.liveDelta > b.liveDelta;
        });
        out << " net-growth/window:";
        any = false;
        for (size_t i = 0; i < leaks.size() && i < 4; ++i) {
            if (leaks[i].liveDelta <= 0) break;
            any = true;
            out << ' ' << leaks[i].kind << '@' << leaks[i].tag << "=+"
                << allocTraceMegabytes(static_cast<uint64_t>(leaks[i].liveDelta)) << "MB";
        }
        if (!any) out << " none";
    }

    return out.str();
}

} // namespace mcvr::diag
