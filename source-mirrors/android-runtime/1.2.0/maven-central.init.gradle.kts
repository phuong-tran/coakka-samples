gradle.afterProject {
    if (path == ":") {
        apply(from = rootDir.resolve("maven-central.gradle.kts"))
    }
}
