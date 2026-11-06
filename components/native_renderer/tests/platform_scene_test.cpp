#include "core/render/scene_instances.hpp"
#include "core/render/effect_plan.hpp"
#include <memory>
#include <iostream>
int main() {
    mcvr::scene::InstanceStore<std::shared_ptr<int>> scene;
    auto owner = std::make_shared<int>(1);
    std::weak_ptr<int> weak = owner;
    scene.put(17, owner);
    owner.reset();
    if (weak.expired() || *scene.at(17) != 1) return 1;
    // An empty frame selection does not mutate the scene's owner set.
    if (scene.size() != 1) return 2;
    scene.put(17, std::make_shared<int>(2));
    if (!weak.expired() || *scene.at(17) != 2) return 3;
    scene.remove(17);
    if (scene.size()) return 4;
    // Retiring residency must transfer the selected version's ownership to this frame.
    // The next frame may reuse the identity, without replacing this frame's snapshot.
    auto selectedOwner = std::make_shared<int>(41);
    std::weak_ptr<int> selectedWeak = selectedOwner;
    scene.put(41, selectedOwner);
    selectedOwner.reset();
    auto sampled = scene.remove(41);
    if (!sampled || scene.contains(41) || selectedWeak.expired() || **sampled != 41) return 11;
    if (scene.remove(41)) return 12;
    try {
        scene.at(41);
        return 16;
    } catch (const std::out_of_range &) {}
    scene.put(41, std::make_shared<int>(42));
    if (*scene.at(41) != 42 || **sampled != 41) return 13;
    auto inFlight = *sampled;
    sampled.reset();
    if (selectedWeak.expired()) return 14;
    inFlight.reset();
    if (!selectedWeak.expired()) return 15;
    mcvr::scene::ResourcePins<int> pins(16);
    if (!pins.pin(1, 12) || !pins.pin(1, 12) || pins.pin(2, 8) || pins.bytes() != 12) return 7;
    pins.unpin(1);
    if (pins.bytes() != 12) return 8;
    pins.unpin(1);
    if (pins.bytes() != 0 || !pins.pin(2, 8)) return 9;
    {
        // A retained group stays selected without per-frame input, follows replaced versions
        // and never keeps a removed version alive.
        mcvr::scene::InstanceStore<int> store;
        mcvr::scene::RetainedSelections<int> retained;
        const auto sum = [&] {
            int total = 0;
            const size_t count = retained.each(store, [&](const int &value) { total += value; });
            return std::pair<size_t, int>{count, total};
        };
        const auto drop = [&](uint64_t id) {
            if (store.remove(id)) retained.stale(id);
        };
        const auto accept = [&](uint64_t id, int value) {
            drop(id);
            store.put(id, value);
            retained.stale(id);
        };
        for (uint64_t id = 1; id <= 6; ++id) accept(id, int(id));
        const uint64_t first[] = {1, 2, 3}, second[] = {3, 4};
        retained.set(7, first);
        retained.set(8, second);
        if (retained.groups() != 2 || retained.members() != 5) return 20;
        if (sum() != std::pair<size_t, int>{5, 13} || sum() != std::pair<size_t, int>{5, 13}) return 21;
        // Unreferenced churn must not disturb resolved members; a replaced member is followed.
        accept(5, 50);
        accept(6, 60);
        accept(2, 20);
        if (sum() != std::pair<size_t, int>{5, 31}) return 22;
        // A removed member drops out of every group and returns once accepted again.
        drop(3);
        if (sum() != std::pair<size_t, int>{3, 25}) return 23;
        accept(3, 300);
        if (sum() != std::pair<size_t, int>{5, 625}) return 24;
        // Replacing a group releases its former members.
        const uint64_t replaced[] = {6};
        retained.set(7, replaced);
        drop(1);
        if (retained.members() != 3 || sum() != std::pair<size_t, int>{3, 364}) return 25;
        retained.set(8, {});
        retained.set(9, {});
        if (retained.groups() != 1 || sum() != std::pair<size_t, int>{1, 60}) return 26;
        store.clear();
        retained.staleAll();
        if (sum() != std::pair<size_t, int>{0, 0}) return 27;
        accept(6, 6);
        if (sum() != std::pair<size_t, int>{1, 6}) return 28;
        retained.clear();
        if (retained.groups() || retained.members() || sum().first) return 29;
        try {
            const uint64_t zero[] = {0};
            retained.set(1, zero);
            return 30;
        } catch (const std::invalid_argument &) {}
        if (retained.groups() || retained.members()) return 31;
    }
    for (auto type : {CREEPER, SPIDER, INVERT}) mcvr::effects::validate(mcvr::effects::entity(type));
    auto spider = mcvr::effects::entity(SPIDER);
    if (spider.passes.size() != 5 || spider.passes.back().inputs.size() != 3) return 5;
    auto broken = spider;
    std::swap(broken.passes[0], broken.passes[1]);
    bool rejected = false;
    try {
        mcvr::effects::validate(broken);
    } catch (const std::invalid_argument &) { rejected = true; }
    if (!rejected) return 6;
    mcvr::effects::validate(mcvr::effects::single(BLUR, 256));
    mcvr::effects::Plan composed{{{512, 1, {0}}, {513, 2, {1}}}, mcvr::effects::Stage::GuiPost};
    composed.targets = {{2, 2}, {1, 1}};
    composed.output = 2;
    mcvr::effects::validate(composed);
    composed.passes[0].inputs = {2};
    try {
        mcvr::effects::validate(composed);
        return 10;
    } catch (const std::invalid_argument &) {}
    std::cout << "Persistent owner/update/removal and real screen-effect dependency plans PASS\n";
}
