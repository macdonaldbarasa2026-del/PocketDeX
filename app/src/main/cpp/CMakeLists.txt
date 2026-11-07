cmake_minimum_required(VERSION 3.22.1)
project(pocketdex_vulkan_probe LANGUAGES CXX)

find_library(vulkan_lib vulkan REQUIRED)

add_library(pocketdex_vulkan_probe SHARED vulkan_probe.cpp)
target_compile_features(pocketdex_vulkan_probe PRIVATE cxx_std_17)
target_link_libraries(pocketdex_vulkan_probe PRIVATE ${vulkan_lib})
