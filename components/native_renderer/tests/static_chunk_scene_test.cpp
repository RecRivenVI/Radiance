#include "core/render/static_chunk_scene.hpp"
#include "core/render/scene_table.hpp"
#include <algorithm>
#include "core/render/frame_retention.hpp"
#include <iostream>
#include <map>
#include <cstring>

namespace vk {
class BLAS {};
} // namespace vk
namespace {
void check(bool value) {
    if (!value) throw std::runtime_error("static chunk scene regression");
}
struct Published {
    std::vector<uint64_t> indices{11, 12}, positions{21, 22}, materials{31, 32};
    std::vector<std::string> groups{"water", "block"};
    std::vector<uint32_t> flags{mcvr::faces::back, 0};
    std::shared_ptr<vk::BLAS> blas = std::make_shared<vk::BLAS>();
    glm::ivec3 origin{30000000, -64, -30000000};
};
struct Fixture {
    std::map<uint32_t, std::shared_ptr<Published>> slots;
    uint64_t visits = 0;
    std::shared_ptr<mcvr::StaticChunkPage> build(const std::set<uint32_t> &active) {
        auto next = std::make_shared<mcvr::StaticChunkPage>();
        for (auto slot : active) {
            ++visits;
            auto &p = slots.at(slot);
            // Owner of the arrays, like ChunkRenderData in production. No cycle.
            auto metadata = std::make_shared<mcvr::ChunkSceneMetadata>(p, 2, p->indices, p->positions, p->materials,
                                                                       p->groups, p->flags);
            next->add(slot, p->origin, p->blas, metadata);
        }
        return next;
    }
};
void compare(const mcvr::StaticChunkScene &scene, const Fixture &fixture, uint32_t dynamicCount, glm::dvec3 camera) {
    std::vector<uint32_t> offsets(dynamicCount, 0), referenceOffsets = offsets;
    std::vector<mcvr::hitgroups::Id> groups(dynamicCount, mcvr::hitgroups::intern("prefix")), refGroups = groups;
    std::vector<uint64_t> indices(dynamicCount, 3), positions(dynamicCount, 4), materials(dynamicCount, 5);
    std::vector<uint64_t> lastI(dynamicCount, 6), lastP(dynamicCount, 7);
    auto refI = indices, refP = positions, refM = materials, refLI = lastI, refLP = lastP;
    std::vector<vk::VertexFormat::InstanceAppearance> appearance(dynamicCount), refA = appearance;
    scene.append(dynamicCount, offsets, groups, indices, positions, materials, lastI, lastP, appearance);
    uint32_t base = dynamicCount;
    scene.eachInstance([&](const auto &page, size_t i) {
        const auto &entry = page.instances[i];
        const auto &p = fixture.slots.at(entry.slot);
        mcvr::ChunkSceneMetadata uncached(p, 2, p->indices, p->positions, p->materials, p->groups, p->flags);
        referenceOffsets.push_back(base);
        uncached.append(false, refGroups, refI, refP, refM, refLI, refLP, refA);
        base += 2;
    });
    check(offsets == referenceOffsets && groups == refGroups && indices == refI && positions == refP &&
          materials == refM && lastI == refLI && lastP == refLP);
    check(appearance.size() == refA.size());
    check(!std::memcmp(appearance.data(), refA.data(), appearance.size() * sizeof(appearance[0])));
    // Production upload layout: dynamic prefix + immutable primary chunks + external suffix.
    auto compareUpload = [&]<class T>(const std::vector<T> &flat, std::vector<T> mcvr::StaticChunkPage::*member) {
        std::vector<T> changing(flat.begin(), flat.begin() + dynamicCount);
        auto expected = flat;
        if (!flat.empty()) {
            changing.push_back(flat.back());
            expected.push_back(flat.back());
        }
        const auto table = scene.table<T>(changing, dynamicCount, member);
        check(table.directory == scene.table<T>(changing, dynamicCount, member).directory && table.segments.empty());
        std::vector<std::byte> packed(table.size());
        table.write(packed);
        const auto reference = std::as_bytes(std::span(expected));
        check(std::equal(packed.begin(), packed.end(), reference.begin(), reference.end()));
    };
    compareUpload(refI, &mcvr::StaticChunkPage::indices);
    compareUpload(refP, &mcvr::StaticChunkPage::positions);
    compareUpload(refM, &mcvr::StaticChunkPage::materials);
    compareUpload(refLI, &mcvr::StaticChunkPage::lastIndices);
    compareUpload(refLP, &mcvr::StaticChunkPage::lastPositions);
    compareUpload(refA, &mcvr::StaticChunkPage::appearances);
    std::vector<const mcvr::StaticChunkPage::Instance *> ordered;
    scene.eachInstance([&](const auto &page, size_t i) { ordered.push_back(&page.instances[i]); });
    std::vector<glm::mat4> history;
    size_t count = 0;
    scene.appendInstances(camera, dynamicCount, 17, history,
                          [&](const auto &transform, auto index, auto group, auto flags, const auto &blas) {
                              const auto &entry = *ordered[count];
                              const auto &p = fixture.slots.at(entry.slot);
                              const auto d = glm::dvec3(p->origin) - camera;
                              check(index == dynamicCount + count && group == 17 + count * 3 && blas == p->blas);
                              check(transform.matrix[0][3] == float(d.x) && transform.matrix[1][3] == float(d.y) &&
                                    transform.matrix[2][3] == float(d.z));
                              check(flags == mcvr::faces::ModelRules(p->flags).instanceFlags(transform));
                              ++count;
                          });
    check(history.size() == scene.instanceCount);
    for (size_t i = 0; i < history.size(); ++i) {
        glm::dmat4 original(1);
        original[3] = glm::dvec4(glm::dvec3(ordered[i]->origin) - camera, 1);
        check(history[i] == glm::mat4(original));
    }
}
} // namespace
int main() {
    Fixture fixture;
    fixture.slots[100000] = std::make_shared<Published>(); // Capacity is not a traversal bound.
    fixture.slots[5] = std::make_shared<Published>();
    fixture.slots[5]->indices = {111, 112};
    auto publications = std::make_shared<mcvr::ChunkScenePublications>();
    mcvr::StaticChunkSceneCache cache;
    auto get = [&] { return cache.get(publications, [&](const auto &active) { return fixture.build(active); }); };
    auto empty = get();
    check(empty->instanceCount == 0 && fixture.visits == 0);
    publications->changed(100000, true);
    publications->changed(5, true);
    auto frame = get();
    check(frame->instanceCount == 2 && frame->pages.front()->instances[0].slot == 5 && fixture.visits == 2);
    for (uint32_t i = 0; i < 100; ++i) {
        check(get() == frame);
        compare(*frame, fixture, i % 9, {30000000.125 + i * 0.125, -8, -30000000.25});
    }
    check(fixture.visits == 2 && cache.builds() == 2);
    mcvr::FrameRetention<std::shared_ptr<const mcvr::StaticChunkScene>> retention;
    retention.beginFrame(1, 0, 3);
    retention.retain(frame);
    std::weak_ptr<Published> old = fixture.slots[5];
    fixture.slots[5] = std::make_shared<Published>();
    fixture.slots[5]->materials = {1001, 1002}; // Replacement / resource reload at same slot.
    publications->changed(5, true);
    auto updated = get();
    check(updated != frame && updated->pages.front()->materials[0] == 1001 && !old.expired());
    check(updated->pages.back() == frame->pages.back() && fixture.visits == 3 && cache.pageBuilds() == 3);
    compare(*updated, fixture, 11, {0, 1, 2});
    frame.reset();
    retention.beginFrame(2, 0, 3);
    check(!old.expired());
    retention.beginFrame(4, 1, 3);
    check(old.expired());
    publications->changed(5, false);
    auto removed = get();
    check(removed->instanceCount == 1 && removed->pages.front()->instances[0].slot == 100000);
    publications->changed(100000, false); // Last geometry unloads, not a stale nonempty snapshot.
    check(get()->instanceCount == 0);
    publications->changed(5, true);
    try {
        cache.get(publications, [](const auto &) -> std::shared_ptr<mcvr::StaticChunkPage> { throw std::bad_alloc(); });
        check(false);
    } catch (const std::bad_alloc &) {}
    check(get()->instanceCount == 1); // Failed refresh does not acknowledge the revision.
    auto oldPublications = publications;
    publications = std::make_shared<mcvr::ChunkScenePublications>(); // World reset / identity ABA.
    check(get()->instanceCount == 0);
    oldPublications->changed(5, true); // An old world cannot dirty the current world.
    auto reset = get();
    check(get() == reset && reset->instanceCount == 0);
    cache.reset();
    check(get() != reset);
    std::cout
        << "static publication, suffix rebasing, camera/history, replacement, failure and frame retirement passed\n";
}
