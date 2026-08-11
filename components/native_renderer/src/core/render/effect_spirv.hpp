#pragma once
#include <spirv_reflect.h>
#include <span>
#include <array>
#include <stdexcept>
#include <string_view>

namespace mcvr::effects {
inline void validateSpirv(std::span<const uint32_t> words, uint32_t inputs) {
    if (inputs < 1 || inputs > 3 || words.size() < 5 || words.size_bytes() > 1024 * 1024)
        throw std::invalid_argument("Screen SPIR-V/input budget");
    SpvReflectShaderModule shader{};
    if (spvReflectCreateShaderModule(words.size_bytes(), words.data(), &shader) != SPV_REFLECT_RESULT_SUCCESS)
        throw std::invalid_argument("Invalid screen SPIR-V module");
    struct Cleanup {
        SpvReflectShaderModule *value;
        ~Cleanup() {
            spvReflectDestroyShaderModule(value);
        }
    } cleanup{&shader};
    if (shader.entry_point_count != 1 || std::string_view(shader.entry_point_name) != "main" ||
        shader.shader_stage != SPV_REFLECT_SHADER_STAGE_FRAGMENT_BIT || shader.descriptor_binding_count != inputs)
        throw std::invalid_argument("Screen program requires one fragment main and declared sampler inputs");
    for (uint32_t i = 0; i < shader.capability_count; ++i) {
        auto value = shader.capabilities[i].value;
        if (value != SpvCapabilityShader && value != SpvCapabilityMatrix && value != SpvCapabilityImageQuery &&
            value != SpvCapabilityDerivativeControl)
            throw std::invalid_argument("Screen program has an unsupported SPIR-V capability");
    }
    std::array<bool, 3> found{};
    constexpr uint32_t bindings[]{1, 2, 4};
    for (uint32_t i = 0; i < shader.descriptor_binding_count; ++i) {
        const auto &binding = shader.descriptor_bindings[i];
        bool accepted = false;
        for (uint32_t n = 0; n < inputs; ++n)
            if (binding.set == 0 && binding.binding == bindings[n] && !found[n] && binding.count == 1 &&
                binding.descriptor_type == SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER &&
                binding.image.dim == SpvDim2D && !binding.image.arrayed && !binding.image.ms &&
                binding.image.depth != 1) {
                found[n] = true;
                accepted = true;
            }
        if (!accepted) throw std::invalid_argument("Screen program sampler ABI mismatch");
    }
    if (shader.push_constant_block_count > 1 ||
        (shader.push_constant_block_count &&
         (shader.push_constant_blocks[0].offset != 0 || shader.push_constant_blocks[0].size > 16)))
        throw std::invalid_argument("Screen push constants exceed vec4");
    for (uint32_t i = 0; i < shader.input_variable_count; ++i) {
        const auto &input = *shader.input_variables[i];
        if ((input.decoration_flags & SPV_REFLECT_DECORATION_BUILT_IN) || input.location != 0 ||
            input.format != SPV_REFLECT_FORMAT_R32G32_SFLOAT)
            throw std::invalid_argument("Screen fragment input must be uv at location 0");
    }
    if (shader.output_variable_count != 1 || shader.output_variables[0]->location != 0 ||
        shader.output_variables[0]->format != SPV_REFLECT_FORMAT_R32G32B32A32_SFLOAT)
        throw std::invalid_argument("Screen program output must be RGBA at location 0");
}
} // namespace mcvr::effects
