#pragma once
#include <cstdint>
#include <optional>
#include <string>
#include <span>
#include <unordered_map>
#include <stdexcept>
#include <utility>
namespace mcvr::scene {
// Source lifetime and view selection are independent. Owners survive an empty visible selection.
template <class Record>
class InstanceStore {
    std::unordered_map<uint64_t, Record> records_;

  public:
    void put(uint64_t id, Record record) {
        if (!id) throw std::invalid_argument("Zero scene instance identity");
        records_.insert_or_assign(id, std::move(record));
    }
    const Record &at(uint64_t id) const {
        auto found = records_.find(id);
        if (found == records_.end()) throw std::out_of_range("Missing persistent scene instance " + std::to_string(id));
        return found->second;
    }
    bool contains(uint64_t id) const {
        return records_.contains(id);
    }
    std::optional<Record> remove(uint64_t id) {
        auto node = records_.extract(id);
        if (node.empty()) return std::nullopt;
        return std::move(node.mapped());
    }
    void clear() {
        records_.clear();
    }
    size_t size() const {
        return records_.size();
    }
};
// Bound strong model residency independently from the active view and the ordinary LRU cache.
template <class Key>
class ResourcePins {
    struct Entry {
        size_t references, bytes;
    };
    std::unordered_map<Key, Entry> entries_;
    size_t bytes_ = 0, limit_;

  public:
    explicit ResourcePins(size_t limit) : limit_(limit) {}
    bool pin(Key key, size_t bytes) {
        auto found = entries_.find(key);
        if (found != entries_.end()) {
            ++found->second.references;
            return true;
        }
        if (bytes > limit_ || bytes_ > limit_ - bytes) return false;
        entries_.emplace(key, Entry{1, bytes});
        bytes_ += bytes;
        return true;
    }
    void unpin(Key key) {
        auto found = entries_.find(key);
        if (found == entries_.end()) throw std::logic_error("Unowned model pin");
        if (--found->second.references == 0) {
            bytes_ -= found->second.bytes;
            entries_.erase(found);
        }
    }
    void clear() {
        entries_.clear();
        bytes_ = 0;
    }
    size_t bytes() const {
        return bytes_;
    }
};
} // namespace mcvr::scene
