#pragma once
#include "mz.h"
#include "mz_strm.h"
#include "mz_zip.h"
#include "mz_zip_rw.h"
#include <cstdint>
#include <filesystem>
#include <span>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace mcvr::replay {
class SnapshotZip {
    void *handle_ = nullptr;

  public:
    explicit SnapshotZip(const std::filesystem::path &path) {
        handle_ = mz_zip_writer_create();
        mz_zip_writer_set_compress_method(handle_, MZ_COMPRESS_METHOD_DEFLATE);
        mz_zip_writer_set_compress_level(handle_, 1);
        if (mz_zip_writer_open_file(handle_, path.string().c_str(), 0, 0) != MZ_OK) {
            mz_zip_writer_delete(&handle_);
            throw std::runtime_error("Cannot create snapshot ZIP");
        }
    }
    SnapshotZip(const SnapshotZip &) = delete;
    ~SnapshotZip() {
        if (handle_) mz_zip_writer_delete(&handle_);
    }
    void add(const std::string &name, std::span<const uint8_t> bytes) {
        if (bytes.size() > INT32_MAX) throw std::runtime_error("One snapshot blob exceeds ZIP buffer API limit");
        mz_zip_file entry{};
        entry.filename = name.c_str();
        entry.compression_method = MZ_COMPRESS_METHOD_DEFLATE;
        if (mz_zip_writer_add_buffer(handle_, bytes.data(), int32_t(bytes.size()), &entry) != MZ_OK)
            throw std::runtime_error("Snapshot compression failed");
    }
    void close() {
        const auto result = mz_zip_writer_close(handle_);
        mz_zip_writer_delete(&handle_);
        if (result != MZ_OK) throw std::runtime_error("Snapshot ZIP close failed");
    }
};
class SnapshotReader {
    void *handle_ = nullptr;
    void *zip_ = nullptr;
    std::unordered_map<std::string, int64_t> entries_;

  public:
    explicit SnapshotReader(const std::filesystem::path &path) {
        handle_ = mz_zip_reader_create();
        if (mz_zip_reader_open_file(handle_, path.string().c_str()) != MZ_OK) {
            mz_zip_reader_delete(&handle_);
            throw std::runtime_error("Snapshot ZIP missing or unreadable");
        }
        try {
            if (mz_zip_reader_get_zip_handle(handle_, &zip_) != MZ_OK)
                throw std::runtime_error("Cannot access snapshot ZIP directory");
            auto status = mz_zip_goto_first_entry(zip_);
            while (status == MZ_OK) {
                mz_zip_file *entry = nullptr;
                if (mz_zip_entry_get_info(zip_, &entry) != MZ_OK ||
                    !entries_.emplace(entry->filename, mz_zip_get_entry(zip_)).second)
                    throw std::runtime_error("Invalid or duplicate snapshot ZIP entry");
                status = mz_zip_goto_next_entry(zip_);
            }
            if (status != MZ_END_OF_LIST) throw std::runtime_error("Invalid snapshot ZIP directory");
        } catch (...) {
            mz_zip_reader_delete(&handle_);
            throw;
        }
    }
    SnapshotReader(const SnapshotReader &) = delete;
    ~SnapshotReader() {
        if (handle_) mz_zip_reader_delete(&handle_);
    }
    std::vector<uint8_t> read(const std::string &name, uint64_t size) {
        if (!size || size > INT32_MAX) throw std::runtime_error("Invalid snapshot blob size");
        const auto it = entries_.find(name);
        if (it == entries_.end() || mz_zip_goto_entry(zip_, it->second) != MZ_OK)
            throw std::runtime_error("Snapshot blob missing");
        mz_zip_file *entry = nullptr;
        if (mz_zip_entry_get_info(zip_, &entry) != MZ_OK || entry->uncompressed_size != int64_t(size))
            throw std::runtime_error("Snapshot blob size mismatch");
        std::vector<uint8_t> bytes(size);
        if (mz_zip_entry_read_open(zip_, 0, nullptr) != MZ_OK) throw std::runtime_error("Cannot open snapshot blob");
        const auto count = mz_zip_entry_read(zip_, bytes.data(), int32_t(size));
        const auto close = mz_zip_entry_read_close(zip_, nullptr, nullptr, nullptr);
        if (count != int32_t(size) || close != MZ_OK) throw std::runtime_error("Snapshot blob decode/CRC failed");
        return bytes;
    }
};
inline std::vector<uint8_t>
readSnapshotBlob(const std::filesystem::path &path, const std::string &name, uint64_t size) {
    SnapshotReader reader(path);
    return reader.read(name, size);
}
} // namespace mcvr::replay
