cmake_minimum_required(VERSION 3.20)
project(CoAkkaNativeStartupAdmission LANGUAGES NONE)
set(COAKKA_NATIVE_EVIDENCE_REQUIRE_NATIVE_STARTUP ON)
include("${CMAKE_CURRENT_LIST_DIR}/../cmake/NativeStartupPackage.cmake")
