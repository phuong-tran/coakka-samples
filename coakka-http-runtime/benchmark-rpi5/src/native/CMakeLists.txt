cmake_minimum_required(VERSION 3.24)
project(coakka_http_rpi5_native LANGUAGES C)

# Compile only this consumer; the exact installed native package owns Core.
# Native measures CoAkka C alone, without competitor or C++ comparison rows.
find_package(CoAkkaHttp 1.0.0 EXACT CONFIG REQUIRED)
find_package(Threads REQUIRED)
add_executable(coakka-c coakka.c)
target_link_libraries(coakka-c PRIVATE CoAkkaHttp::runtime Threads::Threads)
set_target_properties(coakka-c PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
if(MSVC)
  target_compile_options(coakka-c PRIVATE /W4 /WX)
else()
  target_compile_options(coakka-c PRIVATE -O3 -DNDEBUG -Wall -Wextra -Wpedantic -Werror)
endif()
