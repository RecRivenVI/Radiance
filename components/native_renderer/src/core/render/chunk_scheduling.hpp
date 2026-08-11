#pragma once
#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <limits>
#include <map>
#include <vector>
namespace mcvr::chunkScheduling {
using Clock = std::chrono::steady_clock;
inline constexpr auto backgroundWait = std::chrono::milliseconds(25);
inline constexpr auto oversizedWait = std::chrono::seconds(1);
inline constexpr uint64_t uploadBudget = 8ull * 1024 * 1024;
// Bounds queued GPU build inputs separately from the per-poll preparation budget.
// AS/scratch allocations have an additional bound from the existing in-flight fence count.
inline constexpr uint64_t inFlightInputBudget = 32ull * 1024 * 1024;
inline constexpr uint64_t pendingPublicationSections = 64;
inline uint32_t remainingSections(uint64_t selected, uint32_t maximum) {
    return uint32_t(
        std::min<uint64_t>(maximum, pendingPublicationSections - std::min(selected, pendingPublicationSections)));
}
inline constexpr auto preparationBudget = std::chrono::milliseconds(2);
// Background throughput is paced in real time, independently of the world FPS.
// Interaction can borrow credit; its cost remains charged to subsequent background work.
class UploadPacer {
    double credit_ = double(uploadBudget);
    Clock::time_point last_;

  public:
    explicit UploadPacer(Clock::time_point now) : last_(now) {}
    uint64_t available(Clock::time_point now) {
        credit_ =
            std::min(double(uploadBudget), credit_ + std::max(0.0, std::chrono::duration<double>(now - last_).count()) *
                                                         (double(uploadBudget) / 0.016));
        last_ = now;
        return uint64_t(std::max(0.0, credit_));
    }
    void consumed(uint64_t bytes, Clock::time_point now) {
        available(now);
        credit_ -= double(bytes);
    }
};
struct Key {
    int priority;
    Clock::time_point queued;
    double distance;
    uint64_t source = 0;
    bool valid = true;
};
inline double distanceRing(double squaredDistance) {
    return std::floor(std::sqrt(squaredDistance));
}
inline Key positionKey(int priority,
                       Clock::time_point queued,
                       const std::array<double, 3> &position,
                       const std::array<double, 3> &camera,
                       const std::array<double, 3> &halfExtents = {8, 8, 8},
                       uint64_t source = 0) {
    double distance = 0;
    for (size_t i = 0; i < 3; ++i) {
        const double reference = (std::floor(camera[i] / 16.0) + 0.5) * 16.0;
        const double d = std::max(0.0, std::abs(position[i] - reference) - halfExtents[i]) / 16.0;
        distance += d * d;
    }
    return {priority, queued, distance, source};
}
inline bool before(const Key &a, const Key &b, Clock::time_point) {
    if (a.priority != b.priority) return a.priority > b.priority;
    return a.distance != b.distance ? a.distance < b.distance : a.queued < b.queued;
}
inline bool ready(bool interactive, uint32_t count, uint32_t maximum, Clock::duration waited) {
    return interactive || count >= maximum || waited >= backgroundWait;
}
inline bool withinBudget(uint32_t admitted, uint64_t bytes, Clock::duration elapsed) {
    return admitted == 0 || (bytes < uploadBudget && elapsed < preparationBudget);
}
inline bool canAdmit(uint64_t inFlightBytes, uint64_t bytes) {
    // A single oversized section can progress only when no other batch is in flight.
    return inFlightBytes == 0 || (inFlightBytes < inFlightInputBudget && bytes <= inFlightInputBudget - inFlightBytes);
}

/** Bounded per-source candidates; nearby sources take turns through every admission stage. */
template <class Range, class KeyOf, class BytesOf>
std::vector<int64_t> selectBatch(const Range &queue,
                                 size_t maximum,
                                 Clock::time_point now,
                                 KeyOf key,
                                 BytesOf bytesOf,
                                 uint64_t inFlightBytes,
                                 uint64_t admissionSequence = 0,
                                 uint64_t byteBudget = uploadBudget,
                                 uint64_t *sourceCursor = nullptr) {
    std::vector<int64_t> interactive, selected;
    struct Lane {
        std::vector<int64_t> values;
        size_t next = 0;
    };
    std::map<uint64_t, Lane> background;
    selected.reserve(maximum);
    if (maximum == 0) return selected;
    double nearestRing = std::numeric_limits<double>::infinity();
    for (int64_t id : queue)
        if (key(id).valid && key(id).priority <= 0) nearestRing = std::min(nearestRing, distanceRing(key(id).distance));
    int64_t oversized = -1;
    for (int64_t id : queue)
        if (key(id).valid && key(id).priority <= 0 && distanceRing(key(id).distance) == nearestRing &&
            now - key(id).queued >= oversizedWait && bytesOf(id) > inFlightInputBudget &&
            (oversized < 0 || key(id).queued < key(oversized).queued))
            oversized = id;
    if (oversized >= 0) {
        // Drain only for a nearby oversized owner; a distant one cannot jump the loading frontier.
        if (inFlightBytes == 0 && byteBudget >= uploadBudget) {
            selected.push_back(oversized);
            if (sourceCursor) *sourceCursor = key(oversized).source;
        }
        return selected;
    }
    auto less = [&](int64_t a, int64_t b) { return before(key(a), key(b), now); };
    auto insert = [&](auto &lane, int64_t id) {
        if (lane.size() < maximum) {
            lane.push_back(id);
            std::push_heap(lane.begin(), lane.end(), less);
        } else if (less(id, lane.front())) {
            std::pop_heap(lane.begin(), lane.end(), less);
            lane.back() = id;
            std::push_heap(lane.begin(), lane.end(), less);
        }
    };
    auto fits = [&](int64_t id, uint64_t bytes) {
        const uint64_t next = bytesOf(id);
        return (next <= byteBudget - std::min(bytes, byteBudget) ||
                (selected.empty() && inFlightBytes == 0 && byteBudget >= uploadBudget)) &&
               canAdmit(inFlightBytes, bytes + next);
    };
    for (int64_t id : queue) {
        if (!key(id).valid || !fits(id, 0)) continue;
        if (key(id).priority > 0)
            insert(interactive, id);
        else
            insert(background[key(id).source].values, id);
    }
    std::sort_heap(interactive.begin(), interactive.end(), less);
    for (auto &[source, lane] : background) std::sort_heap(lane.values.begin(), lane.values.end(), less);
    uint64_t cursor = sourceCursor ? *sourceCursor : std::numeric_limits<uint64_t>::max();
    uint64_t bytes = 0;
    size_t nextInteractive = 0;
    while (selected.size() < maximum) {
        while (nextInteractive < interactive.size() && !fits(interactive[nextInteractive], bytes)) ++nextInteractive;
        auto chosen = background.end(), wrapped = background.end();
        double ring = std::numeric_limits<double>::infinity();
        for (auto lane = background.begin(); lane != background.end(); ++lane) {
            auto &candidates = lane->second;
            while (candidates.next < candidates.values.size() && !fits(candidates.values[candidates.next], bytes))
                ++candidates.next;
            if (candidates.next == candidates.values.size()) continue;
            const double band = distanceRing(key(candidates.values[candidates.next]).distance);
            if (band < ring) {
                ring = band;
                chosen = wrapped = background.end();
            }
            if (band != ring) continue;
            if (wrapped == background.end()) wrapped = lane;
            if (lane->first > cursor && chosen == background.end()) chosen = lane;
        }
        if (chosen == background.end()) chosen = wrapped;
        const bool backgroundTurn = (admissionSequence + selected.size()) % 4 == 3;
        int64_t id;
        if (nextInteractive < interactive.size() && (!backgroundTurn || chosen == background.end()))
            id = interactive[nextInteractive++];
        else if (chosen != background.end()) {
            id = chosen->second.values[chosen->second.next++];
            cursor = chosen->first;
            if (sourceCursor) *sourceCursor = cursor;
        } else
            break;
        bytes += bytesOf(id);
        selected.push_back(id);
    }
    return selected;
}
} // namespace mcvr::chunkScheduling
