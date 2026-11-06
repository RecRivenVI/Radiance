# Production SDK, including the matching signed feature runtimes. No moving URL/OTA.
include(FetchContent)
FetchContent_Declare(streamline_sdk
    URL https://github.com/NVIDIA-RTX/Streamline/releases/download/v2.14.16/streamline-sdk-v2.14.16.zip
    URL_HASH SHA256=32041e51db0cab709a271b72a352281b690c2abbd669a827809401a5dd23956b
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_GetProperties(streamline_sdk)
if(NOT streamline_sdk_POPULATED)
    FetchContent_Populate(streamline_sdk)
endif()
set(MCVR_STREAMLINE_INCLUDE_DIR "${streamline_sdk_SOURCE_DIR}/include")
set(MCVR_STREAMLINE_RUNTIME_NAMES
    sl.interposer sl.common sl.dlss sl.dlss_d sl.dlss_g sl.reflex sl.pcl
    nvngx_dlss nvngx_dlssd nvngx_dlssg NvLowLatencyVk)
foreach(name IN LISTS MCVR_STREAMLINE_RUNTIME_NAMES)
    install(FILES "${streamline_sdk_SOURCE_DIR}/bin/x64/${name}.dll"
        DESTINATION "${MCVR_INSTALL_LIB_DIR}/dlss")
    install(FILES "${streamline_sdk_SOURCE_DIR}/bin/x64/${name}.dll"
        DESTINATION "${MCVR_INSTALL_BIN_DIR}/dlss")
endforeach()
install(FILES "${streamline_sdk_SOURCE_DIR}/license.txt"
    DESTINATION "${MCVR_INSTALL_LIB_DIR}/dlss" RENAME Streamline-LICENSE.txt)
install(FILES "${streamline_sdk_SOURCE_DIR}/3rd-party-licenses.md"
    DESTINATION "${MCVR_INSTALL_LIB_DIR}/dlss" RENAME Streamline-THIRD-PARTY.md)

install(FILES "${MCVR_DLSS_ROOT}/LICENSE.txt" DESTINATION "${MCVR_INSTALL_LIB_DIR}/dlss")

install(FILES "${PROJECT_SOURCE_DIR}/src/runtime/dlss/NOTICE.txt" "${PROJECT_SOURCE_DIR}/src/runtime/dlss/runtime-manifest.json" DESTINATION "${MCVR_INSTALL_LIB_DIR}/dlss")
