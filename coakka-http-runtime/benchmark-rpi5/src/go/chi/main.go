// Command chi-server uses Chi routing without linking another server runtime.
package main

import (
	"github.com/go-chi/chi/v5"
	"github.com/phuong-tran/coakka-samples/coakka-http-runtime/benchmark-rpi5/internal/fixture"
	"net/http"
)

func main() {
	port := fixture.Port("chi")
	router := chi.NewRouter()
	router.Get("/fixed", func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", fixture.ContentType)
		writer.Header().Set("Content-Length", "32")
		writer.WriteHeader(http.StatusOK)
		_, _ = writer.Write(fixture.Body)
	})
	fixture.Serve(router, port)
}
