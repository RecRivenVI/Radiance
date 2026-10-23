#pragma once
#include <cstdint>

namespace mcvr::scene {
struct ResourceVersion {
    uint64_t epoch, identity;
    int64_t revision;
    bool operator==(const ResourceVersion &) const = default;
};
enum class Completion { pending, empty, orderedFrame, gpuComplete };

// The caller supplies the current owner under its existing lock. Completion is
// either a signalled build fence, an empty version, or the ordered frame graph.
inline bool mayPublish(ResourceVersion desired, ResourceVersion built, int64_t published, Completion completion) {
    return completion != Completion::pending && desired == built && built.revision > published;
}
} // namespace mcvr::scene
