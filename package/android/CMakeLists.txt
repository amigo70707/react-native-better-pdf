project(NitroBetterPdf)
cmake_minimum_required(VERSION 3.9.0)

set (PACKAGE_NAME NitroBetterPdf)
set (CMAKE_VERBOSE_MAKEFILE ON)
set (CMAKE_CXX_STANDARD 20)

add_library(${PACKAGE_NAME} SHARED
        src/main/cpp/cpp-adapter.cpp
)

include(${CMAKE_SOURCE_DIR}/../nitrogen/generated/android/NitroBetterPdf+autolinking.cmake)

# Only JNI_OnLoad (JNIEXPORT) is looked up by name; everything else is
# registered by address (fbjni's registerNatives). Hiding the other symbols
# keeps their names out of the library.
target_compile_options(${PACKAGE_NAME} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)

include_directories(
        "src/main/cpp"
)

find_library(LOG_LIB log)

target_link_libraries(
        ${PACKAGE_NAME}
        ${LOG_LIB}
        android
)
