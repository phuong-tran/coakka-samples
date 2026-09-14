package main

import (
	"fmt"
	"os"

	"coakka-http-rpi5-go-comparisons/internal/benchserver"
	"github.com/gin-gonic/gin"
)

func main() {
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.Use(gin.Recovery())
	router.GET("/fixed", func(context *gin.Context) {
		benchserver.WriteFixed(context.Writer)
	})
	if err := benchserver.Run(router); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
