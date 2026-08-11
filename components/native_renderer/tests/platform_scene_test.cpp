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
