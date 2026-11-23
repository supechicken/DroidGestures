cmake_minimum_required(VERSION 3.22.1)
project("DroidGesturesJNI")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

file(GLOB_RECURSE CPP_SOURCES CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/libdroidgestures/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/*.cpp"
)

add_library(droidgestures SHARED ${CPP_SOURCES})

target_compile_options(droidgestures PRIVATE -O3 -flto=thin)
target_link_options(droidgestures PRIVATE -flto=thin -s)

target_include_directories(droidgestures PRIVATE
    "${JNI_INCLUDE_DIRS}"
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/include/libevdev-1.0"
    "${CMAKE_CURRENT_SOURCE_DIR}/libdroidgestures/src"
)

find_library(log-lib log)

target_link_libraries(droidgestures
    ${log-lib}
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libevdev.so"
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libinput.so"
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libudev.so"
)
