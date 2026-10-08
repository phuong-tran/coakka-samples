module github.com/phuong-tran/coakka-samples/coakka-http-runtime/benchmark-rpi5

// Gin 1.12 requires Go 1.25. This benchmark-only module does not change the
// independently qualified CoAkka connector's Go 1.23 minimum.
go 1.25.0

require (
	github.com/gin-gonic/gin v1.12.0
	github.com/go-chi/chi/v5 v5.3.2
	github.com/phuong-tran/coakka-http-runtime-go v0.0.0
)
