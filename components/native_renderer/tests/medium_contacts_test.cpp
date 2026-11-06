#include "core/render/medium_contact_publication.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <chrono>
#include <thread>
#include <stdexcept>

#define REQUIRE(value)                                                                                                 \
    do {                                                                                                               \
        if (!(value)) throw std::runtime_error(#value);                                                                \
    } while (false)
using namespace mcvr::medium;
std::shared_ptr<Mesh> triangle(uint32_t geometry = 0, double z = 0) {
    auto mesh = std::make_shared<Mesh>();
    mesh->primitiveCounts = {1};
    mesh->triangles.push_back({{glm::dvec3(0, 0, z), glm::dvec3(1, 0, z), glm::dvec3(0, 1, z)}, geometry, 0, false});
    mesh->build();
    return mesh;
}

void checkIncrementalDirectory() {
    struct Buffer {
        std::vector<std::byte> bytes;
        uint64_t bufferAddress() const {
            return reinterpret_cast<uintptr_t>(bytes.data());
        }
        size_t size() const {
            return bytes.size();
        }
    };
    using Publication = ContactPublication<Buffer>;
    auto mesh = triangle();
    auto unbounded = std::make_shared<Mesh>();
    unbounded->complete = false;
    Publication directory, full;
    auto upload = [](std::span<const std::byte> bytes) {
        return std::make_shared<Buffer>(Buffer{{bytes.begin(), bytes.end()}});
    };
    auto keep = [](const auto &) {};
    auto phase = [](const char *) {};
    auto patch = [&](std::span<const Filter> rows, std::span<const Publication::Range> ranges, const auto &old) {
        auto next = std::make_shared<Buffer>();
        next->bytes.resize(rows.size_bytes());
        if (old) std::copy_n(old->bytes.begin(), std::min(old->size(), next->size()), next->bytes.begin());
        auto bytes = std::as_bytes(rows);
        for (auto [first, last] : ranges) {
            first *= sizeof(Filter);
            last *= sizeof(Filter);
            std::copy(bytes.begin() + first, bytes.begin() + last, next->bytes.begin() + first);
        }
        return next;
    };
    auto run = [&](uint32_t count, int first, int second, int unknown = -1) {
        auto populate = [&](Publication &p, std::string_view mode) {
            p.begin(mode);
            auto add = [&](uint64_t owner, const auto &source, double x, int base) {
                if (base < 0) return;
                const auto pose = glm::translate(glm::dmat4(1), glm::dvec3(x, 0, 0));
                p.scene.add({0, owner, 0}, source, pose, pose, 0, true, uint32_t(base), 1);
            };
            add(1, mesh, 0, first);
            add(2, mesh, 8, second);
            add(3, unbounded, 16, unknown);
            p.publish(count, upload, keep, patch, phase);
            const auto bytes = std::as_bytes(std::span(p.directory));
            REQUIRE(p.directoryBuffer->size() >= bytes.size());
            REQUIRE(std::equal(bytes.begin(), bytes.end(), p.directoryBuffer->bytes.begin()));
        };
        populate(directory, "verify");
        populate(full, "0");
        const auto bytes = std::as_bytes(std::span(directory.directory));
        REQUIRE(full.directoryBuffer->size() == bytes.size());
        REQUIRE(std::equal(bytes.begin(), bytes.end(), full.directoryBuffer->bytes.begin()));
    };
    run(256, 100, 102);
    REQUIRE(directory.directoryRows == 256);
    run(256, 100, 102);
    REQUIRE(directory.directoryRows == 0);
    run(256, 102, 100);
    REQUIRE(directory.directoryRows == 2);
    REQUIRE(directory.uploadedBytes == 48 && directory.copiedBytes == 254 * sizeof(Filter));
    run(256, 102, 100, 1);
    REQUIRE(directory.directory[102].unknown == 1);
    run(256, 102, 100);
    REQUIRE(directory.directory[102].unknown == 0);
    run(12, -1, 10);
    run(8, -1, 4);
    run(32, 30, 4);
    run(32, -1, -1);
    run(32, -1, 8);

    auto staticOwner = std::make_shared<const int>(1);
    size_t calls = 0;
    const auto staticFrame = [&](const auto &owner, uint32_t base, uint64_t key) {
        directory.begin("verify");
        full.begin("0");
        for (auto *p : {&directory, &full}) {
            if (owner)
                p->staticSources(owner, base, [&](auto add) {
                    if (p == &directory) ++calls;
                    add({4, key, 0}, mesh, glm::dmat4(1), 0, 1);
                });
            p->publish(128, upload, keep, patch, phase);
        }
        REQUIRE(std::equal(directory.directoryBuffer->bytes.begin(), directory.directoryBuffer->bytes.end(),
                           full.directoryBuffer->bytes.begin()));
    };
    staticFrame(staticOwner, 10, 1);
    staticFrame(staticOwner, 10, 1);
    REQUIRE(directory.scene.lifetimeVisits == 0 && directory.directoryRows == 0 && calls == 1);
    staticFrame(staticOwner, 11, 1);
    REQUIRE(directory.scene.lifetimeVisits == 0 && directory.directoryRows == 2 && calls == 1);
    staticFrame(std::make_shared<const int>(2), 11, 2);
    REQUIRE(directory.scene.lifetimeVisits == 1 && calls == 2);
    staticFrame(std::shared_ptr<const int>{}, 11, 2);
    REQUIRE(directory.scene.lifetimeVisits == 1 && directory.directory[11].unknown == 1);
}

int main() {
    checkIncrementalDirectory();
    auto a = triangle(), b = triangle();
    REQUIRE(contacts(*a, *b, glm::dmat4(1)).size() == 1);
    REQUIRE(contacts(*a, *triangle(0, .000001), glm::dmat4(1)).empty());
    REQUIRE(contacts(*a, *b, glm::translate(glm::dmat4(1), glm::dvec3(.25, .25, 0))).size() == 1);
    REQUIRE(contacts(*a, *b, glm::translate(glm::dmat4(1), glm::dvec3(1, 0, 0))).empty());
    auto mesh = std::make_shared<Mesh>(*a);
    auto reverse = a->triangles.front();
    std::swap(reverse.points[1], reverse.points[2]);
    reverse.primitive = 1;
    mesh->triangles.push_back(reverse);
    mesh->primitiveCounts = {2};
    mesh->build();
    REQUIRE(contacts(*mesh, *mesh, glm::dmat4(1), true).size() == 2);
    auto diagonal = triangle();
    diagonal->triangles.front().sameMaterialSheet = true;
    diagonal->triangles.front().sheetPair = 7;
    auto opposite = diagonal->triangles.front();
    opposite.points = {glm::dvec3(1, 1, 0), glm::dvec3(1, 0, 0), glm::dvec3(0, 0, 0)};
    diagonal->triangles.push_back(opposite);
    diagonal->build();
    REQUIRE(contacts(*diagonal, *diagonal, glm::dmat4(1), true).empty());
    diagonal->triangles.back().sheetPair = 8;
    REQUIRE(contacts(*diagonal, *diagonal, glm::dmat4(1), true).size() == 2);
    for (auto &t : mesh->triangles) t.sameMaterialSheet = true;
    REQUIRE(contacts(*mesh, *mesh, glm::dmat4(1), true).empty());
    // A different material/geometry cannot disappear as an authored same-material sheet.
    mesh->triangles[1].geometry = 1;
    mesh->primitiveCounts = {1, 2};
    REQUIRE(contacts(*mesh, *mesh, glm::dmat4(1), true).size() == 2);
    const auto parent =
        glm::rotate(glm::translate(glm::dmat4(1), glm::dvec3(-30000000, 90, 30000000)), .67, glm::dvec3(0, 1, 0));
    const auto local = glm::translate(glm::dmat4(1), glm::dvec3(.125, .5, -.125));
    auto relative = relativePose(parent, parent * local);
    REQUIRE(glm::length(glm::dvec3(relative[3]) - glm::dvec3(local[3])) < 1e-8);
    const auto touching = parent * glm::translate(glm::dmat4(1), glm::dvec3(.25, .25, 0));
    REQUIRE(contacts(*a, *b, relativePose(parent, touching), false, poseUncertainty(parent, touching)).size() == 1);
    const auto farGap = parent * glm::translate(glm::dmat4(1), glm::dvec3(.25, .25, .000001));
    REQUIRE(contacts(*a, *b, relativePose(parent, farGap), false, poseUncertainty(parent, farGap)).empty());
    Scene scene;
    auto frame = [&](glm::dmat4 second, bool include = true, uint64_t space = 0, glm::dmat4 first = glm::dmat4(1)) {
        scene.begin();
        scene.add({0, 1, 0}, a, first, glm::dmat4(1), space, false, 0, 1);
        if (include) scene.add({0, 2, 0}, b, second, glm::dmat4(1), space, false, 1, 1);
        scene.finish();
    };
    frame(glm::translate(glm::dmat4(1), glm::dvec3(4, 0, 0)));
    REQUIRE(scene.pairs().empty()); // Independent dynamic glass, no external candidate.
    auto settle = [&](glm::dmat4 pose, uint64_t space = 0) {
        for (int i = 0; i < 200; ++i) {
            frame(pose, true, space, space ? pose : glm::dmat4(1));
            if (!scene.pairs().empty() && scene.pairs().begin()->second.known) return;
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
        throw std::runtime_error("Contact worker did not publish");
    };
    settle(glm::dmat4(1));
    REQUIRE(scene.pairs().begin()->second.links.size() == 1);
    const auto originalB = b;
    b = triangle();
    b->triangles.front().points[2].z = .001;
    b->build();
    frame(glm::dmat4(1));
    REQUIRE(!scene.pairs().begin()->second.known); // Overlapping bounds do not preserve a stale content proof.
    settle(glm::dmat4(1));
    REQUIRE(scene.pairs().begin()->second.links.empty());
    b = originalB;
    settle(glm::dmat4(1));
    frame(glm::translate(glm::dmat4(1), glm::dvec3(0, 0, .001)));
    REQUIRE(scene.pairs().empty()); // Separation invalidates before the next draw.
    settle(glm::dmat4(1));
    frame(glm::dmat4(1), false);
    REQUIRE(scene.pairs().empty()); // Removal invalidates both sides, including pending jobs.
    scene.clear();
    settle(parent, 91);
    const auto revision = scene.pairs().begin()->second.revision;
    frame(glm::translate(glm::dmat4(1), glm::dvec3(500, 0, 0)) * parent, true, 91,
          glm::translate(glm::dmat4(1), glm::dvec3(500, 0, 0)) * parent);
    REQUIRE(scene.pairs().begin()->second.known);
    REQUIRE(scene.pairs().begin()->second.revision == revision); // Parent motion reuses local relationships.
    REQUIRE(scene.publicationChanges().empty());
    scene.clear();
    auto unknown = std::make_shared<Mesh>();
    unknown->complete = false;
    scene.begin();
    scene.add({0, 3, 0}, unknown, glm::dmat4(1), glm::dmat4(1), 0, false, 0, 1);
    scene.finish();
    REQUIRE(scene.unboundedUnknown()); // Not prepared is never proof of no contact.
    REQUIRE(scene.publicationChanges().size() == 1);
    scene.begin();
    scene.finish();
    REQUIRE(!scene.unboundedUnknown());
    REQUIRE(scene.publicationChanges().size() == 1);
    scene.clear();
    REQUIRE(scene.sources().empty());
    scene.begin();
    const auto slot = scene.add({4, 9, 0}, a, parent, parent, 0, true, 3, 1);
    const auto generation = scene.source(slot).generation;
    scene.finish();
    scene.begin();
    REQUIRE(scene.touch(slot, generation, 7));
    scene.finish();
    REQUIRE(scene.source(slot).geometryBase == 7);
    REQUIRE(scene.publicationChanges() == std::set<uint32_t>{slot});
    scene.begin();
    REQUIRE(scene.touch(slot, generation, 7));
    scene.finish();
    REQUIRE(scene.publicationChanges().empty());
    scene.begin();
    scene.finish(); // Missing from the next static snapshot means actual removal.
    scene.begin();
    scene.add({4, 10, 0}, b, parent, parent, 0, true, 11, 1);
    REQUIRE(!scene.touch(slot, generation, 99)); // Reused slots cannot revive an obsolete snapshot.
    scene.finish();
}
