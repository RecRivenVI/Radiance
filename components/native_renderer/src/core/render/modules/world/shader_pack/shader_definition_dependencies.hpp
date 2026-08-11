#pragma once
#include <cctype>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>

namespace mcvr::shader {
using Definitions = std::unordered_map<std::string, std::string>;
using DefinitionNames = std::unordered_set<std::string>;

inline void referenceDefinitions(std::string_view source, const DefinitionNames &names, DefinitionNames &used) {
    for (size_t i = 0; i < source.size();) {
        const auto first = static_cast<unsigned char>(source[i]);
        if (first != '_' && !std::isalpha(first)) {
            ++i;
            continue;
        }
        const size_t start = i++;
        while (i < source.size()) {
            const auto next = static_cast<unsigned char>(source[i]);
            if (next != '_' && !std::isalnum(next)) break;
            ++i;
        }
        std::string name(source.substr(start, i - start));
        if (names.contains(name)) used.insert(std::move(name));
    }
}

inline Definitions dependencyClosure(const Definitions &definitions, DefinitionNames used, std::string_view injected) {
    // Token concatenation can manufacture an identifier absent from the source.
    if (injected.find("##") != std::string_view::npos) return definitions;
    DefinitionNames names;
    for (const auto &[name, value] : definitions) names.insert(name);
    referenceDefinitions(injected, names, used);
    size_t previous;
    do {
        previous = used.size();
        const auto current = used;
        for (const auto &name : current) {
            if (auto entry = definitions.find(name); entry != definitions.end()) {
                if (entry->second.find("##") != std::string::npos) return definitions;
                referenceDefinitions(entry->second, names, used);
            }
        }
    } while (used.size() != previous);
    Definitions result;
    for (const auto &[name, value] : definitions)
        if (used.contains(name)) result.emplace(name, value);
    return result;
}
} // namespace mcvr::shader
