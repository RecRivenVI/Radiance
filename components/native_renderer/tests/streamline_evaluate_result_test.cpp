#include "core/render/streamline_evaluate_result.hpp"
#include "core/render/evaluated_output.hpp"
#include <stdexcept>
#include <string>

int main() {
    for (bool sdkValid : {false, true}) {
        int evaluate = 0, published = 0, fallback = 0;
        bool actual = mcvr::consumeEvaluatedOutput(
            [&] {
                ++evaluate;
                return sdkValid;
            },
            [&] { ++published; }, [&] { ++fallback; });
        if (actual != sdkValid || evaluate != 1 || published != int(sdkValid) || fallback != int(!sdkValid))
            throw std::runtime_error("invalid SDK output published or valid output discarded");
    }
    int callbacks = 0;
    try {
        mcvr::consumeEvaluatedOutput([]() -> bool { throw std::runtime_error("primary SDK error"); },
                                     [&] { ++callbacks; }, [&] { ++callbacks; });
        throw std::logic_error("SDK exception swallowed");
    } catch (const std::runtime_error &error) {
        if (std::string(error.what()) != "primary SDK error" || callbacks) throw;
    }

    if (!mcvr::streamlineEvaluationCompleted(sl::Result::eOk) ||
        !mcvr::streamlineEvaluationCompleted(sl::Result::eWarnOutOfVRAM))
        throw std::runtime_error("completed evaluation was rejected");
    for (int result = int(sl::Result::eErrorIO); result <= int(sl::Result::eErrorInvalidState); ++result)
        if (mcvr::streamlineEvaluationCompleted(static_cast<sl::Result>(result)))
            throw std::runtime_error("failed evaluation was accepted");
    if (mcvr::streamlineEvaluationCompleted(static_cast<sl::Result>(999)))
        throw std::runtime_error("unknown result was accepted");
}
