from std.ffi import OwnedDLHandle, c_int
from std.sys import argv


def main() raises:
    args = argv()
    if len(args) != 2:
        print("usage: mojo main.mojo <sample-shim-library>")
        raise Error("missing sample shim library path")

    var lib = OwnedDLHandle(args[1])
    var status = lib.call["coakka_mojo_basic_run", c_int](0)
    if status != 0:
        raise Error("coakka_mojo_basic_run failed")
