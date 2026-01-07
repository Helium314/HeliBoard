cmake_minimum_required(VERSION 3.10.2)

project(jni_latinime)

# JNI wrapper files in the current directory
file(GLOB JNI_SOURCES "*.cpp")

# Core logic files in src/
file(GLOB_RECURSE CORE_SOURCES "src/*.cpp")

# Exclude test files
list(FILTER CORE_SOURCES EXCLUDE REGEX ".*_test\\.cpp$")

add_library(
        jni_latinime
        SHARED
        ${JNI_SOURCES}
        ${CORE_SOURCES}
)

# Include the 'src' directory for header searches
target_include_directories(jni_latinime PRIVATE src)

# Link against the Android log library
find_library(log-lib log)
target_link_libraries(jni_latinime ${log-lib})

