#include "core/render/effect_spirv.hpp"
#include <fstream>
#include <iostream>
#include <vector>
#include <functional>

int main(int argc, char **argv) {
    if (argc != 2) return 1;
    std::ifstream stream(argv[1], std::ios::binary | std::ios::ate);
    if (!stream) return 2;
    auto size = stream.tellg();
    std::vector<uint32_t> original(size_t(size) / 4);
    stream.seekg(0);
    stream.read(reinterpret_cast<char *>(original.data()), size);
    mcvr::effects::validateSpirv(original, 1);
    auto rejected = [](const auto &words, uint32_t inputs) {
        try {
            mcvr::effects::validateSpirv(words, inputs);
        } catch (const std::invalid_argument &) { return true; }
        return false;
    };
    if (!rejected(original, 2)) return 3;
    auto bad = original;
    bad[0] = 0;
    if (!rejected(bad, 1)) return 4;
    for (auto decoration : {SpvDecorationBinding, SpvDecorationDescriptorSet, SpvDecorationLocation}) {
        bad = original;
        bool changed = false;
        for (size_t at = 5; at < bad.size(); at += bad[at] >> 16) {
            if (!(bad[at] >> 16)) return 5;
            if ((bad[at] & 0xffff) == SpvOpDecorate && bad[at + 2] == decoration) {
                bad[at + 3] = 9;
                changed = true;
                break;
            }
        }
        if (!changed || !rejected(bad, 1)) return 6;
    }
    std::cout << "Actual invert SPIR-V accepted; corrupt/foreign descriptor and IO contracts rejected\n";
}
