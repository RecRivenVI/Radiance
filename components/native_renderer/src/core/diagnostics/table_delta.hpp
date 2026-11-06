#pragma once

#include "core/render/scene_table.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include <algorithm>
#include <string>

namespace mcvr::profile {
// Compare actual records at the same ordinal, independently of segment ownership.
// Appended/removed records are separate from changed overlapping records. A shifted
// suffix may count as changed; this does not claim the source geometry changed.
struct TableDelta {
    size_t before = 0, after = 0, changed = 0, prefix = 0, suffix = 0;
};
inline TableDelta tableDelta(const SceneTable &previous, const SceneTable &current, size_t stride) {
    if (!stride || previous.size() % stride || current.size() % stride)
        throw std::invalid_argument("Unaligned diagnostic table records");
    TableDelta result{previous.size() / stride, current.size() / stride};
    std::vector<std::span<const std::byte>> a, b;
    previous.each([&](size_t, auto bytes, const auto &) { a.push_back(bytes); });
    current.each([&](size_t, auto bytes, const auto &) { b.push_back(bytes); });
    size_t ai = 0, bi = 0, ao = 0, bo = 0, offset = 0;
    const auto overlap = std::min(result.before, result.after);
    const auto limit = overlap * stride;
    size_t firstChanged = overlap, lastChanged = SIZE_MAX;
    while (offset < limit) {
        const auto length = std::min({a[ai].size() - ao, b[bi].size() - bo, limit - offset});
        if (std::memcmp(a[ai].data() + ao, b[bi].data() + bo, length)) {
            size_t local = 0;
            while (local < length) {
                const auto record = (offset + local) / stride;
                const auto part = std::min(length - local, stride - (offset + local) % stride);
                if (record != lastChanged && std::memcmp(a[ai].data() + ao + local, b[bi].data() + bo + local, part)) {
                    ++result.changed;
                    firstChanged = std::min(firstChanged, record);
                    lastChanged = record;
                }
                local += part;
            }
        }
        offset += length;
        ao += length;
        bo += length;
        if (ao == a[ai].size()) {
            ++ai;
            ao = 0;
        }
        if (bo == b[bi].size()) {
            ++bi;
            bo = 0;
        }
    }
    result.prefix = firstChanged;
    result.suffix = lastChanged == SIZE_MAX ? overlap : overlap - lastChanged - 1;
    return result;
}
inline void
observeTable(const SceneTable &previous, const SceneTable &current, size_t stride, const std::string &label) {
    if (!flowAuditEnabled()) return;
    Scope observation("diagnostic.table-delta");
    const auto delta = tableDelta(previous, current, stride);
    auto counter = [&](const char *field, size_t count) {
        emit(frame, 5, (label + field).c_str(), count, count * stride);
    };
    counter(".before", delta.before);
    counter(".after", delta.after);
    counter(".changed-overlap", delta.changed);
    counter(".added", delta.after - std::min(delta.before, delta.after));
    counter(".removed", delta.before - std::min(delta.before, delta.after));
    counter(".equal-prefix", delta.prefix);
    counter(".equal-suffix", delta.suffix);
}
} // namespace mcvr::profile
