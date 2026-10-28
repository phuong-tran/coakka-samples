from std.ffi import OwnedDLHandle, c_int
from std.sys import argv


def main() raises:
    var args = argv()
    if len(args) != 2:
        print("usage: mojo main.mojo <runtime-shim-library>")
        raise Error("missing runtime shim library path")

    print("coakka_runtime_mojo_basic starting")
    var lib = OwnedDLHandle(String(args[1]))
    var status = lib.call["coakka_mojo_runtime_basic", c_int](0)
    if status != 0:
        raise Error("coakka_mojo_runtime_basic failed")
    print("coakka_runtime_mojo_basic done")
