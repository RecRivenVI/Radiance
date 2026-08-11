#include "core/render/feature_resource_lifetime.hpp"
#include "core/render/scene_release_policy.hpp"
#include "core/render/frame_retention.hpp"
#include "core/render/section_handoff_state.hpp"
#include "core/vulkan/idle_descriptor_reset.hpp"

#include <map>
#include <memory>
#include <stdexcept>
#include <tuple>
#include <vector>

static void require(bool value, const char *message) {
    if (!value) throw std::runtime_error(message);
}
struct Key {
    uint32_t set, binding;
    bool operator<(const Key &other) const {
        return std::tie(set, binding) < std::tie(other.set, other.binding);
    }
};

int main() {
    using mcvr::scene::initialSectionTransferRequired;
    require(!initialSectionTransferRequired(false, 1, -1, 1), "initial load does not hold unseen world geometry");
    require(!initialSectionTransferRequired(true, 1, 1, 1), "unchanged world publication is not an assembly transfer");
    require(!initialSectionTransferRequired(true, 2, 1, -1), "neighbor refresh is not an outgoing content transfer");
    require(!initialSectionTransferRequired(true, 3, 2, 2), "already published removal does not hold a later refresh");
    require(initialSectionTransferRequired(true, 2, 1, 2), "real replacement retains the outgoing world publication");
    using mcvr::scene::sectionReplacementReady;
    require(!sectionReplacementReady(4, 3, 3, std::nullopt, false), "stale completed geometry cannot replace source");
    require(!sectionReplacementReady(4, 3, std::nullopt, 3, false), "stale empty publication cannot remove source");
    require(sectionReplacementReady(4, 3, 4, std::nullopt, false), "current GPU geometry opens publication gate");
    require(sectionReplacementReady(4, 3, std::nullopt, 4, false), "confirmed empty completes publication gate");
    require(sectionReplacementReady(4, 4, std::nullopt, std::nullopt, false), "unchanged published member is ready");
    require(!sectionReplacementReady(5, 4, 4, std::nullopt, false), "new source revision closes previous readiness");
    require(sectionReplacementReady(5, 4, std::nullopt, std::nullopt, true),
            "removed source needs no replacement build");

    mcvr::scene::SectionHandoffState<int> group;
    group.scopes = {{1, {10, 11}}, {2, {11, 12}}};
    group.sections = {
        {10, std::make_shared<int>(10)}, {11, std::make_shared<int>(11)}, {12, std::make_shared<int>(12)}};
    group.sealed.insert(1);
    require(!group.allSealed(), "joined unprepared scope keeps the group closed");
    std::vector<int64_t> released;
    auto release = [&](int64_t handle, const auto &owner) {
        require(owner && *owner == handle, "release keeps the exact section owner");
        released.push_back(handle);
    };
    group.cancelScope(1, release);
    require(released == std::vector<int64_t>{10}, "cancel releases only exclusively owned holds");
    require(!group.complete && !group.allSealed() && group.sections.size() == 2,
            "cancel does not complete or seal another scope");
    require(group.scopes.at(2).contains(11) && group.sections.contains(11),
            "shared member remains held by the surviving scope");
    group.cancelScope(1, release);
    require(released.size() == 1, "repeated cancellation does not release twice");
    group.removeSection(11, release);
    require(!group.complete && group.sections.size() == 1 && group.sections.contains(12),
            "relocation removes one dependency without publishing the remaining group");
    require(!group.scopes.at(2).contains(11), "relocated member is absent from all scopes");
    group.sealed.insert(2);
    require(group.allSealed() && !group.complete, "sealing still requires normal version readiness");
    group.cancelScope(2, release);
    require(group.scopes.empty() && group.sections.empty() && !group.allSealed() && !group.complete,
            "fully cancelled group is never a committed group");
    require(released == std::vector<int64_t>({10, 11, 12}), "every dependency releases once");
    // The actual DescriptorTable reset helper must replace the Vulkan set before
    // releasing its last scene owners, preserve unrelated sets, and tolerate reuse.
    using Resources = std::map<Key, std::vector<std::shared_ptr<void>>>;
    Resources resources;
    auto scene = std::make_shared<int>(1), global = std::make_shared<int>(2);
    std::weak_ptr<int> sceneOwner = scene, globalOwner = global;
    resources[{1, 0}] = {scene};
    resources[{1, 1}] = {scene};
    resources[{0, 0}] = {global};
    scene.reset();
    global.reset();
    VkDescriptorSet set = (VkDescriptorSet)(uintptr_t)1;
    int freeCalls = 0, allocateCalls = 0;
    const auto free = [&](VkDescriptorSet old) {
        require(old != VK_NULL_HANDLE && !sceneOwner.expired(), "scene owner lives through Vulkan free");
        ++freeCalls;
        return VK_SUCCESS;
    };
    const auto allocate = [&](VkDescriptorSet &replacement) {
        require(set == VK_NULL_HANDLE && !sceneOwner.expired(), "allocation precedes resource release");
        replacement = (VkDescriptorSet)(uintptr_t)2;
        ++allocateCalls;
        return VK_SUCCESS;
    };
    require(vk::detail::resetIdleDescriptorSet(1, set, resources, free, allocate) == VK_SUCCESS, "reset succeeds");
    require(sceneOwner.expired() && !globalOwner.expired() && resources.size() == 1, "only scene set releases");
    require(freeCalls == 1 && allocateCalls == 1 && set != VK_NULL_HANDLE, "one replacement");

    // A failed free leaves both the descriptor and every owner intact.
    resources[{1, 0}] = {std::make_shared<int>(3)};
    sceneOwner = std::static_pointer_cast<int>(resources[{1, 0}][0]);
    const auto failedFree = [](VkDescriptorSet) { return VK_ERROR_DEVICE_LOST; };
    require(vk::detail::resetIdleDescriptorSet(1, set, resources, failedFree, allocate) == VK_ERROR_DEVICE_LOST,
            "free failure propagates");
    require(set != VK_NULL_HANDLE && !sceneOwner.expired(), "failed free keeps ownership");
    const auto failedAllocate = [](VkDescriptorSet &) { return VK_ERROR_OUT_OF_POOL_MEMORY; };
    require(vk::detail::resetIdleDescriptorSet(1, set, resources, free, failedAllocate) == VK_ERROR_OUT_OF_POOL_MEMORY,
            "allocation failure propagates");
    require(set == VK_NULL_HANDLE && !sceneOwner.expired(), "failed replacement cannot be bound and keeps owners");
    const int previousFrees = freeCalls;
    require(vk::detail::resetIdleDescriptorSet(1, set, resources, free, allocate) == VK_SUCCESS, "retry allocation");
    require(freeCalls == previousFrees && sceneOwner.expired(), "retry does not double-free");
    require(vk::detail::resetIdleDescriptorSet(
                1, set, resources, [](VkDescriptorSet) { return VK_SUCCESS; },
                [](VkDescriptorSet &replacement) {
                    replacement = (VkDescriptorSet)(uintptr_t)3;
                    return VK_SUCCESS;
                }) == VK_SUCCESS,
            "repeated unload resets an empty scene set");

    // Runtime set 5 is another scene owner: an inactive context can pin its old
    // r32 neighborhood buffer even after the producer has switched to r16.
    auto obsoleteRuntime = std::make_shared<int>(32);
    std::weak_ptr<int> runtimeOwner = obsoleteRuntime;
    resources[{5, 14}] = {obsoleteRuntime};
    resources[{4, 14}] = {obsoleteRuntime}; // another module sharing the producer
    obsoleteRuntime.reset();
    require(!runtimeOwner.expired(), "inactive runtime descriptor retains the obsolete generation");
    VkDescriptorSet runtimeSet = (VkDescriptorSet)(uintptr_t)4;
    require(vk::detail::resetIdleDescriptorSet(
                5, runtimeSet, resources, [](VkDescriptorSet) { return VK_SUCCESS; },
                [](VkDescriptorSet &replacement) {
                    replacement = (VkDescriptorSet)(uintptr_t)5;
                    return VK_SUCCESS;
                }) == VK_SUCCESS,
            "runtime set resets at world unload");
    require(!runtimeOwner.expired(), "releasing one consumer cannot free a resource held by another module");
    require(vk::detail::resetIdleDescriptorSet(
                4, runtimeSet, resources, [](VkDescriptorSet) { return VK_SUCCESS; },
                [](VkDescriptorSet &replacement) {
                    replacement = (VkDescriptorSet)(uintptr_t)6;
                    return VK_SUCCESS;
                }) == VK_SUCCESS,
            "shared runtime consumer releases at the same idle boundary");
    require(runtimeOwner.expired() && !globalOwner.expired(), "all consumers release without dropping assets");

    mcvr::FeatureResourceLifetime feature;
    int sdkFreeCalls = 0;
    auto sdkFree = [&] {
        ++sdkFreeCalls;
        return true;
    };
    require(feature.release(sdkFree) && sdkFreeCalls == 0, "never-evaluated viewport does not call SDK free");
    feature.evaluated();
    require(!feature.release([] { return false; }) && feature.allocated(), "failed SDK free remains retryable");
    require(feature.release(sdkFree) && !feature.allocated(), "evaluated viewport frees once");
    require(feature.release(sdkFree) && sdkFreeCalls == 1, "repeat close is a no-op");
    feature.evaluated();
    bool callbackThrew = false;
    try {
        feature.release([]() -> bool { throw std::runtime_error("diagnostic/free callback failed"); });
    } catch (const std::runtime_error &) { callbackThrew = true; }
    require(callbackThrew && feature.allocated(), "throwing callback propagates and keeps resources retryable");
    require(feature.release(sdkFree) && sdkFreeCalls == 2, "re-entered world has a new resource lifecycle");

    const auto ordinary = mcvr::sceneReleasePolicy(false, true, true);
    require(ordinary.descriptors && ordinary.reconstruction, "diagnostic overrides cannot affect ordinary clients");
    const auto compare = mcvr::sceneReleasePolicy(true, false, true);
    require(compare.descriptors && !compare.reconstruction, "isolated SDK attribution keeps descriptor release");

    mcvr::FrameRetention<std::shared_ptr<int>> frames;
    frames.beginFrame(10, 9, 3);
    frames.retain(std::make_shared<int>(4));
    frames.clear();
    frames.beginFrame(10, 10, 3); // same active context, after GPU-idle unload
    auto gui = std::make_shared<int>(5);
    std::weak_ptr<int> guiOwner = gui;
    frames.retain(gui);
    gui.reset();
    frames.beginFrame(13, 9, 3);
    require(!guiOwner.expired(), "post-unload GUI work is not retired as serial 0");
    frames.beginFrame(14, 10, 3);
    require(guiOwner.expired(), "GUI work retires after its actual frame completes");
}
