#include "core/render/entity_conversion.hpp"
#include "core/render/emissive_eye_composition.hpp"

#include <limits>
#include <memory>
#include <stdexcept>
#include <type_traits>
#include <iostream>

namespace {
void require(bool value, const char *message) {
    if (!value) throw std::runtime_error(message);
}

template <typename Function>
void requireThrows(Function &&function, const char *message) {
    try {
        function();
    } catch (const std::exception &) { return; }
    throw std::runtime_error(message);
}

mcvr::EntityConvertJob job(uint32_t source, uint32_t output) {
    mcvr::EntityConvertJob value{};
    value.source = source;
    value.indexSource = source + 32;
    value.vertexOffset = output;
    value.indexOffset = output * 2;
    value.vertexCount = 64;
    value.indexCount = 96;
    return value;
}
} // namespace

int main() {
    try {
        mcvr::validateEntityInputVersion(mcvr::abi::ENTITY_INPUT_VERSION, true);
        mcvr::validateEntityInputVersion(0, false);
        requireThrows([] { mcvr::validateEntityInputVersion(0, true); }, "direct input requires metadata");
        requireThrows([] { mcvr::validateEntityInputVersion(1, true); }, "old source ABI cannot be accepted");
        requireThrows([] { mcvr::validateEntityInputVersion(99, false); }, "unknown source ABI cannot be accepted");
        requireThrows([] { mcvr::validateEntityInputVersion(2, true); }, "pre-emission source ABI was accepted");
        // Exercise the production pairing and column-erasure functions with real packed vertices.
        using V = vk::VertexFormat::PBRVertex;
        std::vector<V> base(3);
        for (size_t i = 0; i < 3; ++i) {
            base[i].pos = glm::vec3(float(i), float(i % 2), 0.0f);
            base[i].textureUV = glm::vec2(float(i), 0.25f);
            base[i].useTexture = 1;
            base[i].textureID = 7;
        }
        auto eye = base;
        for (auto &v : eye) v.textureID = 42;
        std::vector<std::vector<V>> meshes{base, eye, base};
        std::vector<std::vector<uint32_t>> indices(3, std::vector<uint32_t>{0, 1, 2});
        std::vector<uint32_t> faces{0, mcvr::faces::back, mcvr::faces::front},
            policies{0, mcvr::abi::SOURCE_EMISSION_EYE | mcvr::abi::SOURCE_EMISSION_NO_EYE_BASE, 0};
        auto plan = mcvr::surface::eyeAttachments<V>(faces, policies, meshes, indices);
        require(plan.size() == 1 && plan[0].base == 0 && plan[0].overlay == 1 &&
                    plan[0].texture == (42 | mcvr::abi::EYE_CULL_BACK_BIT),
                "typed eye pairing mismatch");
        auto missingFace = faces;
        missingFace[0] = mcvr::faces::back;
        missingFace[1] = mcvr::faces::front;
        missingFace[2] = mcvr::faces::back;
        require(mcvr::surface::eyeAttachments<V>(missingFace, policies, meshes, indices).empty(),
                "base which hides the overlay side was paired");
        auto wrongFaces = faces;
        wrongFaces[1] = 4;
        auto wrongMeshes = meshes;
        wrongMeshes[1][0].textureUV.x += 0.1f;
        require(mcvr::surface::eyeAttachments<V>(wrongFaces, policies, wrongMeshes, indices).empty(),
                "unrelated surface paired");
        require(mcvr::surface::eyeAttachments<V>(faces, policies, wrongMeshes, indices).empty(), "different UV paired");
        wrongMeshes = meshes;
        wrongMeshes[1][0].pos.z += 0.1f;
        require(mcvr::surface::eyeAttachments<V>(faces, policies, wrongMeshes, indices).empty(),
                "different position paired");
        wrongMeshes = meshes;
        wrongMeshes[1][0].textureID = 99;
        require(mcvr::surface::eyeAttachments<V>(faces, policies, wrongMeshes, indices).empty(),
                "nonuniform eye texture paired");
        auto fractionalBase = meshes;
        for (auto &vertex : fractionalBase[0]) vertex.alphaMode = mcvr::abi::ALPHA_COVERAGE;
        require(mcvr::surface::eyeAttachments<V>(faces, policies, fractionalBase, indices).empty(),
                "different facing broadened composition to a fractional base");
        require(mcvr::surface::eyeTexture(42, mcvr::faces::back | mcvr::faces::clockwise) ==
                    (42 | mcvr::abi::EYE_CULL_FRONT_BIT),
                "clockwise coating lost source facing");
        require(mcvr::surface::eyeTexture(4095, 0) == 4095, "last texture descriptor truncated");
        requireThrows([] { mcvr::surface::eyeTexture(4096, 0); }, "unbounded overlay descriptor accepted");
        auto otherOwnership = faces;
        otherOwnership[1] |= 1u << 10u;
        require(mcvr::surface::eyeAttachments<V>(otherOwnership, policies, meshes, indices).empty(),
                "unrelated ownership was discarded during eye composition");
        auto ordinary = policies;
        ordinary[1] = 0;
        require(mcvr::surface::eyeAttachments<V>(faces, ordinary, meshes, indices).empty(),
                "unmarked layer paired by geometry alone");
        auto noBase = policies;
        noBase[0] = mcvr::abi::SOURCE_EMISSION_NO_EYE_BASE;
        require(mcvr::surface::eyeAttachments<V>(faces, noBase, meshes, indices).empty(), "excluded base paired");
        auto duplicatedMeshes = meshes;
        duplicatedMeshes.push_back(eye);
        auto duplicatedIndices = indices;
        duplicatedIndices.push_back(indices[1]);
        auto duplicatedFaces = faces;
        duplicatedFaces.push_back(mcvr::faces::back);
        auto duplicatedPolicies = policies;
        duplicatedPolicies.push_back(policies[1]);
        require(
            mcvr::surface::eyeAttachments<V>(duplicatedFaces, duplicatedPolicies, duplicatedMeshes, duplicatedIndices)
                    .size() == 1,
            "second overlay overwrote owner");
        std::vector<glm::vec4> colours{glm::vec4(.2f, .3f, .4f, .5f), glm::vec4(.7f, .8f, .9f, .1f), glm::vec4(1.0f)},
            eyeColours;
        std::vector<uint32_t> textures;
        const auto originalColours = colours;
        requireThrows(
            [&] {
                mcvr::surface::applyEyeAttachments(std::vector<mcvr::surface::EyeAttachment>{{0, 99, 42}}, colours,
                                                   eyeColours, textures, glm::vec4(1.0f), faces, policies, meshes,
                                                   indices);
            },
            "invalid plan accepted");
        require(colours == originalColours && textures.empty() && eyeColours.empty(), "invalid plan mutated owners");
        std::vector<std::string> owners{"base", "overlay", "unrelated"};
        mcvr::surface::applyEyeAttachments(plan, colours, eyeColours, textures, glm::vec4(1.0f), faces, policies,
                                           meshes, indices, owners);
        require(meshes.size() == 2 && indices.size() == 2 && colours.size() == 2 && faces.size() == 2 &&
                    policies.size() == 2 && owners[1] == "unrelated",
                "eye erasure misaligned columns");
        require(colours[0] == originalColours[0] && eyeColours[0] == originalColours[1] &&
                    textures[0] == (42 | mcvr::abi::EYE_CULL_BACK_BIT) && textures[1] == 0,
                "independent draw states lost during composition");
        static_assert(!std::is_copy_constructible_v<mcvr::EntityDirectInputBatch>);
        static_assert(!std::is_move_constructible_v<mcvr::EntityDirectInputBatch>);

        constexpr size_t stride = sizeof(vk::VertexFormat::PBRVertex);
        require(mcvr::checkedEntitySourceBytes(3, stride, 3 * stride) == 3 * stride,
                "v1 source byte count must match the declared vertex stride");
        requireThrows([&] { mcvr::checkedEntitySourceBytes(3, stride, 3 * stride - 1); },
                      "short source byte count was accepted");
        requireThrows([&] { mcvr::checkedEntitySourceBytes(3, stride, 3 * stride + 1); },
                      "long source byte count was accepted");
        requireThrows([&] { mcvr::validateEntitySourceAddress(nullptr, stride); },
                      "null nonempty source address was accepted");
        mcvr::validateEntitySourceAddress(nullptr, 0);

        mcvr::EntityDirectInputLayout layout(4096);
        require(layout.append(512) == 0, "first direct source offset mismatch");
        require(layout.append(768) == 512, "second direct source offset mismatch");
        require(layout.totalBytes() == 1280 && layout.withinLimit(), "direct input layout total mismatch");
        mcvr::EntityDirectInputLayout bounded(1024);
        require(bounded.append(768) == 0, "bounded layout first offset mismatch");
        require(bounded.append(512) == 768 && !bounded.withinLimit(),
                "over-budget direct layout did not select the copy fallback");
        mcvr::EntityDirectInputLayout overflowing(std::numeric_limits<size_t>::max());
        overflowing.append(std::numeric_limits<size_t>::max());
        requireThrows([&] { overflowing.append(1); }, "overflowing direct layout was accepted");
        require(mcvr::entityWordCount(512) == 128, "word offset conversion mismatch");
        requireThrows([&] { mcvr::entityWordCount(3); }, "unaligned word offset was accepted");

        require(mcvr::EntityDirectInputBudget::reservedBytes() == 0,
                "direct input test process started with a leaked reservation");
        require(mcvr::EntityDirectInputBudget::tryReserve(mcvr::maxEntityDirectInputBytes),
                "full direct input budget reservation failed");
        require(!mcvr::EntityDirectInputBudget::tryReserve(1),
                "capacity fallback admitted an over-budget direct source");
        auto owner = std::make_shared<mcvr::EntityDirectInputBatch>();
        owner->byteCount = mcvr::maxEntityDirectInputBytes;
        owner->ownsBudgetReservation = true;
        require(mcvr::EntityDirectInputBudget::reservedBytes() == mcvr::maxEntityDirectInputBytes,
                "direct input owner did not retain its reservation");
        auto inFlight = owner;
        owner.reset();
        require(mcvr::EntityDirectInputBudget::reservedBytes() == mcvr::maxEntityDirectInputBytes,
                "submission release retired an owner still held by an in-flight conversion");
        inFlight.reset();
        require(mcvr::EntityDirectInputBudget::reservedBytes() == 0,
                "direct input owner did not release its reservation");
        bool reachedInjectedFailure = false;
        try {
            auto failed = std::make_shared<mcvr::EntityDirectInputBatch>();
            require(mcvr::EntityDirectInputBudget::tryReserve(4096), "failure fixture reserve failed");
            failed->byteCount = 4096;
            failed->ownsBudgetReservation = true;
            reachedInjectedFailure = true;
            throw std::runtime_error("injected source-fill failure before publication");
        } catch (const std::runtime_error &) {}
        require(reachedInjectedFailure, "source-fill failure fixture did not reach the injected point");
        require(mcvr::EntityDirectInputBudget::reservedBytes() == 0,
                "failed unpublished source retained its direct-input reservation");

        std::vector<mcvr::EntitySourceJobGroup> groups;
        const auto sourceA = mcvr::entitySourceJobGroupIndex(groups, 0xA1u);
        mcvr::appendEntityTiles(groups[sourceA].tiles, job(8, 0));
        const auto sourceB = mcvr::entitySourceJobGroupIndex(groups, 0xB2u);
        mcvr::appendEntityTiles(groups[sourceB].tiles, job(0, 64));
        const auto sourceAAgain = mcvr::entitySourceJobGroupIndex(groups, 0xA1u);
        mcvr::appendEntityTiles(groups[sourceAAgain].tiles, job(40, 128));
        require(groups.size() == 2 && sourceA == sourceAAgain,
                "shared submission input was split into duplicate source groups");
        require(groups[0].tiles.size() == 4 && groups[1].tiles.size() == 2, "source group job counts mismatch");
        require(groups[0].tiles[0].vertexOffset == 0 && groups[1].tiles[0].vertexOffset == 64 &&
                    groups[0].tiles[2].vertexOffset == 128,
                "grouping changed original global output offsets");
        require(groups[0].tiles[0].source == 8 && groups[0].tiles[2].source == 40,
                "grouping changed source-local word offsets");
        std::cout << "Input bounds, fallback, shared retirement, unwind and grouped output offsets passed\n";
    } catch (const std::exception &failure) {
        std::cerr << failure.what() << '\n';
        return 1;
    }
}
