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
    // Optional crash-dump capture for isolated GPU fault diagnosis. The Aftermath
    // agent is the same module the Java client loads as a JVM agent; its entry
    // point ignores the JavaVM argument, so the replay process can initialize it
    // before core.dll creates the Vulkan device.
    if (const wchar_t *agentPath = _wgetenv(L"MCVR_REPLAY_AFTERMATH_AGENT")) {
        const wchar_t *dumpDir = _wgetenv(L"MCVR_REPLAY_AFTERMATH_OUTPUT");
        auto agent = LoadLibraryExW(std::filesystem::absolute(agentPath).c_str(), nullptr,
                                    LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        auto onLoad =
            agent ? reinterpret_cast<long(__stdcall *)(void *, char *, void *)>(GetProcAddress(agent, "Agent_OnLoad")) :
                    nullptr;
        if (!onLoad || !dumpDir) {
            std::wcerr << L"Cannot initialize Aftermath agent: " << GetLastError() << '\n';
            return 5;
        }
        auto options = std::filesystem::absolute(dumpDir).u8string();
        if (onLoad(nullptr, reinterpret_cast<char *>(options.data()), nullptr) != 0) {
            std::wcerr << L"Aftermath agent refused initialization\n";
            return 5;
        }
        _wputenv_s(L"MCVR_LOCAL_AFTERMATH", L"1");
    }
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
