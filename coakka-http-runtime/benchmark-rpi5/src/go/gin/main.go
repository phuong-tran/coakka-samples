// Command gin-server uses Gin's byte response path without a CoAkka dependency.
package main

import (
	"github.com/gin-gonic/gin"
	"github.com/phuong-tran/coakka-samples/coakka-http-runtime/benchmark-rpi5/internal/fixture"
	"net/http"
)

func main() {
	port := fixture.Port("gin")
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.GET("/fixed", func(context *gin.Context) {
		context.Data(http.StatusOK, fixture.ContentType, fixture.Body)
	})
	fixture.Serve(router, port)
}
