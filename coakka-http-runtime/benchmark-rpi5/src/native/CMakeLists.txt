cmake_minimum_required(VERSION 3.24)
project(coakka_http_rpi5_native LANGUAGES C CXX)

find_package(CoAkkaHttpHost 1.0.0 EXACT CONFIG REQUIRED)
find_package(PkgConfig REQUIRED)
find_package(Threads REQUIRED)
pkg_check_modules(MICROHTTPD REQUIRED IMPORTED_TARGET libmicrohttpd)
pkg_check_modules(CPPHTTPLIB REQUIRED IMPORTED_TARGET cpp-httplib)

add_executable(coakka-c coakka.c)
target_link_libraries(coakka-c PRIVATE CoAkkaHttp::host)
target_compile_features(coakka-c PRIVATE c_std_11)

add_executable(microhttpd microhttpd.c)
target_link_libraries(microhttpd PRIVATE PkgConfig::MICROHTTPD)
target_compile_features(microhttpd PRIVATE c_std_11)

add_executable(coakka-cpp coakka.cpp)
target_link_libraries(coakka-cpp PRIVATE CoAkkaHttp::host Threads::Threads)
target_compile_features(coakka-cpp PRIVATE cxx_std_20)

add_executable(cpp-httplib cpp_httplib.cpp)
target_link_libraries(cpp-httplib PRIVATE PkgConfig::CPPHTTPLIB Threads::Threads)
target_compile_features(cpp-httplib PRIVATE cxx_std_20)

foreach(target coakka-c microhttpd coakka-cpp cpp-httplib)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /WX)
  else()
    target_compile_options(${target} PRIVATE -O3 -DNDEBUG -Wall -Wextra -Wpedantic -Werror)
  endif()
endforeach()
