#pragma once

#include <cstring>
#include <tuple>

namespace mcvr {
// Compare complete TLAS inputs, including order and the owning BLAS identity.
// An address alone can be recycled after a resource generation has retired.
template <class Instances>
bool sameInstanceContent(const Instances &a, const Instances &b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); ++i) {
        if (std::memcmp(std::get<0>(a[i]).matrix, std::get<0>(b[i]).matrix, sizeof(std::get<0>(a[i]).matrix)) ||
            std::get<1>(a[i]) != std::get<1>(b[i]) || std::get<2>(a[i]) != std::get<2>(b[i]) ||
            std::get<3>(a[i]) != std::get<3>(b[i]) || std::get<4>(a[i]) != std::get<4>(b[i]) ||
            std::get<5>(a[i]) != std::get<5>(b[i]))
            return false;
    }
    return true;
}
} // namespace mcvr
