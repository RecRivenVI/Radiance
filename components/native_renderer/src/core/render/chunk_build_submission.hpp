#pragma once

#include "core/diagnostics/frame_profile.hpp"
#include "core/render/external_chunk_handle.hpp"
#include <algorithm>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <utility>
#include <vector>

namespace mcvr::chunkInput {
template <typename Owner>
struct Admission {
    uint32_t slot, generation;
    int64_t version;
    // A weak identity rejects reset/reuse even when the handle table starts at generation zero again.
    // It does not keep the old owner's GPU resources alive during CPU preparation or shutdown.
    std::weak_ptr<Owner> owner;
};

template <typename Owner, typename Prepare, typename Publish>
bool submit(std::recursive_mutex &mutex,
            const ExternalChunkHandleTable &handles,
            std::vector<std::shared_ptr<Owner>> &owners,
            int64_t handle,
            int64_t requestedVersion,
            Prepare &&prepare,
            Publish &&publish) {
    std::optional<Admission<Owner>> admission;
    {
        std::unique_lock lock(mutex, std::defer_lock);
        {
            profile::Scope wait("chunk-input-resolve-lock-wait");
            lock.lock();
        }
        auto slot = handles.resolve(handle);
        if (!slot || *slot >= owners.size() || !owners[*slot]) return false;
        auto &owner = *owners[*slot];
        if (requestedVersion >= 0 && requestedVersion < owner.desiredVersion) return false;
        if (requestedVersion < 0 && owner.latestVersion == std::numeric_limits<int64_t>::max())
            throw std::overflow_error("Chunk mesh revision exhausted");
        const int64_t version = requestedVersion >= 0 ? requestedVersion : owner.latestVersion++;
        owner.desiredVersion = std::max(owner.desiredVersion, version);
        admission.emplace(Admission<Owner>{*slot, handles.generation(*slot), version, owners[*slot]});
    }

    // This owns its copied input. It must not create GPU resources or access mutable scene state.
    auto prepared = std::forward<Prepare>(prepare)(*admission);

    std::unique_lock lock(mutex, std::defer_lock);
    {
        profile::Scope wait("chunk-input-publish-lock-wait");
        lock.lock();
    }
    profile::Scope held("chunk-input-publish-lock-held");
    const auto slot = admission->slot;
    if (!handles.isCurrent(slot, admission->generation) || slot >= owners.size() || !owners[slot] ||
        admission->owner.lock() != owners[slot] || admission->version != owners[slot]->desiredVersion)
        return false;
    std::forward<Publish>(publish)(*admission, std::move(prepared));
    return true;
}
} // namespace mcvr::chunkInput
