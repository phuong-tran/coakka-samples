package main

import (
	"fmt"
	"net/http"
	"os"

	"coakka-http-rpi5-go-comparisons/internal/benchserver"
)

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /fixed", func(response http.ResponseWriter, _ *http.Request) {
		benchserver.WriteFixed(response)
	})
	if err := benchserver.Run(mux); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
