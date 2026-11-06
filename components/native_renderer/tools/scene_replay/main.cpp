#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <filesystem>
#include <iostream>

int wmain(int argc, wchar_t **argv) {
    if (argc != 5) {
        std::wcerr << L"Usage: mcvr_scene_replay <core.dll> <snapshot> <plan.json> <new-output-directory>\n";
        return 2;
    }
    const auto dll = std::filesystem::absolute(argv[1]);
    SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    const auto cookie = AddDllDirectory(dll.parent_path().c_str());
    auto module =
        LoadLibraryExW(dll.c_str(), nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    if (!module) {
        std::wcerr << L"Cannot load core.dll: " << GetLastError() << '\n';
        return 3;
    }
    auto run = reinterpret_cast<int (*)(const wchar_t *, const wchar_t *, const wchar_t *)>(
        GetProcAddress(module, "MCVR_ReplayMain"));
    if (!run) {
        std::cerr << "Use development core built with -Pnative.sceneReplay=true\n";
        return 4;
    }
    const auto result = run(argv[2], argv[3], argv[4]);
    // Native singletons are destroyed during process teardown, before OS unload.
    // Do not manually unload core while its static dependencies remain alive.
    if (cookie) RemoveDllDirectory(cookie);
    return result;
}
