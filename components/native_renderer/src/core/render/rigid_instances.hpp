#pragma once

#include "common/render_abi.hpp"
#include "core/render/draw_colour.hpp"
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <vector>
#include <span>
#include <unordered_map>

namespace mcvr::rigid {

struct SurfaceParameters {
    uint32_t flags = 0;
    int32_t light = 0, overlay = 0, reserved = 0;
    float uv[6]{1, 0, 0, 0, 1, 0};
    float colour[4]{1, 1, 1, 1};

    void validate() const {
        constexpr auto supported = abi::APPEARANCE_COLOR_MULTIPLY | abi::APPEARANCE_OVERRIDE_LIGHT |
                                   abi::APPEARANCE_OVERRIDE_OVERLAY | abi::APPEARANCE_UV_AFFINE;
        if ((flags & ~supported) || reserved) throw std::invalid_argument("Invalid local surface flags");
        surface::validateDrawColour(colour);
        for (float value : uv)
            if (!std::isfinite(value)) throw std::invalid_argument("Non-finite local surface UV");
    }
};
static_assert(sizeof(SurfaceParameters) == 56);

// Trace offset/stride 1/1 addresses G material records after one shared shadow
// record. Previous transforms are indexed by instance, not by geometry.
template <class Geometries, class Groups, class Matrices, class Matrix, class AppendGeometry>
void appendInstanceTables(const Geometries &geometries,
                          typename Groups::value_type shadow,
                          Groups &groups,
                          Matrices &matrices,
                          const Matrix &previousTransform,
                          AppendGeometry &&appendGeometry) {
    groups.push_back(shadow);
    for (const auto &geometry : geometries) {
        groups.push_back(geometry.group);
        appendGeometry(geometry);
    }
    matrices.push_back(previousTransform);
}

// One persistent-model draw, written by RigidModelCapture.Batch in native byte order. The local
// vertex pointer is only read when the model is not resident; the transform stays per instance.
struct SubmissionRecord {
    uint64_t model;    // Java resource id << 32 | encoded geometry type
    uint64_t history;  // stable owner/draw identity, top bit set
    uint64_t vertices; // local PBR quads, 128 bytes per vertex
    double x, y, z;    // owner origin before camera-relative float conversion
    float matrix[16];  // column-major local transform
    int32_t type;
    int32_t texture;
    int32_t vertexCount;
    int32_t mask;
    int32_t group; // index into the submission's group-name table
    int32_t coordinates = 0;
    float drawColour[4]{1.0f, 1.0f, 1.0f, 1.0f};
    uint64_t space = 0;
    SurfaceParameters surface;
};
static_assert(sizeof(SubmissionRecord) == abi::RIGID_RECORD_BYTES);
static_assert(offsetof(SubmissionRecord, drawColour) == abi::RIGID_COLOUR_OFFSET);
static_assert(offsetof(SubmissionRecord, surface) == abi::RIGID_SURFACE_OFFSET);
inline void validateBatch(int version, int stride, int bytes, int count) {
    if (version != abi::RIGID_INPUT_VERSION || stride != sizeof(SubmissionRecord) || bytes < 0 || count < 0 ||
        uint64_t(count) * sizeof(SubmissionRecord) > uint64_t(bytes))
        throw std::invalid_argument("Invalid rigid model batch ABI or byte range");
}
static_assert(offsetof(SubmissionRecord, x) == 24);
static_assert(offsetof(SubmissionRecord, matrix) == 48);
static_assert(offsetof(SubmissionRecord, type) == 112);
static_assert(offsetof(SubmissionRecord, group) == 128);

inline void validate(const SubmissionRecord &record, size_t groupCount) {
    if (!record.model || !(record.history >> 63u) || !record.vertices || record.vertexCount <= 0 ||
        record.vertexCount % 4 || record.group < 0 || static_cast<size_t>(record.group) >= groupCount ||
        record.coordinates < 0 || record.coordinates > 2 || (record.coordinates && record.space))
        throw std::invalid_argument("Invalid rigid model submission");
    surface::validateDrawColour(record.drawColour);
    record.surface.validate();
    for (float value : record.matrix)
        if (!std::isfinite(value)) throw std::invalid_argument("Non-finite rigid model transform");
    if (!std::isfinite(record.x) || !std::isfinite(record.y) || !std::isfinite(record.z))
        throw std::invalid_argument("Non-finite rigid model origin");
}

// Immutable local model layers supplied with a draw. Read only on a resident-resource miss.
// Grouping is declared by the source's model/part identity, never discovered from world vertices.
struct ModelBinding {
    uint64_t model, vertices;
    uint32_t vertexCount;
    int32_t type, texture, group;
    float drawColour[4];
    uint32_t ordinal, count;
    std::array<float, 6> uv{1, 0, 0, 0, 1, 0};
};
static_assert(sizeof(ModelBinding) == abi::RIGID_BINDING_BYTES);
inline void validateBindingBatch(int version, int byteCount, int count) {
    if (version != abi::RIGID_BINDING_VERSION || byteCount < 0 || count < 0 ||
        uint64_t(count) * sizeof(ModelBinding) > uint64_t(byteCount))
        throw std::invalid_argument("Invalid local model binding ABI or byte range");
}
using BindingTable = std::unordered_map<uint64_t, std::span<const ModelBinding>>;

inline BindingTable bindings(std::span<const ModelBinding> records, size_t groupCount) {
    BindingTable result;
    for (size_t at = 0; at < records.size();) {
        const auto &first = records[at];
        if (!first.model || !first.count || first.count > records.size() - at || first.ordinal)
            throw std::invalid_argument("Invalid local model binding range");
        const auto range = records.subspan(at, first.count);
        for (size_t j = 0; j < range.size(); ++j) {
            const auto &record = range[j];
            if (record.model != first.model || record.count != first.count || record.ordinal != j || !record.vertices ||
                !record.vertexCount || record.vertexCount > INT32_MAX || record.vertexCount % 4 || record.group < 0 ||
                size_t(record.group) >= groupCount)
                throw std::invalid_argument("Invalid local model binding");
            surface::validateDrawColour(record.drawColour);
            for (float value : record.uv)
                if (!std::isfinite(value)) throw std::invalid_argument("Non-finite local binding UV");
        }
        if (!result.emplace(first.model, range).second)
            throw std::invalid_argument("Duplicate local model binding resource");
        at += range.size();
    }
    return result;
}

// Previous-frame record for one rigid instance: the camera-relative object-to-world rows and the
// geometry that frame actually referenced. Its owner keeps those buffers alive.
struct HistoryEntry {
    uint64_t history = 0;
    uint64_t model = 0;
    std::array<float, 12> transform{};
    uint64_t indexAddress = 0, positionAddress = 0;
    uint32_t vertexCount = 0, indexCount = 0;
};

// Sorted once per frame instead of allocating one tree node per instance. Duplicate identities
// resolve to the last submission, matching the previous map assignment semantics.
class HistoryFrame {
  public:
    void reserve(size_t count) {
        entries_.reserve(count);
    }
    void add(const HistoryEntry &entry) {
        entries_.push_back(entry);
        sorted_ = false;
    }
    void finish() {
        std::stable_sort(entries_.begin(), entries_.end(),
                         [](const HistoryEntry &a, const HistoryEntry &b) { return a.history < b.history; });
        // Keep the last entry of every identity.
        std::vector<HistoryEntry> unique;
        unique.reserve(entries_.size());
        for (size_t i = 0; i < entries_.size(); ++i)
            if (i + 1 == entries_.size() || entries_[i + 1].history != entries_[i].history)
                unique.push_back(entries_[i]);
        entries_.swap(unique);
        sorted_ = true;
    }
    const HistoryEntry *find(uint64_t history) const {
        if (!sorted_) throw std::logic_error("Rigid history frame queried before finish");
        auto it = std::lower_bound(entries_.begin(), entries_.end(), history,
                                   [](const HistoryEntry &entry, uint64_t key) { return entry.history < key; });
        return it != entries_.end() && it->history == history ? &*it : nullptr;
    }
    size_t size() const {
        return entries_.size();
    }

  private:
    std::vector<HistoryEntry> entries_;
    bool sorted_ = true;
};

// Motion history exactly as the per-entity path assigns it: a previous transform whenever the
// identity existed, but previous geometry addresses only for the same model and topology.
struct PreviousGeometry {
    std::array<float, 12> transform;
    uint64_t indexAddress, positionAddress;
    bool hasTransform;
};
inline PreviousGeometry previous(const HistoryEntry *entry, uint64_t model, uint32_t vertexCount, uint32_t indexCount) {
    if (!entry) return {{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0}, 0, 0, false};
    const bool sameGeometry =
        entry->model == model && entry->vertexCount == vertexCount && entry->indexCount == indexCount;
    return {entry->transform, sameGeometry ? entry->indexAddress : 0, sameGeometry ? entry->positionAddress : 0, true};
}

} // namespace mcvr::rigid
