from std.ffi import OwnedDLHandle, c_int
from std.sys import argv


def main() raises:
    var args = argv()
    if len(args) != 2:
        print("usage: mojo main.mojo <logger-shim-library>")
        raise Error("missing logger shim library path")

    var lib = OwnedDLHandle(String(args[1]))
    var status = lib.call["coakka_mojo_logger_basic", c_int](0)
    if status != 0:
        raise Error("coakka_mojo_logger_basic failed")
