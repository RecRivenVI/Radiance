#include "core/render/modules/world/shader_pack/shader_definition_dependencies.hpp"
#include <stdexcept>
using namespace mcvr::shader;
int main() {
    const Definitions definitions{{"LIVE", "INDIRECT + 1"}, {"INDIRECT", "7"},      {"UNUSED", "99"},
                                  {"CYCLE_A", "CYCLE_B"},   {"CYCLE_B", "CYCLE_A"}, {"INJECTED", "LIVE"}};
    auto required = dependencyClosure(definitions, {"LIVE"}, "");
    if (required.size() != 2 || required.at("INDIRECT") != "7") throw std::runtime_error("Transitive definition lost");
    required = dependencyClosure(definitions, {}, "result = INJECTED;");
    if (required.size() != 3 || !required.contains("LIVE")) throw std::runtime_error("Injected contract lost");
    if (dependencyClosure(definitions, {"CYCLE_A"}, "").size() != 2) throw std::runtime_error("Cyclic closure failed");
    if (dependencyClosure(definitions, {}, "x ## y").size() != definitions.size())
        throw std::runtime_error("Manufactured identifier not conservative");
    if (!dependencyClosure(definitions, {}, "UNUSED_SUFFIX").empty())
        throw std::runtime_error("Identifier substring collision");
}
