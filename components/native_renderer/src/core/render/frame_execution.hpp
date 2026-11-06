#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <exception>
#include <functional>
#include <mutex>
#include <optional>
#include <shared_mutex>
#include <thread>

namespace mcvr {
// One sealed frame at a time. The producer lock protects the handoff itself;
// JNI calls then wait outside that lock while the render lane owns the frame.
class FrameExecution {
  public:
    static FrameExecution &instance();
    static bool enabled();
    static void *workerRecordingSlot();
    static const void *producerRecordingSlot();
    static void setProducerWorldActive(bool active);
    static bool producerWorldActive();
    static void setProducerRecordingSlot(const void *slot);
    static void setDeferredProducerPreparation(void (*prepare)());
    static bool producerOverlayDeferred();
    static void prepareDeferredProducer();
    static void noteProducerPacing(uint64_t start, uint64_t end);
    static void checkAccess(const char *family, const void *slot = nullptr);

    class Producer {
      public:
        explicit Producer(bool enter, const char *operation = "native producer");
        ~Producer();
        Producer(const Producer &) = delete;
        Producer &operator=(const Producer &) = delete;

      private:
        bool entered_;
        std::shared_lock<std::shared_mutex> access_;
        static thread_local unsigned depth_;
    };

    void dispatch(void *slot, std::function<int()> work);
    std::optional<int> consume();
    ~FrameExecution();

  private:
    struct HandoffProfile {
        std::thread::id producer;
        uint64_t owner, generation, queuedAt;
        uint64_t workerBegan = 0, workerEnded = 0;
        uint64_t firstWait = 0, blocked = 0;
        uint64_t pacingStart = 0, pacingEnd = 0;
    };

    void waitUntilIdle(std::unique_lock<std::mutex> &state, const char *operation);
    void execute();

    std::shared_mutex producers_;
    std::mutex mutex_;
    std::condition_variable changed_;
    std::function<int()> work_;
    std::optional<int> result_;
    std::exception_ptr error_;
    void *queuedSlot_ = nullptr;
    std::atomic<const void *> executingSlot_{nullptr};
    std::optional<HandoffProfile> handoffProfile_;
    bool active_ = false, closed_ = false;
    std::thread worker_;
};
} // namespace mcvr
