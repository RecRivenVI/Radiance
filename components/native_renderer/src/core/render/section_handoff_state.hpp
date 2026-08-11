#pragma once

#include <cstdint>
#include <algorithm>
#include <map>
#include <memory>
#include <optional>
#include <set>

namespace mcvr::scene {
inline bool initialSectionTransferRequired(bool publishedGeometry, int64_t desired, int64_t settled, int64_t removal) {
    return publishedGeometry && removal > settled && desired >= removal;
}
// Empty is a completed publication too. Superseded work must never open the gate.
inline bool sectionReplacementReady(
    int64_t desired, int64_t settled, std::optional<int64_t> geometry, std::optional<int64_t> empty, bool removing) {
    return removing || settled == desired || geometry == desired || empty == desired;
}

// Membership belongs to each scope, even after scopes have joined one publication group.
// Cancelling one scope must not publish or release another scope's dependencies.
template <typename Section>
struct SectionHandoffState {
    std::map<uint64_t, std::set<int64_t>> scopes;
    std::set<uint64_t> sealed;
    std::map<int64_t, std::shared_ptr<Section>> sections;
    bool complete = false;

    bool allSealed() const {
        return !scopes.empty() && std::all_of(scopes.begin(), scopes.end(),
                                              [&](const auto &scope) { return sealed.contains(scope.first); });
    }

    template <typename Release>
    void removeSection(int64_t handle, Release &&release) {
        for (auto &[token, members] : scopes) members.erase(handle);
        auto found = sections.find(handle);
        if (found == sections.end()) return;
        auto owner = std::move(found->second);
        sections.erase(found);
        release(handle, owner);
    }

    template <typename Release>
    void cancelScope(uint64_t token, Release &&release) {
        auto found = scopes.find(token);
        if (found == scopes.end()) return;
        auto members = std::move(found->second);
        scopes.erase(found);
        sealed.erase(token);
        for (auto handle : members) {
            const bool shared = std::any_of(scopes.begin(), scopes.end(),
                                            [&](const auto &scope) { return scope.second.contains(handle); });
            if (!shared) removeSection(handle, release);
        }
    }
};
} // namespace mcvr::scene
