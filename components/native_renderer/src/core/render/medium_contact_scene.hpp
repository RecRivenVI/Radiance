#pragma once

#include "core/render/medium_contacts.hpp"
#include <condition_variable>
#include <exception>
#include <deque>
#include <map>
#include <mutex>
#include <set>
#include <thread>
#include <tuple>

namespace mcvr::medium {
struct SourceKey {
    uint64_t kind, owner, instance;
    auto operator<=>(const SourceKey &) const = default;
};
struct Reference {
    uint32_t source, generation, geometry, primitive;
};
class Scene {
  public:
    struct Source {
        SourceKey key;
        uint32_t slot = 0, generation = 0, geometryBase = 0, geometryCount = 0;
        uint64_t seen = 0, space = 0, topologyRevision = 1, linksRevision = 1;
        std::shared_ptr<const Mesh> mesh;
        glm::dmat4 world{1}, local{1};
        Box box;
        bool fixed = false, large = false, persistent = false;
        unsigned quiet = 0;
        std::vector<std::array<int64_t, 3>> cells;
        std::set<uint32_t> neighbors;
    };
    struct PairKey {
        uint32_t a, b;
        auto operator<=>(const PairKey &) const = default;
    };
    struct Pair {
        uint64_t revision = 0;
        glm::dmat4 relative{1};
        const Mesh *meshA = nullptr, *meshB = nullptr;
        bool known = false, pending = false;
        uint64_t stableSince = 0;
        double uncertainty = 0;
        std::vector<Link> links;
    };

  private:
    struct Job {
        PairKey key;
        uint64_t epoch, revision;
        std::shared_ptr<const Mesh> a, b;
        glm::dmat4 relative;
        double uncertainty;
        std::vector<Link> links;
        std::exception_ptr failure;
    };
    uint64_t frame_ = 0, epoch_ = 1, revision_ = 0;
    uint32_t nextGeneration_ = 0;
    std::vector<std::unique_ptr<Source>> sources_;
    std::vector<uint32_t> free_;
    std::map<SourceKey, uint32_t> keys_;
    std::map<std::array<int64_t, 3>, std::set<uint32_t>> grid_;
    std::set<uint32_t> large_, changed_, publicationChanges_, transient_;
    size_t unboundedSources_ = 0;
    std::map<PairKey, Pair> pairs_;
    std::deque<std::pair<PairKey, uint64_t>> waiting_;
    std::mutex workMutex_;
    std::condition_variable workReady_;
    std::deque<Job> work_, done_;
    bool stopping_ = false;
    std::thread worker_;
    static bool unbounded(const Source &source) {
        return !source.mesh || (!source.mesh->complete && !source.box.valid());
    }
    void invalidate(Source &source) {
        ++source.topologyRevision;
        publicationChanges_.insert(source.slot);
    }
    void work() {
        for (;;) {
            Job job;
            {
                std::unique_lock lock(workMutex_);
                workReady_.wait(lock, [&] { return stopping_ || !work_.empty(); });
                if (stopping_) return;
                job = std::move(work_.front());
                work_.pop_front();
            }
            try {
                job.links = contacts(*job.a, *job.b, job.relative, false, job.uncertainty);
            } catch (...) { job.failure = std::current_exception(); }
            {
                std::lock_guard lock(workMutex_);
                done_.push_back(std::move(job));
            }
        }
    }
    static std::array<int64_t, 3> cell(glm::dvec3 p) {
        return {int64_t(std::floor(p.x / 32)), int64_t(std::floor(p.y / 32)), int64_t(std::floor(p.z / 32))};
    }
    static uint64_t cells(const Box &box) {
        if (!box.valid()) return 0;
        const auto padding = glm::dvec3(numericalPadding(box));
        const auto a = cell(box.lo - padding), b = cell(box.hi + padding);
        long double n = 1;
        for (int i = 0; i < 3; ++i) n *= 1.0L + b[i] - a[i];
        return n > 32768 ? 32769 : uint64_t(n);
    }
    template <class Visit>
    static void eachCell(const Box &box, Visit visit) {
        const auto padding = glm::dvec3(numericalPadding(box));
        const auto a = cell(box.lo - padding), b = cell(box.hi + padding);
        for (auto x = a[0]; x <= b[0]; ++x)
            for (auto y = a[1]; y <= b[1]; ++y)
                for (auto z = a[2]; z <= b[2]; ++z) visit(std::array{x, y, z});
    }
    void eraseGrid(Source &s) {
        if (s.large) large_.erase(s.slot);
        for (auto c : s.cells) {
            auto it = grid_.find(c);
            if (it != grid_.end() && it->second.erase(s.slot) && it->second.empty()) grid_.erase(it);
        }
        s.cells.clear();
        s.large = false;
    }
    void insertGrid(Source &s) {
        if (!s.box.valid()) return;
        s.large = cells(s.box) > 512;
        if (s.large)
            large_.insert(s.slot);
        else
            eachCell(s.box, [&](auto c) {
                grid_[c].insert(s.slot);
                s.cells.push_back(c);
            });
    }
    std::set<uint32_t> nearby(const Source &s) const {
        std::set<uint32_t> result = large_;
        if (cells(s.box) > 32768) {
            for (const auto &v : sources_)
                if (v) result.insert(v->slot);
        } else if (s.box.valid())
            eachCell(s.box, [&](auto c) {
                auto it = grid_.find(c);
                if (it != grid_.end()) result.insert(it->second.begin(), it->second.end());
            });
        result.erase(s.slot);
        return result;
    }
    void removePair(PairKey key) {
        auto it = pairs_.find(key);
        if (it == pairs_.end()) return;
        for (auto slot : {key.a, key.b})
            if (sources_[slot]) {
                sources_[slot]->neighbors.erase(slot == key.a ? key.b : key.a);
                invalidate(*sources_[slot]);
                if (it->second.known) ++sources_[slot]->linksRevision;
            }
        pairs_.erase(it);
    }
    void refreshPair(PairKey key) {
        auto &a = *sources_[key.a], &b = *sources_[key.b];
        if (!a.box.padded(numericalPadding(a.box) + numericalPadding(b.box)).overlaps(b.box)) {
            removePair(key);
            return;
        }
        const bool common = a.space == b.space;
        const auto relative = common ? relativePose(a.local, b.local) : relativePose(a.world, b.world);
        const double uncertainty = common ? poseUncertainty(a.local, b.local) : poseUncertainty(a.world, b.world);
        auto [it, inserted] = pairs_.try_emplace(key);
        auto &pair = it->second;
        // A mesh replacement or relative pose change invalidates proofs before this frame is published.
        const bool invalid = inserted || pair.relative != relative || pair.uncertainty != uncertainty ||
                             pair.meshA != a.mesh.get() || pair.meshB != b.mesh.get();
        if (invalid) {
            if (pair.known) {
                ++a.linksRevision;
                ++b.linksRevision;
            }
            pair = {++revision_, relative, a.mesh.get(), b.mesh.get()};
            pair.stableSince = frame_;
            pair.uncertainty = uncertainty;
            invalidate(a);
            invalidate(b);
        }
        a.neighbors.insert(key.b);
        b.neighbors.insert(key.a);
        const bool stable = (a.fixed && b.fixed) || frame_ >= pair.stableSince + 2;
        if (!pair.known && !pair.pending && stable && a.mesh && b.mesh && a.mesh->complete && b.mesh->complete) {
            pair.pending = true;
            waiting_.push_back({key, pair.revision});
        }
    }

  public:
    Scene() : worker_([this] { work(); }) {}
    ~Scene() {
        {
            std::lock_guard lock(workMutex_);
            stopping_ = true;
            work_.clear();
        }
        workReady_.notify_one();
        worker_.join();
    }
    void clear() {
        ++epoch_;
        sources_.clear();
        keys_.clear();
        free_.clear();
        grid_.clear();
        large_.clear();
        pairs_.clear();
        waiting_.clear();
        changed_.clear();
        publicationChanges_.clear();
        transient_.clear();
        unboundedSources_ = 0;
        std::lock_guard lock(workMutex_);
        work_.clear();
        done_.clear();
    }
    void begin() {
        ++frame_;
        changed_.clear();
        publicationChanges_.clear();
    }
    bool touch(uint32_t slot, uint32_t generation, uint32_t base) {
        if (slot >= sources_.size() || !sources_[slot] || sources_[slot]->generation != generation) return false;
        auto &source = *sources_[slot];
        source.seen = frame_;
        if (source.geometryBase != base) publicationChanges_.insert(slot);
        source.geometryBase = base;
        return true;
    }
    void expirePersistent(uint32_t slot, uint32_t generation) {
        if (slot >= sources_.size() || !sources_[slot] || sources_[slot]->generation != generation) return;
        auto &source = *sources_[slot];
        if (source.seen == frame_ || !source.persistent) return;
        source.persistent = false;
        transient_.insert(slot);
    }
    uint32_t add(SourceKey key,
                 std::shared_ptr<const Mesh> mesh,
                 const glm::dmat4 &world,
                 const glm::dmat4 &local,
                 uint64_t space,
                 bool fixed,
                 uint32_t base,
                 uint32_t count,
                 bool persistent = false) {
        auto it = keys_.find(key);
        uint32_t slot;
        const bool created = it == keys_.end();
        if (it == keys_.end()) {
            if (free_.empty()) {
                slot = uint32_t(sources_.size());
                sources_.push_back(nullptr);
            } else {
                slot = free_.back();
                free_.pop_back();
            }
            auto s = std::make_unique<Source>();
            s->key = key;
            s->slot = slot;
            s->generation = ++nextGeneration_;
            sources_[slot] = std::move(s);
            keys_[key] = slot;
        } else
            slot = it->second;
        auto &s = *sources_[slot];
        if (created || s.persistent != persistent) {
            s.persistent = persistent;
            if (persistent)
                transient_.erase(slot);
            else
                transient_.insert(slot);
        }
        const bool content = s.mesh != mesh || s.geometryCount != count;
        const bool moved = s.world != world || s.space != space || s.local != local;
        s.seen = frame_;
        if (created || s.geometryBase != base) publicationChanges_.insert(slot);
        s.geometryBase = base;
        s.geometryCount = count;
        s.fixed = fixed;
        if (content || moved || created) {
            if (!created && unbounded(s)) --unboundedSources_;
            eraseGrid(s);
            s.mesh = std::move(mesh);
            s.world = world;
            s.local = local;
            s.space = space;
            s.box = s.mesh ? transformed(s.mesh->bounds, world) : Box{};
            if (unbounded(s)) ++unboundedSources_;
            s.quiet = 0;
            insertGrid(s);
            changed_.insert(slot);
            if (content) {
                invalidate(s);
                ++s.linksRevision;
            }
        } else
            ++s.quiet;
        // Revisit a stationary dynamic pair once it qualifies for a stable proof.
        if (s.quiet == 2) changed_.insert(slot);
        return slot;
    }
    void finish(bool incremental = false) {
        const auto expire = [&](auto &p) {
            auto neighbors = p->neighbors;
            for (auto other : neighbors) removePair({std::min(p->slot, other), std::max(p->slot, other)});
            eraseGrid(*p);
            keys_.erase(p->key);
            free_.push_back(p->slot);
            publicationChanges_.insert(p->slot);
            if (unbounded(*p)) --unboundedSources_;
            p.reset();
        };
        lifetimeVisits = 0;
        if (incremental) {
            auto slot = transient_.begin();
            while (slot != transient_.end()) {
                ++lifetimeVisits;
                auto &source = sources_[*slot];
                if (source->seen != frame_) {
                    expire(source);
                    slot = transient_.erase(slot);
                } else {
                    ++slot;
                }
            }
        } else {
            lifetimeVisits = sources_.size();
            for (auto &source : sources_)
                if (source && source->seen != frame_) {
                    transient_.erase(source->slot);
                    expire(source);
                }
        }
        for (auto slot : changed_)
            if (sources_[slot]) {
                const auto &s = *sources_[slot];
                auto nearbySlots = nearby(s);
                nearbySlots.insert(s.neighbors.begin(), s.neighbors.end());
                for (auto other : nearbySlots)
                    if (sources_[other]) refreshPair({std::min(slot, other), std::max(slot, other)});
            }
        for (auto &[key, pair] : pairs_)
            if (!pair.known && !pair.pending) refreshPair(key);
        std::deque<Job> completed;
        {
            std::lock_guard lock(workMutex_);
            completed.swap(done_);
        }
        for (auto &job : completed) {
            auto it = pairs_.find(job.key);
            if (job.epoch != epoch_ || it == pairs_.end() || it->second.revision != job.revision) continue;
            if (job.failure) std::rethrow_exception(job.failure);
            it->second.links = std::move(job.links);
            it->second.known = true;
            it->second.pending = false;
            invalidate(*sources_[job.key.a]);
            invalidate(*sources_[job.key.b]);
            ++sources_[job.key.a]->linksRevision;
            ++sources_[job.key.b]->linksRevision;
        }
        {
            std::lock_guard lock(workMutex_);
            std::erase_if(work_, [&](const Job &job) {
                auto it = pairs_.find(job.key);
                return job.epoch != epoch_ || it == pairs_.end() || it->second.revision != job.revision;
            });
            unsigned submitted = 0;
            while (!waiting_.empty() && work_.size() < 128 && submitted < 32) {
                const auto [key, revision] = waiting_.front();
                waiting_.pop_front();
                auto it = pairs_.find(key);
                if (it == pairs_.end() || it->second.known || it->second.revision != revision) continue;
                auto &a = *sources_[key.a], &b = *sources_[key.b];
                work_.push_back(
                    {key, epoch_, it->second.revision, a.mesh, b.mesh, it->second.relative, it->second.uncertainty});
                ++submitted;
            }
        }
        workReady_.notify_one();
    }
    bool unboundedUnknown() const {
        return unboundedSources_ != 0;
    }
    size_t lifetimeVisits = 0;
    const auto &publicationChanges() const {
        return publicationChanges_;
    }
    uint64_t meshBytes() const {
        uint64_t bytes = 0;
        std::set<const Mesh *> meshes;
        for (const auto &s : sources_)
            if (s && s->mesh && meshes.insert(s->mesh.get()).second) bytes += s->mesh->bytes();
        for (const auto &[key, pair] : pairs_) bytes += pair.links.capacity() * sizeof(Link);
        return bytes;
    }
    const auto &sources() const {
        return sources_;
    }
    const auto &pairs() const {
        return pairs_;
    }
    const Source &source(uint32_t slot) const {
        return *sources_.at(slot);
    }
    std::vector<uint32_t> unresolved(const Source &s) const {
        std::vector<uint32_t> out;
        for (auto other : s.neighbors) {
            const auto &p = pairs_.at({std::min(s.slot, other), std::max(s.slot, other)});
            if (!p.known) out.push_back(other);
        }
        return out;
    }
    template <class Visit>
    void links(const Source &s, Visit visit) const {
        if (!s.mesh) return;
        for (const auto &l : s.mesh->internal)
            visit(l.geometry, l.primitive, Reference{s.slot, s.generation, l.otherGeometry, l.otherPrimitive});
        for (auto other : s.neighbors) {
            const bool first = s.slot < other;
            const auto &p = pairs_.at({std::min(s.slot, other), std::max(s.slot, other)});
            if (!p.known) continue;
            const auto &target = *sources_[other];
            for (const auto &l : p.links)
                visit(first ? l.geometry : l.otherGeometry, first ? l.primitive : l.otherPrimitive,
                      Reference{other, target.generation, first ? l.otherGeometry : l.geometry,
                                first ? l.otherPrimitive : l.primitive});
        }
    }
};
} // namespace mcvr::medium
