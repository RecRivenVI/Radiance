#include "common/query_policy.hpp"
#include "core/render/water_clip.hpp"
#include <array>
#include <glm/gtc/matrix_transform.hpp>
#include <iostream>
#include <limits>
#include <stdexcept>

static void require(bool value) {
    if (!value) throw std::runtime_error("Query visibility contract failed");
}
template <class F>
static void rejected(F call) {
    bool failed = false;
    try {
        call();
    } catch (const std::exception &) { failed = true; }
    require(failed);
}
int main() {
    using namespace mcvr::query;
    for (bool firstPerson : {false, true}) {
        require(queryIncludes(queryWorld, queryDirect, firstPerson));
        require(queryIncludes(queryWorld, queryTransport, firstPerson));
        require(queryIncludes(queryParticle, queryDirect, firstPerson));
        require(queryIncludes(queryParticle, queryTransport, firstPerson));
        require(queryIncludes(querySelf, queryDirect, firstPerson) == !firstPerson);
        require(queryIncludes(querySelf, queryTransport, firstPerson));
        require(queryIncludes(queryReceiveOnly, queryDirect, firstPerson));
        require(!queryIncludes(queryReceiveOnly, queryTransport, firstPerson));
        require(!queryIncludes(queryOverlay, queryDirect, firstPerson));
        require(queryIncludes(queryOverlay, queryPriority, firstPerson));
        require(queryIncludes(queryOverlay, queryTransport, firstPerson));
        require(!queryIncludes(queryWorld, queryPriority, firstPerson));
        require((queryMask(queryTransport, firstPerson) & 128u) == 0u);
    }
    uint purpose = queryDirect;
    for (unsigned step = 0; step < 64; ++step) purpose = advanceQuery(purpose, 2);
    require(purpose == queryDirect);
    purpose = advanceQuery(purpose, 1);
    require(advanceQuery(purpose, 2) == queryTransport);
    require(advanceQuery(advanceQuery(queryDirect, 0), 2) == queryTransport);

    // Same producer helper used by Entity: source mesh -> local convex planes.
    const std::array<glm::vec3, 8> positions{
        {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}}};
    const std::array<uint32_t, 36> indices{
        {0, 1, 2, 2, 3, 0, 4, 7, 6, 6, 5, 4, 0, 4, 5, 5, 1, 0, 3, 2, 6, 6, 7, 3, 0, 3, 7, 7, 4, 0, 1, 5, 6, 6, 2, 1}};
    auto planes = mcvr::water::clipPlanes(positions, indices);
    require(planes.size() == 6);
    // Minecraft box faces duplicate corner vertices; normalOffset may displace
    // each face by a few float units. Use the real producer helper, not an AABB.
    std::vector<glm::vec3> faceVertices;
    std::vector<uint32_t> faceIndices;
    for (auto index : indices) {
        faceIndices.push_back(static_cast<uint32_t>(faceVertices.size()));
        faceVertices.push_back(positions[index]);
    }
    require(mcvr::water::clipPlanes(faceVertices, faceIndices).size() == 6);

    require(mcvr::water::contains(planes, {0, 0, 0}));
    require(!mcvr::water::contains(planes, {1.001f, 0, 0}));
    require(!mcvr::water::contains(planes, {0, 2, 0}));
    // Vertex extents emitted by the real BoatModel/ChestBoatModel adapter regression.
    // The old depth slab [0.375,0.5625] misses the source floating equilibrium.
    auto boat = positions;
    for (auto &p : boat) {
        p.x *= .875f;
        p.z *= .5f;
        p.y = p.y < 0 ? .1875f : .5625f;
    }
    auto interior = mcvr::water::clipPlanes(boat, indices);
    require(mcvr::water::contains(interior, {0, .65f * .5625f, 0}));
    require(!mcvr::water::contains(interior, {0, .1f, 0}));
    require(!mcvr::water::contains(interior, {2, .365625f, 0}));
    rejected([&] { mcvr::water::clipPlanes(positions, std::span(indices).first(33)); });
    rejected([&] { mcvr::water::clipPlanes(positions, std::span(indices).first(6)); });
    auto invalid = positions;
    invalid[0].x = std::numeric_limits<float>::quiet_NaN();
    rejected([&] { mcvr::water::clipPlanes(invalid, indices); });
    for (float scale : {-2.0f, 1.0f, 3.0f}) {
        auto transform = glm::translate(glm::mat4(1), glm::vec3(20, 4, -8));
        transform = glm::rotate(transform, 0.7f, glm::normalize(glm::vec3(1, 2, 3)));
        transform = glm::scale(transform, glm::vec3(scale, 1, 2));
        std::vector<glm::vec4> records;
        mcvr::water::appendVolume(records, planes, transform);
        require(std::bit_cast<uint32_t>(records[0].x) == 1);
        require(std::bit_cast<uint32_t>(records[1].x) == 6);
        glm::mat4 inverse(records[2], records[3], records[4], records[5]);
        auto point = inverse * transform * glm::vec4(0, 0, 0, 1);
        require(mcvr::water::contains(planes, glm::vec3(point)));
        mcvr::water::appendVolume(records, planes, transform);
        require(std::bit_cast<uint32_t>(records[0].x) == 2);
    }
    rejected([&] {
        std::vector<glm::vec4> out;
        mcvr::water::appendVolume(out, planes, glm::mat4(0));
    });
    std::cout
        << "Query channels, 64 refractions, irreversible transport, source convex water exclusion, transforms and invalid masks passed\n";
}
