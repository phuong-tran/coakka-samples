plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "coakka-samples"

include("logger:jvm:basic")
include("logger:jvm:java-basic")
include("logger:jvm:pressure")
include("logger:jvm:java-pressure")
include("runtime:jvm:basic")
include("runtime:jvm:java-basic")
include("runtime:jvm:deadletter")
include("runtime:jvm:java-deadletter")
include("runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract")
include("runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-web")
include("runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-store")
include("runtime:scenarios:customer-crud:spring-boot-single-process:customer-app")
include("runtime:scenarios:customer-crud:spring-boot-starter-local:customer-app")
include("runtime:scenarios:customer-crud:kotlin-desktop-local:customer-desktop")
