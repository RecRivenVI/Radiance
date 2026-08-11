#include "core/diagnostics/device_loss_trace.hpp"
#include "core/diagnostics/gpu_fault_capture.hpp"
#include "core/diagnostics/as_lifetime_trace.hpp"
#include "core/diagnostics/local_gpu_capture.hpp"

#include <stdexcept>
#include <string_view>
#include <thread>

int main() {
    if (mcvr::diagnostics::local_gpu_capture::flags() != 0)
        throw std::runtime_error("ordinary regression process unexpectedly loaded a capture agent");
    mcvr::diagnostics::local_gpu_capture::beforeLostDeviceRelease();
    using namespace mcvr::diagnostics::device_loss;
    if (!enabled()) throw std::runtime_error("test trace was not enabled");
    if (passFingerprint("primary") != passFingerprint(std::string("primary")) ||
        passFingerprint("primary") == passFingerprint("final_compose"))
        throw std::runtime_error("pass fingerprint is not stable");
    resetForTest();
    for (uint32_t i = 0; i < capacity + 4; ++i)
        note("controlled-event", i == capacity + 3 ? VK_ERROR_DEVICE_LOST : VK_SUCCESS, i, i + 1);
    const auto snapshot = snapshotForTest();
    if (snapshot.size() != capacity || snapshot.front().sequence != 4 || snapshot.back().sequence != capacity + 3 ||
        snapshot.back().value0 != capacity + 3 || snapshot.back().result != VK_ERROR_DEVICE_LOST)
        throw std::runtime_error("fixed-capacity device-loss event ring lost ordering or payload");
    int calls = 0;
    std::string label = "owned-temporary-label";
    note(label, VK_SUCCESS, 9, 8, 0x123456789ull, 0xfedcba987ull, 64);
    label.assign("changed");
    if (std::string_view(snapshotForTest().back().operation.data()) != "owned-temporary-label")
        throw std::runtime_error("trace borrowed the caller label");
    auto &busy = events[nextSequence.load() % capacity];
    busy.busy.test_and_set();
    note("deliberately-busy-slot");
    busy.busy.clear();
    if (dropped.load() != 1) throw std::runtime_error("busy diagnostic producer must drop rather than wait");
    resetForTest();
    std::vector<std::thread> producers;
    for (uint32_t thread = 0; thread < 8; ++thread)
        producers.emplace_back([thread] {
            for (uint32_t i = 0; i < capacity; ++i) {
                const uint64_t id = (uint64_t(thread) << 32) | i;
                note("parallel", VK_SUCCESS, thread, i, id, ~id, id ^ 0x555555555ull, 0x123456789abcdef0ull);
            }
        });
    for (auto &producer : producers) producer.join();
    const auto concurrent = snapshotForTest();
    if (concurrent.empty()) throw std::runtime_error("concurrent trace empty");
    for (const auto &entry : concurrent)
        if (entry.object != ((uint64_t(entry.value0) << 32) | entry.value1) || entry.related != ~entry.object ||
            entry.bytes != (entry.object ^ 0x555555555ull) || entry.queue != 0x123456789abcdef0ull)
            throw std::runtime_error("mixed trace payload during concurrent wrap");
    auto failed = mcvr::diagnostics::captureFault(VK_NULL_HANDLE,
                                                  [&](VkDevice, VkDeviceFaultCountsEXT *, VkDeviceFaultInfoEXT *) {
                                                      ++calls;
                                                      return VK_ERROR_UNKNOWN;
                                                  });
    if (calls != 1 || failed.countResult != VK_ERROR_UNKNOWN || failed.detailResult != VK_NOT_READY)
        throw std::runtime_error("failed fault query must not continue");
    calls = 0;
    auto bounded = mcvr::diagnostics::captureFault(
        VK_NULL_HANDLE, [&](VkDevice, VkDeviceFaultCountsEXT *counts, VkDeviceFaultInfoEXT *info) {
            ++calls;
            if (!info) {
                counts->addressInfoCount = 10000;
                counts->vendorInfoCount = 10000;
                return VK_SUCCESS;
            }
            if (counts->addressInfoCount != 256 || counts->vendorInfoCount != 32 || counts->vendorBinarySize != 0)
                throw std::runtime_error("fault output exceeded bounded capacity");
            info->pAddressInfos[255].reportedAddress = 0x123456789ull;
            std::memset(info->description, 'x', VK_MAX_DESCRIPTION_SIZE);
            return VK_INCOMPLETE;
        });
    if (calls != 2 || bounded.detailResult != VK_INCOMPLETE || bounded.description.back() != '\0' ||
        bounded.addresses.back().reportedAddress != 0x123456789ull)
        throw std::runtime_error("partial fault data or description termination was lost");
    namespace lifetime = mcvr::diagnostics::as_lifetime;
    for (uint64_t i = 0; i < lifetime::capacity + 7; ++i)
        lifetime::note(lifetime::Kind::instance, i, i + 1, i + 2, i + 3);
    const auto &last = lifetime::events[(lifetime::nextSequence - 1) % lifetime::capacity];
    if (lifetime::nextSequence != lifetime::capacity + 7 || last.sequence != lifetime::capacity + 6 ||
        last.address != lifetime::capacity + 7 || last.related != lifetime::capacity + 8 ||
        last.detail != lifetime::capacity + 9)
        throw std::runtime_error("bounded AS ownership records lost exact 64-bit identities");
    return 0;
}
