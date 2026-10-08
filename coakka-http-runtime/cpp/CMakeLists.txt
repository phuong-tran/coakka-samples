cmake_minimum_required(VERSION 3.24)
project(coakka_http_runtime_cpp_sample LANGUAGES CXX)

find_package(CoAkkaHttp 1.0.0 EXACT CONFIG REQUIRED)
find_package(Threads REQUIRED)

add_executable(coakka-http-cpp server.cpp)
add_executable(coakka-http-cpp-streaming streaming.cpp)
add_executable(coakka-http-cpp-outbound outbound.cpp)
foreach(sample_target coakka-http-cpp coakka-http-cpp-streaming coakka-http-cpp-outbound)
  target_link_libraries(${sample_target} PRIVATE CoAkkaHttp::runtime Threads::Threads)
  set_target_properties(${sample_target} PROPERTIES
    CXX_STANDARD 20 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
  if(MSVC)
    target_compile_options(${sample_target} PRIVATE /W4 /WX /permissive-)
  else()
    target_compile_options(${sample_target} PRIVATE -Wall -Wextra -Wpedantic -Werror)
  endif()
endforeach()
