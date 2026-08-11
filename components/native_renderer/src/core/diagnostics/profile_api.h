#pragma once
#include <stdint.h>

/* Optional, local observer, separate from the allocation/capture ABI. Borrowed labels,
 * non-throwing process-lived callback; no renderer re-entry. Nanoseconds are durations,
 * never a clock epoch to subtract from Java nanoTime. frame=0 means a worker/no owner.
 * Domain 5 is an additive work counter, NOT a duration: the two values are count and bytes. */
#define MCVR_PROFILE_ABI 2u
// Independent observer work classes. A timing capture never implies a full
// resource census or a second copy/comparison of scene contents.
#define MCVR_PROFILE_TIMING 1u
#define MCVR_PROFILE_CONTENT_AUDIT 2u
#define MCVR_PROFILE_RESIDENCY_CENSUS 4u
struct McvrProfileSink {
    uint32_t abi, size;
    void (*sample)(uint64_t frame, uint32_t domain, const char *label, uint64_t inclusiveNs, uint64_t selfNs);
};
typedef int (*McvrInstallProfileSink)(const struct McvrProfileSink *sink);
typedef void (*McvrProfileFrame)(uint64_t frame, int flags);
