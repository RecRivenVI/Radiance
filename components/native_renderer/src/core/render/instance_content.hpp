#pragma once

#include <cstring>

namespace mcvr {
// Compare complete TLAS inputs, including order and the owning BLAS identity.
// An address alone can be recycled after a resource generation has retired.
template <class Instances, class Models>
bool sameInstanceContent(const Instances &a, const Models &aModels, const Instances &b, const Models &bModels) {
    if (a.size() != b.size() || aModels != bModels || aModels.size() != a.size()) return false;
    for (size_t i = 0; i < a.size(); ++i) {
        if (std::memcmp(&a[i], &b[i], sizeof(a[i]))) return false;
    }
    return true;
}
} // namespace mcvr
