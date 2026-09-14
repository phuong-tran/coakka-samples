package main

import (
	"fmt"
	"net/http"
	"os"

	"coakka-http-rpi5-go-comparisons/internal/benchserver"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func main() {
	router := chi.NewRouter()
	router.Use(middleware.Recoverer)
	router.Get("/fixed", func(response http.ResponseWriter, _ *http.Request) {
		benchserver.WriteFixed(response)
	})
	if err := benchserver.Run(router); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
