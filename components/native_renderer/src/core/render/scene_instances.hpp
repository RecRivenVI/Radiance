#pragma once
#include <cstdint>
#include <optional>
#include <string>
#include <span>
#include <unordered_map>
#include <stdexcept>
#include <utility>
#include <vector>
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
        const auto *record = find(id);
        if (!record) throw std::out_of_range("Missing persistent scene instance " + std::to_string(id));
        return *record;
    }
    const Record *find(uint64_t id) const {
        const auto found = records_.find(id);
        return found == records_.end() ? nullptr : &found->second;
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
// A source keeps a group of accepted instances selected until it replaces the group, so an
// unchanged source costs nothing per frame on its own side. Membership is weak: a member whose
// accepted version is removed drops out and never delays that removal. `stale` must be called for
// every identity whose stored record is extracted or cleared.
template <class Record>
class RetainedSelections {
    struct Group {
        std::vector<uint64_t> ids;
        std::vector<const Record *> records;
        uint64_t epoch = 0;
    };
    std::unordered_map<uint64_t, Group> groups_;
    std::unordered_map<uint64_t, uint32_t> references_;
    uint64_t epoch_ = 1;
    size_t members_ = 0;

    void release(const Group &group) {
        for (uint64_t id : group.ids) {
            auto found = references_.find(id);
            if (--found->second == 0) references_.erase(found);
        }
        members_ -= group.ids.size();
    }

  public:
    // An empty span ends the group.
    void set(uint64_t group, std::span<const uint64_t> ids) {
        if (!group) throw std::invalid_argument("Zero retained selection group");
        for (uint64_t id : ids)
            if (!id) throw std::invalid_argument("Zero retained instance identity");
        auto found = groups_.find(group);
        if (found != groups_.end()) {
            release(found->second);
            if (ids.empty()) {
                groups_.erase(found);
                return;
            }
        } else if (ids.empty())
            return;
        else
            found = groups_.try_emplace(group).first;
        auto &next = found->second;
        next.ids.assign(ids.begin(), ids.end());
        next.records.clear();
        next.epoch = 0;
        for (uint64_t id : ids) ++references_[id];
        members_ += ids.size();
    }
    void stale(uint64_t id) {
        if (references_.contains(id)) ++epoch_;
    }
    void staleAll() {
        ++epoch_;
    }
    void clear() {
        groups_.clear();
        references_.clear();
        members_ = 0;
        ++epoch_;
    }
    // Visits each member that still has an accepted version. Returns the visited count.
    template <class Visit>
    size_t each(const InstanceStore<Record> &store, Visit &&visit) {
        size_t visited = 0;
        for (auto &[key, group] : groups_) {
            if (group.epoch != epoch_) {
                group.records.clear();
                group.records.reserve(group.ids.size());
                for (uint64_t id : group.ids)
                    if (const auto *record = store.find(id)) group.records.push_back(record);
                group.epoch = epoch_;
            }
            for (const auto *record : group.records) visit(*record);
            visited += group.records.size();
        }
        return visited;
    }
    size_t groups() const {
        return groups_.size();
    }
    size_t members() const {
        return members_;
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
