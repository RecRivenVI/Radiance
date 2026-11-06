#include "core/logging.hpp"
// This file is part of the FidelityFX SDK.
//
// Copyright (C) 2024 Advanced Micro Devices, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and /or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

#include "fsr_setup.hpp"
#include <cstring>
#include <filesystem>
#include <iostream>

#ifdef MCVR_ENABLE_FFX_UPSCALER
#    include <vk/ffx_api_vk.hpp>
#endif

namespace mcvr::fsr {

#ifdef MCVR_ENABLE_FFX_UPSCALER

#    ifdef _WIN32
const ffxFunctions &functions() {
    static const ffxFunctions api = [] {
        ffxFunctions table{};
        HMODULE core = nullptr;
        std::wstring filename(32768, L'\0');
        if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                                reinterpret_cast<LPCWSTR>(&functions), &core))
            return table;
        const auto length = GetModuleFileNameW(core, filename.data(), DWORD(filename.size()));
        if (!length || length >= filename.size()) return table;
        filename.resize(length);
        // GAME moves the writable shader/config directory after SERVICE starts.
        // SDK binaries belong beside the actually loaded core DLL, not there.
        const auto directory = std::filesystem::path(filename).parent_path() / "fsr";
        const auto path = directory / "amd_fidelityfx_loader_vk.dll";
        // Keep the loader resident: all view contexts and SDK provider pointers
        // must die before its code is unloaded. Windows releases it at process exit.
        constexpr auto flags = LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS;
        const auto provider = LoadLibraryExW((directory / "amd_fidelityfx_upscaler_vk.dll").c_str(), nullptr, flags);
        const auto module = provider ? LoadLibraryExW(path.c_str(), nullptr, flags) : nullptr;
        if (module) ffxLoadFunctions(&table, module);
        if (!table.CreateContext || !table.DestroyContext || !table.Query || !table.Dispatch || !table.Configure) {
            mcvr::log::error("Fsr3Upscaler")
                << "Official Vulkan loader unavailable: " << path << "; error=" << GetLastError() << std::endl;
            table = {};
        }
        return table;
    }();
    return api;
}

bool apiAvailable() {
    return functions().CreateContext != nullptr;
}
#    endif

FfxApiSurfaceFormat vkToFfxFormat(VkFormat format) {
    switch (format) {
        case VK_FORMAT_R32G32B32A32_SFLOAT: return FFX_API_SURFACE_FORMAT_R32G32B32A32_FLOAT;
        case VK_FORMAT_R16G16B16A16_SFLOAT: return FFX_API_SURFACE_FORMAT_R16G16B16A16_FLOAT;
        case VK_FORMAT_R16G16_SFLOAT: return FFX_API_SURFACE_FORMAT_R16G16_FLOAT;
        case VK_FORMAT_R32_SFLOAT: return FFX_API_SURFACE_FORMAT_R32_FLOAT;
        case VK_FORMAT_R8G8B8A8_UNORM: return FFX_API_SURFACE_FORMAT_R8G8B8A8_UNORM;
        case VK_FORMAT_R8G8B8A8_SRGB: return FFX_API_SURFACE_FORMAT_R8G8B8A8_SRGB;
        case VK_FORMAT_B8G8R8A8_UNORM: return FFX_API_SURFACE_FORMAT_B8G8R8A8_UNORM;
        case VK_FORMAT_B8G8R8A8_SRGB: return FFX_API_SURFACE_FORMAT_B8G8R8A8_SRGB;
        case VK_FORMAT_R32_UINT: return FFX_API_SURFACE_FORMAT_R32_UINT;
        case VK_FORMAT_D32_SFLOAT:
        case VK_FORMAT_D32_SFLOAT_S8_UINT: return FFX_API_SURFACE_FORMAT_R32_FLOAT;
        case VK_FORMAT_D24_UNORM_S8_UINT:
        case VK_FORMAT_X8_D24_UNORM_PACK32: return FFX_API_SURFACE_FORMAT_R32_UINT;
        case VK_FORMAT_D16_UNORM:
        case VK_FORMAT_D16_UNORM_S8_UINT: return FFX_API_SURFACE_FORMAT_R16_UNORM;
        case VK_FORMAT_R16_SFLOAT: return FFX_API_SURFACE_FORMAT_R16_FLOAT;
        case VK_FORMAT_R8_UNORM: return FFX_API_SURFACE_FORMAT_R8_UNORM;
        case VK_FORMAT_R8G8_UNORM: return FFX_API_SURFACE_FORMAT_R8G8_UNORM;
        case VK_FORMAT_R8G8B8A8_SNORM: return FFX_API_SURFACE_FORMAT_R8G8B8A8_SNORM;
        case VK_FORMAT_R32G32_SFLOAT: return FFX_API_SURFACE_FORMAT_R32G32_FLOAT;
        case VK_FORMAT_R32G32B32_SFLOAT: return FFX_API_SURFACE_FORMAT_R32G32B32_FLOAT;
        case VK_FORMAT_B10G11R11_UFLOAT_PACK32: return FFX_API_SURFACE_FORMAT_R11G11B10_FLOAT;
        default: return FFX_API_SURFACE_FORMAT_UNKNOWN;
    }
}

PFN_vkVoidFunction customVkGetDeviceProcAddr(VkDevice device, const char *pName) {
    PFN_vkVoidFunction result = vkGetDeviceProcAddr(device, pName);
    if (result != nullptr) { return result; }

    if (volkGetLoadedInstance() != VK_NULL_HANDLE) {
        result = vkGetInstanceProcAddr(volkGetLoadedInstance(), pName);
        if (result != nullptr) { return result; }
    }

    return nullptr;
}

void messageCallback(uint32_t type, const wchar_t *message) {
    try {
        char buffer[1024]{};
        if (message != nullptr) wcstombs(buffer, message, sizeof(buffer) - 1);
        buffer[sizeof(buffer) - 1] = '\0';
        if (type == FFX_API_MESSAGE_TYPE_ERROR) {
            mcvr::log::error("FsrSetup") << buffer << std::endl;
        } else if (type == FFX_API_MESSAGE_TYPE_WARNING) {
            mcvr::log::warn("FsrSetup") << buffer << std::endl;
        } else {
            mcvr::log::debug("FsrSetup") << buffer << std::endl;
        }
    } catch (...) {
        // Never let a C++ exception cross the FidelityFX callback boundary.
    }
}

#endif

} // namespace mcvr::fsr
