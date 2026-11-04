# coakka-http-runtime-test

[Read the source and run the tests in coakka-publish](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/runtime-test).

The public C and C++ tests use the same HTTP Runtime SDK that applications
install. They show what happens when a service tries to answer a request it does
not own, is asked to stop while data is still in use, or a request fails.
The test guide explains what each check covers and how to run it.
