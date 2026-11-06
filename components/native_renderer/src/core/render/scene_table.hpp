#pragma once

#include <algorithm>
#include <cstring>
#include <cstdint>
#include <limits>
#include <memory>
#include <optional>
#include <span>
#include <stdexcept>
#include <map>
#include <tuple>
#include <utility>
#include <vector>

namespace mcvr {
// Preserve the shader's contiguous ordering without first flattening the static
// suffix into a temporary frame vector. Only constant may borrow an owned span.
struct SceneTable {
    struct Segment {
        std::span<const std::byte> bytes;
        std::shared_ptr<const void> owner;
    };
    struct Directory {
        std::vector<Segment> segments;
        size_t bytes = 0;
        void add(Segment segment) {
            if (!segment.owner) throw std::invalid_argument("Unowned scene directory segment");
            if (segment.bytes.size() > SIZE_MAX - bytes) throw std::length_error("Scene directory size overflow");
            segments.push_back(std::move(segment));
            bytes += segments.back().bytes.size();
        }
    };
    std::span<const std::byte> prefix, constant, suffix;
    std::shared_ptr<const void> owner;
    std::vector<Segment> segments;
    std::shared_ptr<const Directory> directory;
    std::shared_ptr<const Directory> leading;

    template <class Visit>
    void each(Visit &&visit, bool includeLeading = true) const {
        size_t offset = !includeLeading && leading ? leading->bytes : 0;
        auto part = [&](std::span<const std::byte> bytes, const std::shared_ptr<const void> &retained) {
            if (bytes.size() > SIZE_MAX - offset) throw std::length_error("Scene table size overflow");
            if (!bytes.empty()) visit(offset, bytes, retained);
            offset += bytes.size();
        };
        if (includeLeading && leading)
            for (const auto &segment : leading->segments) part(segment.bytes, segment.owner);
        part(prefix, {});
        part(constant, owner);
        for (const auto &segment : segments) part(segment.bytes, segment.owner);
        if (directory)
            for (const auto &segment : directory->segments) part(segment.bytes, segment.owner);
        part(suffix, {});
    }

    size_t size() const {
        size_t result = 0;
        auto add = [&](size_t bytes) {
            if (bytes > SIZE_MAX - result) throw std::length_error("Scene table size overflow");
            result += bytes;
        };
        add(prefix.size());
        add(constant.size());
        add(suffix.size());
        for (const auto &segment : segments) add(segment.bytes.size());
        if (directory) add(directory->bytes);
        if (leading) add(leading->bytes);
        return result;
    }
    bool equals(std::span<const std::byte> bytes) const {
        if (bytes.size() != size()) return false;
        bool equal = true;
        each([&](size_t offset, auto part, const auto &) {
            equal &= !std::memcmp(bytes.data() + offset, part.data(), part.size());
        });
        return equal;
    }
    void write(std::span<std::byte> bytes) const {
        if (bytes.size() != size()) throw std::length_error("Scene table destination size mismatch");
        each([&](size_t offset, auto part, const auto &) {
            std::memcpy(bytes.data() + offset, part.data(), part.size());
        });
    }
};

struct SceneTablePatch {
    struct Copy {
        size_t source, target, bytes;
    };
    struct Upload {
        size_t target;
        std::span<const std::byte> bytes;
    };
    std::vector<Copy> copies;
    std::vector<Upload> uploads;
    size_t uploadBytes = 0;
    size_t residentBytes = 0;
    bool inPlace = false;
    void copy(size_t source, size_t target, size_t bytes) {
        if (!copies.empty() && copies.back().source + copies.back().bytes == source &&
            copies.back().target + copies.back().bytes == target)
            copies.back().bytes += bytes;
        else
            copies.push_back({source, target, bytes});
    }
};

template <class T>
SceneTable sceneTable(std::span<const T> changing,
                      size_t prefixCount = 0,
                      std::span<const T> constant = {},
                      std::shared_ptr<const void> owner = {}) {
    if (prefixCount > changing.size()) throw std::out_of_range("Scene table insertion outside dynamic records");
    if (!constant.empty() && !owner) throw std::invalid_argument("Static scene table has no immutable owner");
    return {std::as_bytes(changing.first(prefixCount)), std::as_bytes(constant),
            std::as_bytes(changing.subspan(prefixCount)), std::move(owner)};
}

// Only latest content is cached per recording context/table. Dynamic ends are
// copied exactly; immutable static data is retained rather than copied as a key.
// In-flight versions are immutable. Only a caller that has waited for the
// exclusive recording slot may update an existing buffer in place.
template <class Buffer>
class SceneTableCache {
    struct Content {
        std::vector<std::byte> prefix, suffix;
        std::vector<SceneTable::Segment> segments;
        std::shared_ptr<const SceneTable::Directory> directory;
        std::shared_ptr<const SceneTable::Directory> leading;
        size_t bytes;
        explicit Content(const SceneTable &data)
            : prefix(data.prefix.begin(), data.prefix.end()),
              suffix(data.suffix.begin(), data.suffix.end()),
              directory(data.directory),
              leading(data.leading),
              bytes(data.size()) {
            if (!data.constant.empty()) segments.push_back({data.constant, data.owner});
            segments.insert(segments.end(), data.segments.begin(), data.segments.end());
            for (const auto &segment : segments)
                if (!segment.owner) throw std::invalid_argument("Unowned static scene table");
        }
        static bool equal(std::span<const std::byte> a, std::span<const std::byte> b) {
            return a.size() == b.size() && (a.empty() || !std::memcmp(a.data(), b.data(), a.size()));
        }
        bool matches(const SceneTable &data) const {
            if (leading != data.leading || !equal(prefix, data.prefix) || !equal(suffix, data.suffix) ||
                directory != data.directory)
                return false;
            size_t index = 0;
            bool same = true;
            auto check = [&](auto bytes, const auto &owner) {
                same &= index < segments.size() && segments[index].owner == owner &&
                        segments[index].bytes.data() == bytes.data() && segments[index].bytes.size() == bytes.size();
                ++index;
            };
            if (!data.constant.empty()) check(data.constant, data.owner);
            for (const auto &segment : data.segments) check(segment.bytes, segment.owner);
            return same && index == segments.size();
        }
        SceneTablePatch patch(const SceneTable &data) const {
            SceneTablePatch result;
            const auto upload = [&](size_t target, auto bytes) {
                result.uploads.push_back({target, bytes});
                result.uploadBytes += bytes.size();
            };
            const size_t oldLeading = leading ? leading->bytes : 0;
            const size_t newLeading = data.leading ? data.leading->bytes : 0;
            if (data.leading) {
                if (data.leading == leading) {
                    result.copy(0, 0, newLeading);
                } else {
                    size_t offset = 0;
                    size_t oldOffset = 0;
                    for (size_t i = 0; i < data.leading->segments.size(); ++i) {
                        const auto &next = data.leading->segments[i];
                        if (leading && i < leading->segments.size()) {
                            const auto &old = leading->segments[i];
                            if (old.owner == next.owner && old.bytes.data() == next.bytes.data() &&
                                old.bytes.size() == next.bytes.size())
                                result.copy(oldOffset, offset, next.bytes.size());
                            else
                                upload(offset, next.bytes);
                            oldOffset += old.bytes.size();
                        } else {
                            upload(offset, next.bytes);
                        }
                        offset += next.bytes.size();
                    }
                }
            }
            // A published directory is immutable. Rebase its contiguous GPU
            // range directly instead of rebuilding/searching a page map when
            // only dynamic ends changed. No new GPU bytes or lifetime rules.
            if (directory && directory == data.directory && segments.empty() && data.segments.empty() &&
                data.constant.empty()) {
                const auto ends = [&](size_t source, size_t target, auto previous, auto next) {
                    if (next.empty()) return;
                    if (equal(previous, next))
                        result.copy(source, target, next.size());
                    else { upload(target, next); }
                };
                const size_t oldStatic = oldLeading + prefix.size();
                const size_t newStatic = newLeading + data.prefix.size();
                ends(oldLeading, newLeading, std::span(prefix), data.prefix);
                if (directory->bytes) result.copy(oldStatic, newStatic, directory->bytes);
                ends(oldStatic + directory->bytes, newStatic + directory->bytes, std::span(suffix), data.suffix);
                return result;
            }
            using Key = std::tuple<const void *, const void *, size_t>;
            std::map<Key, size_t> previous;
            size_t offset = oldLeading + prefix.size();
            auto add = [&](const auto &segment) {
                previous.emplace(Key{segment.owner.get(), segment.bytes.data(), segment.bytes.size()}, offset);
                offset += segment.bytes.size();
            };
            for (const auto &segment : segments) add(segment);
            if (directory)
                for (const auto &segment : directory->segments) add(segment);
            const auto oldSuffixOffset = offset;
            const auto suffixOffset = data.size() - data.suffix.size();
            data.each(
                [&](size_t target, auto bytes, const auto &owner) {
                    std::optional<size_t> source;
                    if (owner) {
                        auto found = previous.find(Key{owner.get(), bytes.data(), bytes.size()});
                        if (found != previous.end()) source = found->second;
                    } else if (target == newLeading && equal(prefix, bytes)) {
                        source = oldLeading;
                    } else if (target == suffixOffset && equal(suffix, bytes)) {
                        source = oldSuffixOffset;
                    }
                    if (source)
                        result.copy(*source, target, bytes.size());
                    else
                        upload(target, bytes);
                },
                false);
            return result;
        }
    };
    std::unique_ptr<Content> content_;
    std::shared_ptr<Buffer> buffer_;

  public:
    struct Selection {
        std::shared_ptr<Buffer> buffer;
        bool uploaded;
    };
    // Borrow the existing key for optional diagnostics; no flattened copy or extra cache.
    SceneTable observedContent() const {
        if (!content_) return {};
        return {content_->prefix, {}, content_->suffix, {}, content_->segments, content_->directory, content_->leading};
    }
    void reset() noexcept {
        content_.reset();
        buffer_.reset();
    }
    template <class Prepare, class Record, class Retain>
    Selection
    acquire(const SceneTable &data, Prepare &&prepare, Record &&record, Retain &&retain, bool reusable = true) {
        return acquirePatched(
            data, [&](const auto &input, const auto &, const auto &) { return prepare(input); },
            [&](const auto &buffer, const auto &, const auto &) { record(buffer); }, std::forward<Retain>(retain),
            reusable, false);
    }
    template <class Prepare, class Record, class Retain>
    Selection acquirePatched(const SceneTable &data,
                             Prepare &&prepare,
                             Record &&record,
                             Retain &&retain,
                             bool reusable = true,
                             bool incremental = true,
                             bool completedExclusiveSlot = false,
                             size_t inPlaceCapacity = 0) {
        if (!data.size()) throw std::invalid_argument("Empty scene table");
        if (!data.constant.empty() && !data.owner) throw std::invalid_argument("Unowned static scene table");
        for (const auto &segment : data.segments)
            if (!segment.owner) throw std::invalid_argument("Unowned static scene page");
        if (reusable && buffer_ && content_->matches(data)) {
            retain(buffer_);
            return {buffer_, false};
        }
        auto content = reusable ? std::make_unique<Content>(data) : nullptr;
        SceneTablePatch patch;
        if (incremental && buffer_ && content_)
            patch = content_->patch(data);
        else
            data.each([&](size_t target, auto bytes, const auto &) {
                patch.uploads.push_back({target, bytes});
                patch.uploadBytes += bytes.size();
            });
        if (completedExclusiveSlot && incremental && reusable && buffer_ && content_ &&
            (content_->bytes == content->bytes || inPlaceCapacity >= content->bytes) &&
            std::ranges::all_of(patch.copies, [](const auto &copy) { return copy.source == copy.target; })) {
            patch.inPlace = true;
            for (const auto &copy : patch.copies) patch.residentBytes += copy.bytes;
            patch.copies.clear();
        }
        const auto source = patch.inPlace || !patch.copies.empty() ? buffer_ : std::shared_ptr<Buffer>{};
        auto buffer = prepare(data, patch, source);
        if (!buffer) throw std::logic_error("Scene table preparation returned no buffer");
        if (source && source != buffer) retain(source);
        retain(buffer);
        record(buffer, patch, source);
        if (reusable) {
            content_.swap(content);
            buffer_.swap(buffer);
            return {buffer_, true};
        }
        reset();
        return {buffer, true};
    }
};
} // namespace mcvr
