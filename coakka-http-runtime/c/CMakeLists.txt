cmake_minimum_required(VERSION 3.24)
project(coakka_http_runtime_c_sample LANGUAGES C)

find_package(CoAkkaHttp 1.0.0 EXACT CONFIG REQUIRED)
find_package(Threads REQUIRED)

add_executable(coakka-http-c server.c)
add_executable(coakka-http-c-outbound outbound.c)
add_executable(coakka-http-c-streaming streaming.c)
foreach(sample_target coakka-http-c coakka-http-c-outbound coakka-http-c-streaming)
  target_link_libraries(${sample_target} PRIVATE CoAkkaHttp::runtime Threads::Threads)
  set_target_properties(${sample_target} PROPERTIES
    C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
  if(MSVC)
    target_compile_options(${sample_target} PRIVATE /W4 /WX)
  else()
    target_compile_options(${sample_target} PRIVATE -Wall -Wextra -Wpedantic -Werror)
  endif()
endforeach()
