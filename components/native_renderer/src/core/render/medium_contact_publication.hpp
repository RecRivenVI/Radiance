#pragma once
#include "core/render/medium_contact_scene.hpp"
#include <cstring>
#include <cstdlib>
#include <span>
#include <string_view>

namespace mcvr::medium {
struct Filter {
    uint64_t primitives = 0, neighbors = 0;
    uint32_t neighborCount = 0, unknown = 1;
};
static_assert(sizeof(Filter) == 24);

// One registry per view. Replacing a proof creates immutable GPU storage; old
// frames retain their exact proof and geometry owners through normal retirement.
template <class Buffer>
class ContactPublication {
    struct Version {
        uint64_t revision = 0, linksRevision = 0;
        uint32_t generation = 0, base = 0;
        std::vector<Filter> filters;
        std::shared_ptr<Buffer> bits, neighbors;
    };
    std::vector<Version> versions_;
    std::set<uint32_t> proofOwners_;
    uint64_t filterCapacity_ = 0;
    bool previousUnbounded_ = false;

    template <class Upload>
    void refresh(Version &version, const Scene::Source &source, Upload &upload) {
        if (version.revision == source.topologyRevision && version.generation == source.generation) return;
        ++prepared;
        Version next;
        next.revision = source.topologyRevision;
        next.generation = source.generation;
        next.linksRevision = source.linksRevision;
        next.filters.assign(source.geometryCount, Filter{});
        if (source.mesh && source.mesh->complete) {
            const bool sameLinks =
                version.linksRevision == source.linksRevision && version.generation == source.generation;
            if (sameLinks) {
                next.bits = version.bits;
                next.filters = version.filters;
            }
            std::vector<uint32_t> offsets(source.geometryCount), words;
            if (!sameLinks) {
                for (uint32_t g = 0; g < source.geometryCount; ++g) {
                    offsets[g] = uint32_t(words.size());
                    const uint32_t n = g < source.mesh->primitiveCounts.size() ? source.mesh->primitiveCounts[g] : 0;
                    words.resize(words.size() + (n + 31) / 32);
                }
                scene.links(source, [&](uint32_t g, uint32_t p, const auto &) {
                    if (g >= offsets.size() || g >= source.mesh->primitiveCounts.size() ||
                        p >= source.mesh->primitiveCounts[g])
                        throw std::logic_error("Medium proof primitive outside its publication");
                    words.at(offsets[g] + p / 32) |= 1u << (p % 32);
                });
                if (std::any_of(words.begin(), words.end(), [](auto w) { return w != 0; }))
                    next.bits = upload(std::as_bytes(std::span(words)));
                for (uint32_t g = 0; g < source.geometryCount; ++g)
                    if (next.bits && g < source.mesh->primitiveCounts.size() && source.mesh->primitiveCounts[g])
                        next.filters[g].primitives = next.bits->bufferAddress() + offsets[g] * sizeof(uint32_t);
            }
            std::vector<glm::vec4> bounds;
            for (auto other : scene.unresolved(source)) {
                const auto &target = scene.source(other);
                if (!target.mesh || !target.mesh->bounds.valid()) continue;
                const auto relative = source.space == target.space ? relativePose(source.local, target.local) :
                                                                     relativePose(source.world, target.world);
                const auto box = transformed(target.mesh->bounds, relative);
                // Outward rounding for a conservative exclusion, never evidence of contact.
                glm::vec3 lo(box.lo), hi(box.hi);
                for (int i = 0; i < 3; ++i) {
                    lo[i] = std::nextafter(lo[i], -std::numeric_limits<float>::infinity());
                    hi[i] = std::nextafter(hi[i], std::numeric_limits<float>::infinity());
                }
                bounds.emplace_back(lo, 0);
                bounds.emplace_back(hi, 0);
            }
            if (!bounds.empty()) next.neighbors = upload(std::as_bytes(std::span(bounds)));
            for (uint32_t g = 0; g < source.geometryCount; ++g) {
                auto &filter = next.filters[g];
                filter.unknown = 0;
                filter.neighbors = next.neighbors ? next.neighbors->bufferAddress() : 0;
                filter.neighborCount = uint32_t(bounds.size() / 2);
            }
        }
        version = std::move(next);
    }
    static void write(const Version &version, std::span<Filter> output, bool unbounded) {
        if (version.base + version.filters.size() > output.size())
            throw std::logic_error("Medium proof geometry range");
        std::copy(version.filters.begin(), version.filters.end(), output.begin() + version.base);
        if (unbounded)
            for (size_t g = 0; g < version.filters.size(); ++g) output[version.base + g].unknown = 1;
    }
    struct StaticReference {
        uint32_t slot, generation, offset;
    };
    std::weak_ptr<const void> staticOwner_;
    std::vector<StaticReference> staticSources_;
    uint32_t staticBase_ = 0;
    bool staticSelected_ = false;
    std::string_view mode_ = "0";

  public:
    struct Range {
        size_t start, end;
    };
    Scene scene;
    std::vector<Filter> directory;
    std::shared_ptr<Buffer> directoryBuffer;
    uint64_t cpuBytes = 0, gpuBytes = 0, prepared = 0;
    uint64_t directoryRows = 0, uploadedBytes = 0, copiedBytes = 0;
    void begin(std::string_view mode) {
        mode_ = mode;
        staticSelected_ = false;
        scene.begin();
    }
    template <class Owner, class Populate>
    void staticSources(const std::shared_ptr<Owner> &owner, uint32_t base, Populate populate) {
        staticSelected_ = true;
        const bool incremental = mode_ != "0";
        if (staticOwner_.lock() == owner) {
            if (incremental && base == staticBase_) return;
            if (std::all_of(staticSources_.begin(), staticSources_.end(), [&](const auto &entry) {
                    return scene.touch(entry.slot, entry.generation, base + entry.offset);
                })) {
                staticBase_ = base;
                return;
            }
        }
        auto previous = std::move(staticSources_);
        staticSources_.clear();
        populate([&](SourceKey key, const auto &mesh, const glm::dmat4 &pose, uint32_t offset, uint32_t count) {
            if (!mesh || mesh->complete && mesh->triangles.empty()) return;
            const auto slot = scene.add(key, mesh, pose, pose, 0, true, base + offset, count, incremental);
            staticSources_.push_back({slot, scene.source(slot).generation, offset});
        });
        if (incremental)
            for (const auto &entry : previous) scene.expirePersistent(entry.slot, entry.generation);
        staticOwner_ = owner;
        staticBase_ = base;
    }
    template <class Upload, class Keep, class Patch, class Phase>
    void publish(uint32_t count, Upload upload, Keep keep, Patch patch, Phase phase) {
        if (count > 0x08000000u) throw std::length_error("Medium request geometry index overflow");
        const bool incremental = mode_ != "0";
        if (incremental && !staticSelected_) {
            for (const auto &entry : staticSources_) scene.expirePersistent(entry.slot, entry.generation);
            staticSources_.clear();
            staticOwner_.reset();
        }
        scene.finish(incremental);
        phase("pt.medium.directory-prepare");
        count = std::max(1u, count);
        static const bool forceUnknown = [] {
            const auto *value = std::getenv("MCVR_MEDIUM_CONTACT_FORCE_UNKNOWN");
            return value && std::string_view(value) == "1";
        }();
        const bool unbounded = forceUnknown || scene.unboundedUnknown();
        versions_.resize(scene.sources().size());
        cpuBytes = 0;
        gpuBytes = 0;
        prepared = 0;
        directoryRows = uploadedBytes = copiedBytes = 0;
        if (incremental) {
            publishIncremental(count, unbounded, mode_ == "verify", upload, keep, patch, phase);
            return;
        }
        directory.assign(count, Filter{});
        directoryRows = count;
        for (uint32_t slot = 0; slot < scene.sources().size(); ++slot) {
            const auto &pointer = scene.sources()[slot];
            if (!pointer) {
                versions_[slot] = {};
                continue;
            }
            const auto &source = *pointer;
            auto &version = versions_[source.slot];
            refresh(version, source, upload);
            version.base = source.geometryBase;
            write(version, directory, unbounded);
            for (const auto &buffer : {version.bits, version.neighbors})
                if (buffer) {
                    keep(buffer);
                    gpuBytes += buffer->size();
                }
            cpuBytes += version.filters.capacity() * sizeof(Filter);
        }
        // Directory content is small; immutable storage is reused exactly when addresses match.
        phase("pt.medium.directory-transport");
        const auto bytes = std::as_bytes(std::span(directory));
        if (bytes.size() != lastDirectory_.size() || !std::equal(bytes.begin(), bytes.end(), lastDirectory_.begin())) {
            directoryBuffer = upload(bytes);
            uploadedBytes = bytes.size();
            lastDirectory_.assign(bytes.begin(), bytes.end());
        }
        keep(directoryBuffer);
        gpuBytes += directoryBuffer->size();
        cpuBytes += lastDirectory_.capacity() + directory.capacity() * sizeof(Filter);
    }

  private:
    template <class Upload, class Keep, class Patch, class Phase>
    void publishIncremental(
        uint32_t count, bool unbounded, bool verify, Upload &upload, Keep &keep, Patch &patch, Phase &phase) {
        const size_t previousCount = directory.size();
        directory.resize(count);
        std::vector<Range> dirty;
        const auto add = [&](size_t start, size_t end) {
            if (start < end) dirty.push_back({start, end});
        };
        add(previousCount, count);
        const auto &changed = scene.publicationChanges();
        // Clear old placements before writing any new ranges, including shifted ranges that overlap.
        for (const auto slot : changed) {
            auto &version = versions_[slot];
            const auto &source = scene.sources()[slot];
            if (source && version.base == source->geometryBase && version.filters.size() == source->geometryCount)
                continue;
            const auto end = std::min(size_t(count), version.base + version.filters.size());
            if (version.base < end) {
                std::fill(directory.begin() + version.base, directory.begin() + end, Filter{});
                add(version.base, end);
            }
        }
        phase("pt.medium.proof-publish");
        for (const auto slot : changed) {
            const auto &source = scene.sources()[slot];
            auto &version = versions_[slot];
            filterCapacity_ -= version.filters.capacity();
            if (source) {
                refresh(version, *source, upload);
                version.base = source->geometryBase;
                write(version, directory, unbounded);
                add(version.base, version.base + version.filters.size());
            } else {
                version = {};
            }
            filterCapacity_ += version.filters.capacity();
            if (version.bits || version.neighbors)
                proofOwners_.insert(slot);
            else
                proofOwners_.erase(slot);
        }
        if (unbounded != previousUnbounded_) {
            // An unlocated source affects every proven source, including stationary ones.
            for (const auto &version : versions_) write(version, directory, unbounded);
            add(0, count);
        }
        previousUnbounded_ = unbounded;
        if (verify) {
            reference_.assign(count, Filter{});
            for (uint32_t slot = 0; slot < versions_.size(); ++slot) {
                const auto &source = scene.sources()[slot];
                const auto &version = versions_[slot];
                if (!source) {
                    if (version.generation) throw std::logic_error("Stale medium proof source");
                    continue;
                }
                if (version.revision != source->topologyRevision || version.generation != source->generation ||
                    version.base != source->geometryBase || version.filters.size() != source->geometryCount)
                    throw std::logic_error("Stale medium proof version");
                write(version, reference_, unbounded);
            }
            if (!std::equal(reference_.begin(), reference_.end(), directory.begin(),
                            [](const auto &a, const auto &b) { return std::memcmp(&a, &b, sizeof(Filter)) == 0; }))
                throw std::logic_error("Incremental medium directory differs from full publication");
        }
        phase("pt.medium.proof-retain");
        for (const auto slot : proofOwners_)
            for (const auto &buffer : {versions_[slot].bits, versions_[slot].neighbors})
                if (buffer) {
                    keep(buffer);
                    gpuBytes += buffer->size();
                }
        phase("pt.medium.directory-transport");
        if (!dirty.empty()) {
            std::sort(dirty.begin(), dirty.end(), [](const Range &a, const Range &b) { return a.start < b.start; });
            size_t ranges = 1;
            for (size_t i = 1; i < dirty.size(); ++i)
                if (dirty[ranges - 1].end >= dirty[i].start)
                    dirty[ranges - 1].end = std::max(dirty[ranges - 1].end, dirty[i].end);
                else
                    dirty[ranges++] = dirty[i];
            dirty.resize(ranges);
            for (const auto range : dirty) directoryRows += range.end - range.start;
            uploadedBytes = directoryRows * sizeof(Filter);
            copiedBytes = directory.size() * sizeof(Filter) - uploadedBytes;
            directoryBuffer = patch(std::span<const Filter>(directory), std::span<const Range>(dirty), directoryBuffer);
        }
        keep(directoryBuffer);
        gpuBytes += directoryBuffer->size();
        cpuBytes = (filterCapacity_ + directory.capacity() + reference_.capacity()) * sizeof(Filter);
    }
    std::vector<std::byte> lastDirectory_;
    std::vector<Filter> reference_;
};
} // namespace mcvr::medium
