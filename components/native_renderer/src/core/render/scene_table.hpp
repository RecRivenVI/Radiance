#pragma once

#include <cstring>
#include <cstdint>
#include <limits>
#include <memory>
#include <span>
#include <stdexcept>
#include <vector>

namespace mcvr {
// Preserve the shader's contiguous ordering without first flattening the static
// suffix into a temporary frame vector. Only constant may borrow an owned span.
struct SceneTable {
    std::span<const std::byte> prefix, constant, suffix;
    std::shared_ptr<const void> owner;

    size_t size() const {
        if (constant.size() > SIZE_MAX - prefix.size() || suffix.size() > SIZE_MAX - prefix.size() - constant.size())
            throw std::length_error("Scene table size overflow");
        return prefix.size() + constant.size() + suffix.size();
    }
    bool equals(std::span<const std::byte> bytes) const {
        if (bytes.size() != size()) return false;
        size_t offset = 0;
        for (auto part : {prefix, constant, suffix}) {
            if (!part.empty() && std::memcmp(bytes.data() + offset, part.data(), part.size())) return false;
            offset += part.size();
        }
        return true;
    }
    void write(std::span<std::byte> bytes) const {
        if (bytes.size() != size()) throw std::length_error("Scene table destination size mismatch");
        size_t offset = 0;
        for (auto part : {prefix, constant, suffix}) {
            if (!part.empty()) std::memcpy(bytes.data() + offset, part.data(), part.size());
            offset += part.size();
        }
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
// GPU versions are never overwritten, including multiple records in one frame.
template <class Buffer>
class SceneTableCache {
    struct Content {
        std::vector<std::byte> prefix, suffix;
        std::span<const std::byte> constant;
        std::shared_ptr<const void> owner;
        explicit Content(const SceneTable &data)
            : prefix(data.prefix.begin(), data.prefix.end()),
              suffix(data.suffix.begin(), data.suffix.end()),
              constant(data.constant),
              owner(constant.empty() ? nullptr : data.owner) {
            if (!constant.empty() && !owner) throw std::invalid_argument("Unowned static scene table");
        }
        bool matches(const SceneTable &data) const {
            const auto equal = [](auto a, auto b) {
                return a.size() == b.size() && (a.empty() || !std::memcmp(a.data(), b.data(), a.size()));
            };
            // Identity alone is insufficient: one owner can expose several tables.
            const bool sameSpan = owner == data.owner && constant.data() == data.constant.data() &&
                                  constant.size() == data.constant.size();
            // Retire the previous publication even when replacement bytes happen
            // to match; otherwise an appearance table could pin obsolete BLASes.
            return equal(std::span(prefix), data.prefix) && equal(std::span(suffix), data.suffix) &&
                   ((constant.empty() && data.constant.empty()) || sameSpan);
        }
    };
    std::unique_ptr<Content> content_;
    std::shared_ptr<Buffer> buffer_;

  public:
    struct Selection {
        std::shared_ptr<Buffer> buffer;
        bool uploaded;
    };
    void reset() noexcept {
        content_.reset();
        buffer_.reset();
    }
    template <class Prepare, class Record, class Retain>
    Selection
    acquire(const SceneTable &data, Prepare &&prepare, Record &&record, Retain &&retain, bool reusable = true) {
        if (!data.size()) throw std::invalid_argument("Empty scene table");
        if (reusable && buffer_ && content_->matches(data)) {
            retain(buffer_);
            return {buffer_, false};
        }
        auto content = reusable ? std::make_unique<Content>(data) : nullptr;
        auto buffer = prepare(data);
        if (!buffer) throw std::logic_error("Scene table preparation returned no buffer");
        retain(buffer);
        record(buffer);
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
