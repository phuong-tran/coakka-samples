cmake_minimum_required(VERSION 3.20)
project(CoAkkaLoggerNativePressureSample LANGUAGES C CXX)

find_package(CoAkkaLoggerNative CONFIG REQUIRED)

add_executable(coakka_logger_native_c_pressure main.c)
target_link_libraries(coakka_logger_native_c_pressure PRIVATE CoAkkaLoggerNative::core)
