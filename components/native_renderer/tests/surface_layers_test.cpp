#include "core/render/surface_layers.hpp"
#include <iostream>
#include <stdexcept>
using namespace mcvr;
using V = vk::VertexFormat::PBRVertex;
void require(bool v) {
    if (!v) throw std::runtime_error("ordered surface layer assertion");
}
std::vector<V> triangle(float z = 0) {
    std::vector<V> v(3);
    for (auto &x : v) {
        x.useTexture = 1;
        x.textureID = 1;
        x.useColorLayer = 1;
        x.colorLayer = glm::vec4(1);
        x.alphaMode = abi::ALPHA_COVERAGE;
    }
    v[0].pos = {0, 0, z};
    v[1].pos = {1, 0, z};
    v[2].pos = {0, 1, z};
    return v;
}
int main() {
    try {
        std::vector<uint32_t> faces(3, abi::FACE_BACK_BIT);
        std::vector<std::vector<V>> vertices{triangle(), triangle(), triangle(1)};
        std::vector<std::vector<uint32_t>> indices(3, {0, 1, 2}), policies(3, std::vector<uint32_t>(3, 0));
        std::vector<glm::vec4> colours{{0, 0, 1, 1}, {1, 0, 0, .5f}, {0, 1, 0, 1}};
        vertices[1][0].textureUV = {.25f, .75f};
        vertices[1][0].albedoEmission = 1;
        auto result = surface::composeSurfaceLayers(faces, vertices, indices, colours, policies);
        require(result.attachedTriangles == 1 && result.removedGeometries == std::vector<size_t>{1});
        const auto &stack = result.primitives[0][0];
        require(stack.size() == 2 && stack[0].sourceGeometry == 0 && stack[1].sourceGeometry == 1);
        require(stack[1].vertices[0].textureUV == glm::vec2(.25f, .75f));
        require(stack[1].vertices[0].colorLayer == glm::vec4(1, 0, 0, .5f));
        require(stack[0].vertices[0].albedoEmission == 0 && stack[1].vertices[0].albedoEmission == 1);
        require(colours[0] == glm::vec4(1) && (faces[0] & abi::FACE_SURFACE_LAYERS_BIT));
        surface::eraseLayerGeometries(result, faces, vertices, indices, colours, policies, result.primitives);
        require(vertices.size() == 2 && indices.size() == 2 && result.primitives.size() == 2);
        // Exact matching, same winding, independent owner, supported alpha only.
        uint64_t pairKey = 0;
        for (int mode = 0; mode < 6; ++mode) {
            faces.assign(2, abi::FACE_BACK_BIT);
            vertices = {triangle(), triangle()};
            indices.assign(2, {0, 1, 2});
            policies.assign(2, std::vector<uint32_t>(3, 0));
            colours.assign(2, glm::vec4(1));
            if (mode == 0) indices[1] = {2, 1, 0};
            if (mode == 1) vertices[1][0].pos.x = 1e-7f;
            if (mode == 2)
                for (auto &v : vertices[1]) v.alphaMode = abi::ALPHA_ADDITIVE;
            if (mode == 3) faces[0] = abi::FACE_BACK_BIT | abi::FACE_FRONT_BIT;
            if (mode == 4) indices[1] = {1, 2, 0};
            if (mode == 5) {
                colours[1] = {1, 1, 1, .2f};
                vertices[1][0].textureUV = {.7f, .8f};
            }
            auto x = surface::composeSurfaceLayers(faces, vertices, indices, colours, policies);
            require(x.attachedTriangles == (mode >= 4 ? 1 : 0));
            if (mode == 4) pairKey = x.topologyKey;
            if (mode == 5) require(x.topologyKey == pairKey);
        }
        // Three draws keep source order; a partial later geometry keeps its unmatched primitive.
        faces.assign(3, abi::FACE_BACK_BIT);
        vertices = {triangle(), triangle(), triangle()};
        indices.assign(3, {0, 1, 2});
        policies.assign(3, std::vector<uint32_t>(3, 0));
        colours = {glm::vec4(1), glm::vec4(.2f, .3f, .4f, .5f), glm::vec4(.7f, .8f, .9f, .25f)};
        auto extra = triangle(1);
        vertices[2].insert(vertices[2].end(), extra.begin(), extra.end());
        indices[2].insert(indices[2].end(), {3, 4, 5});
        policies[2].resize(6);
        auto multi = surface::composeSurfaceLayers(faces, vertices, indices, colours, policies);
        require(multi.attachedTriangles == 2 && multi.primitives[0][0].size() == 3);
        require(multi.primitives[0][0][2].sourceGeometry == 2);
        require(indices[2] == std::vector<uint32_t>({3, 4, 5}));
        require(multi.primitives[2].size() == 1 && multi.primitives[2][0].empty());
        require(faces::ModelRules(faces).needsAnyHit(faces[0]));
        bool rejected = false;
        try {
            surface::composeSurfaceLayers(faces, vertices, indices, colours, policies, std::array<uint8_t, 1>{1});
        } catch (const std::invalid_argument &) { rejected = true; }
        require(rejected);
        faces.assign(2, abi::FACE_BACK_BIT);
        vertices = {triangle(), triangle()};
        indices.assign(2, {0, 1, 2});
        policies.assign(2, std::vector<uint32_t>(3, 0));
        colours.assign(2, glm::vec4(.2f, .3f, .4f, .5f));
        auto custom = triangle(1);
        for (auto &v : custom) v.useColorLayer = 2;
        vertices[0].insert(vertices[0].end(), custom.begin(), custom.end());
        indices[0].insert(indices[0].end(), {3, 4, 5});
        policies[0].resize(6);
        auto mixed = surface::composeSurfaceLayers(faces, vertices, indices, colours, policies);
        require(mixed.attachedTriangles == 0 && vertices[0][3].useColorLayer == 2);
        require(colours[0] == glm::vec4(.2f, .3f, .4f, .5f));
        faces.assign(2, abi::FACE_BACK_BIT);
        vertices = {triangle(), triangle()};
        indices.assign(2, {0, 1, 2});
        policies.assign(2, std::vector<uint32_t>(3, 0));
        colours.assign(2, glm::vec4(.2f, .3f, .4f, .5f));
        for (auto &geometry : vertices)
            for (auto &v : geometry) v.useOverlay = 1;
        auto overlay = surface::composeSurfaceLayers(faces, vertices, indices, colours, policies);
        require(overlay.attachedTriangles == 0 && colours[0] == glm::vec4(.2f, .3f, .4f, .5f));
        // Never compose across a coincident unsupported operation in draw order.
        faces.assign(3, abi::FACE_BACK_BIT);
        vertices = {triangle(), triangle(), triangle()};
        indices.assign(3, {0, 1, 2});
        policies.assign(3, std::vector<uint32_t>(3, 0));
        colours.assign(3, glm::vec4(1));
        for (auto &v : vertices[1]) v.alphaMode = abi::ALPHA_ADDITIVE;
        auto barrier = surface::composeSurfaceLayers(faces, vertices, indices, colours, policies);
        require(barrier.attachedTriangles == 0 && indices[0].size() == 3 && indices[1].size() == 3 &&
                indices[2].size() == 3);
        for (auto &v : vertices[1]) v.alphaMode = abi::ALPHA_COVERAGE;
        auto depthBarrier =
            surface::composeSurfaceLayers(faces, vertices, indices, colours, policies, std::array<uint8_t, 3>{1, 0, 1});
        require(depthBarrier.attachedTriangles == 0);
        std::cout << "surface stack: ordered partial mesh, own UV/RGBA/emission, reverse, unsupported, history PASS\n";
        return 0;
    } catch (const std::exception &e) {
        std::cerr << e.what() << "\n";
        return 1;
    }
}
