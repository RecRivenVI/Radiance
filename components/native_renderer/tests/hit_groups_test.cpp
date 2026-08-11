#include "core/render/hit_groups.hpp"

#include <stdexcept>
#include <string>
#include <unordered_map>

using namespace mcvr::hitgroups;

namespace {
void require(bool value, const char *what) {
    if (!value) throw std::runtime_error(what);
}
} // namespace

int main() {
    Registry registry;
    require(registry.size() == 1 && registry.name(shadow) == "shadow", "shadow is id 0");
    require(registry.intern("shadow") == shadow, "shadow name maps to the shadow id");
    const Id block = registry.intern("block");
    const Id outline = registry.intern(std::string("priority_outline_red"));
    require(block != shadow && outline != block, "distinct names get distinct ids");
    require(registry.intern(std::string_view("block")) == block, "ids are stable");
    require(registry.size() == 3 && registry.name(outline) == "priority_outline_red", "append-only table");

    // Same rule as the former per-geometry string lookup: shadow -> shadow index, known -> its
    // index, unknown -> fallback. A later pipeline map simply produces a new table.
    std::unordered_map<std::string, uint32_t> pass{{"block", 7}, {"shadow", 99}};
    auto table = registry.resolve(pass, 1, 2);
    require(table.size() == 3, "one entry per registered name");
    require(table[shadow] == 2 && table[block] == 7 && table[outline] == 1, "resolution rule");
    pass["priority_outline_red"] = 5;
    table = registry.resolve(pass, 1, 2);
    require(table[outline] == 5 && table[block] == 7, "resolution follows the current pass map");

    ResolvedLayout resolved;
    size_t resolutions = 0;
    auto palette = [&] {
        ++resolutions;
        return registry.resolve(pass, 1, 2);
    };
    const auto first = std::make_shared<const std::vector<Id>>(std::vector<Id>{shadow, block, outline});
    auto accepted = resolved.resolve(first, palette);
    require(*accepted == std::vector<uint32_t>({2, 7, 5}), "exact per-pass hit ordering");
    for (size_t i = 0; i < 1000; ++i)
        require(resolved.resolve(first, palette) == accepted, "unchanged version skips resolving and allocation");
    require(resolutions == 1, "one resolution for a stable version and all its consumers");
    const auto invalid = std::make_shared<const std::vector<Id>>(std::vector<Id>{1000});
    bool failed = false;
    try {
        resolved.resolve(invalid, palette);
    } catch (const std::logic_error &) { failed = true; }
    require(failed && resolved.resolve(first, palette) == accepted,
            "failed resolution preserves the last accepted version");
    const auto unknownA = registry.intern("unknown-a"), unknownB = registry.intern("unknown-b");
    auto fallback = resolved.resolve(std::make_shared<const std::vector<Id>>(std::vector<Id>{unknownA}), palette);
    require(resolved.resolve(std::make_shared<const std::vector<Id>>(std::vector<Id>{unknownB}), palette) == fallback,
            "different source names with equal resolved indices preserve the SBT version");
    ResolvedLayout otherPass;
    auto other = otherPass.resolve(first, [&] { return registry.resolve(pass, 10, 20); });
    require((*other)[0] == 20 && (*accepted)[0] == 2, "pass/frame mappings and old versions stay isolated");
    pass["block"] = 9;
    resolved.reset();
    auto reloaded = resolved.resolve(first, palette);
    require((*reloaded)[1] == 9 && (*accepted)[1] == 7, "pipeline reload resolves a new immutable version");

    bool unknown = false;
    try {
        (void)registry.name(1000);
    } catch (const std::out_of_range &) { unknown = true; }
    require(unknown, "unknown ids are rejected");

    Registry bounded;
    bool overflow = false;
    try {
        for (size_t i = 0; i <= Registry::limit; ++i) bounded.intern("name-" + std::to_string(i));
    } catch (const std::length_error &) { overflow = true; }
    require(overflow && bounded.size() == Registry::limit, "registry growth is bounded");
    return 0;
}
