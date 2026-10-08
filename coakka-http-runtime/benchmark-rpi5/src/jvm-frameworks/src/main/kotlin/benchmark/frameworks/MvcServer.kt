package benchmark.frameworks

import org.apache.catalina.startup.Tomcat
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.nio.file.Files
import java.nio.file.Paths

/** Enable ordinary Spring MVC handler mapping and byte-array message conversion. */
@Configuration(proxyBeanMethods = false)
@EnableWebMvc
class MvcConfiguration

/** The framework serializes bytes, not JSON; Content-Length is explicit. */
@RestController
class FixedController {
    @GetMapping("/fixed", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun fixed(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(body.size.toLong()).body(body)
}

/** Spring MVC hosted by embedded Tomcat is one comparison stack, not two rows. */
fun main(args: Array<String>) {
    val base = Files.createDirectories(Paths.get("build/tomcat-work")).toAbsolutePath()
    val tomcat = Tomcat().apply {
        setBaseDir(base.toString())
        setPort(args.single().toInt())
        connector.setProperty("address", "127.0.0.1")
        connector.setProperty("maxThreads", "64")
        connector.setProperty("minSpareThreads", "8")
        connector.setProperty("acceptCount", "256")
    }
    val context = tomcat.addContext("", base.toString())
    val spring = AnnotationConfigWebApplicationContext().apply {
        register(MvcConfiguration::class.java, FixedController::class.java)
    }
    val servlet = Tomcat.addServlet(context, "mvc", DispatcherServlet(spring))
    servlet.loadOnStartup = 1
    servlet.isAsyncSupported = true
    context.addServletMappingDecoded("/", "mvc")
    tomcat.start()
    awaitStop("spring-mvc-tomcat", "servlet;max-workers=64;min-workers=8;nio") {
        try { tomcat.stop() } finally { tomcat.destroy() }
    }
}
