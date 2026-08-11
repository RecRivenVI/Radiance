#pragma once
#include <utility>

namespace mcvr {
// SDK acceptance selects which recording callback may consume output. It does
// not certify GPU completion; exceptions preserve the original failure boundary.
template <class Evaluate, class Publish, class Fallback>
bool consumeEvaluatedOutput(Evaluate &&evaluate, Publish &&publish, Fallback &&fallback) {
    const bool valid = std::forward<Evaluate>(evaluate)();
    if (valid)
        std::forward<Publish>(publish)();
    else
        std::forward<Fallback>(fallback)();
    return valid;
}
} // namespace mcvr
