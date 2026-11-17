# coakka-http-runtime-test

[Read the source and run the tests in coakka-publish](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/runtime-test).

The suite defaults to CoAkka HTTP Runtime SDK 2.3.0 with C ABI 10.

The public C and C++ tests use the same HTTP Runtime SDK that applications
install. They show what happens when a service tries to answer a request it does
not own, is asked to stop while data is still in use, or a request fails.
The test guide explains what each check covers and how to run it.

The installed ABI contract matrix runs HTTP/1.1, HTTP/2 and HTTP/3 on
loopback. Its standalone HTTP/3 WebSocket checks show how to reset one
stream, keep another stream on the same connection usable, and verify
drain, shutdown and port reuse.
