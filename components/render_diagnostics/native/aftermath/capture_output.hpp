#pragma once
#include <array>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <stdexcept>
#include <unordered_set>

namespace local_capture {
enum class Kind { dump, shader };
struct Budget {
    uint64_t bytes = 0;
    uint32_t files = 0, rejected = 0;
};
class Output {
    std::filesystem::path directory;
    std::mutex mutex;
    std::array<Budget, 2> usage{};
    std::unordered_set<std::string> written;

  public:
    explicit Output(std::filesystem::path path) : directory(std::move(path)) {
        std::filesystem::create_directories(directory);
    }
    bool save(Kind kind, const char *name, const void *data, uint32_t size) {
        std::lock_guard lock(mutex);
        const auto filename = std::filesystem::path(name);
        if (filename.has_parent_path() || filename.empty() || !data)
            throw std::invalid_argument("Capture output name/data");
        if (written.contains(name)) return true;
        auto &budget = usage.at(size_t(kind));
        const bool dump = kind == Kind::dump;
        const uint32_t maxFiles = dump ? 4 : 4096, maxFile = (dump ? 128u : 16u) * 1024 * 1024;
        if (size > maxFile || budget.files >= maxFiles || size > 512ull * 1024 * 1024 - budget.bytes) {
            ++budget.rejected;
            return false;
        }
        std::ofstream output(directory / filename, std::ios::binary);
        output.write(static_cast<const char *>(data), size);
        output.close();
        if (!output) throw std::runtime_error("GPU diagnostic output failed");
        written.emplace(name);
        budget.bytes += size;
        ++budget.files;
        return true;
    }
    Budget statistics(Kind kind) {
        std::lock_guard lock(mutex);
        return usage.at(size_t(kind));
    }
};
} // namespace local_capture
