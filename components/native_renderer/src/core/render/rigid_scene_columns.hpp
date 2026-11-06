#pragma once

#include "core/render/hit_groups.hpp"
#include "core/render/rigid_instances.hpp"
#include "core/render/scene_table.hpp"
#include "core/vulkan/vertex.hpp"
#include <algorithm>
#include <array>
#include <cstring>
#include <memory>
#include <vector>

struct RigidModelResource;

namespace mcvr {

// A published page cannot be edited while a recording context still holds it.
template <class T, size_t PageSize>
class RigidSceneColumn {
    using Page = std::array<T, PageSize>;
    std::vector<std::shared_ptr<Page>> pages_;
    std::shared_ptr<const SceneTable::Directory> directory_;
    size_t publishedRows_ = 0;
    bool dirty_ = false;

  public:
    void resize(size_t rows) {
        static auto empty = std::make_shared<Page>();
        pages_.resize((rows + PageSize - 1) / PageSize, empty);
    }

    void set(size_t row, const T &value) {
        auto &page = pages_[row / PageSize];
        auto index = row % PageSize;
        if (std::memcmp(&(*page)[index], &value, sizeof(T)) == 0) return;
        if (page.use_count() != 1) page = std::make_shared<Page>(*page);
        (*page)[index] = value;
        dirty_ = true;
    }

    const T &at(size_t row) const {
        return (*pages_.at(row / PageSize))[row % PageSize];
    }

    std::shared_ptr<const SceneTable::Directory> publish(size_t rows) {
        if (!rows) return {};
        if (directory_ && !dirty_ && rows == publishedRows_) return directory_;
        auto next = std::make_shared<SceneTable::Directory>();
        next->segments.reserve(pages_.size());
        for (const auto &page : pages_) {
            const auto count = std::min(rows, PageSize);
            next->add({std::as_bytes(std::span(*page).first(count)), page});
            rows -= count;
        }
        publishedRows_ = next->bytes / sizeof(T);
        dirty_ = false;
        directory_ = std::move(next);
        return directory_;
    }
};

struct RigidSceneColumns {
    using Publication = std::array<std::shared_ptr<const SceneTable::Directory>, 6>;
    struct Source {
        const RigidModelResource *model = nullptr;
        uint32_t record = 0;
        rigid::SurfaceParameters surface;
        glm::vec4 colour{1};
        bool indexHistory = false, positionHistory = false;
    };

    struct Pose {
        rigid::HistoryEntry entry;
        uint64_t frame = 0;
    };

    static constexpr uint32_t zeroHistorySlot = UINT32_MAX;

    std::vector<Source> sources;
    std::vector<hitgroups::Id> groups;
    std::array<RigidSceneColumn<uint64_t, 64>, 5> addresses;
    RigidSceneColumn<vk::VertexFormat::InstanceAppearance, 16> appearance;

    void beginPoses(size_t slots) {
        ++poseFrame_;
        poses_[poseFrame_ % 2].resize(slots);
    }

    const rigid::HistoryEntry *previousPose(uint32_t slot) const {
        const auto previous = (poseFrame_ - 1) % 2;
        const auto &poses = poses_[previous];
        const Pose *pose = slot == zeroHistorySlot ? &zeroPose_[previous] :
                           slot < poses.size()     ? &poses[slot] :
                                                     nullptr;
        return pose && pose->frame && pose->frame + 1 == poseFrame_ ? &pose->entry : nullptr;
    }

    void recordPose(uint32_t slot, const rigid::HistoryEntry &entry) {
        const auto frame = poseFrame_ % 2;
        auto &pose = slot == zeroHistorySlot ? zeroPose_[frame] : poses_[frame][slot];
        pose = {entry, poseFrame_};
    }

    void resize(size_t slots, size_t records, size_t groupCount) {
        sources.resize(slots);
        groups.resize(groupCount, hitgroups::shadow);
        for (auto &column : addresses) column.resize(records);
        appearance.resize(records);
    }

    void clear(size_t first, size_t group, size_t count) {
        std::fill_n(groups.begin() + group, count + 1, hitgroups::shadow);
        for (size_t i = first; i < first + count; ++i) {
            for (auto &column : addresses) column.set(i, 0);
            appearance.set(i, {});
        }
    }

    Publication publish(size_t n) {
        return {addresses[0].publish(n), addresses[1].publish(n), addresses[2].publish(n),
                addresses[3].publish(n), addresses[4].publish(n), appearance.publish(n)};
    }

  private:
    uint64_t poseFrame_ = 0;
    std::array<std::vector<Pose>, 2> poses_;
    std::array<Pose, 2> zeroPose_;
};

} // namespace mcvr
