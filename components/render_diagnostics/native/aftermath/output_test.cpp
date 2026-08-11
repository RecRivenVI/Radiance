#include "capture_output.hpp"
#include <future>
#include <vector>

void require(bool valid) {
    if (!valid) throw std::runtime_error("GPU capture output regression");
}
int main() {
    const auto folder = std::filesystem::current_path() / "synthetic-capture-output";
    local_capture::Output output(folder);
    const uint32_t data = 0x12345678;
    std::vector<std::future<void>> workers;
    for (int i = 0; i < 16; ++i)
        workers.push_back(std::async(std::launch::async, [&, i] {
            const auto name = "shader-" + std::to_string(i) + ".bin";
            require(output.save(local_capture::Kind::shader, name.c_str(), &data, sizeof(data)));
            require(output.save(local_capture::Kind::shader, name.c_str(), &data, sizeof(data)));
        }));
    for (auto &worker : workers) worker.get();
    require(output.statistics(local_capture::Kind::shader).files == 16);
    for (int i = 0; i < 4; ++i) {
        const auto name = "fixture-" + std::to_string(i) + ".bin";
        require(output.save(local_capture::Kind::dump, name.c_str(), &data, sizeof(data)));
        uint32_t read = 0;
        std::ifstream file(folder / name, std::ios::binary);
        file.read(reinterpret_cast<char *>(&read), sizeof(read));
        require(read == data);
    }
    require(!output.save(local_capture::Kind::dump, "fifth.bin", &data, sizeof(data)));
    require(!output.save(local_capture::Kind::shader, "oversize.bin", &data, 17u * 1024 * 1024));
    require(!std::filesystem::exists(folder / "oversize.bin"));
    bool failed = false;
    try {
        output.save(local_capture::Kind::dump, "../escape.bin", &data, sizeof(data));
    } catch (const std::invalid_argument &) { failed = true; }
    require(failed);
    // Fixture bytes are not a GPU crash dump; no fault or SDK is needed.
}
