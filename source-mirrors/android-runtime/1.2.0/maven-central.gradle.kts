import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

val androidCentralGroup = providers.gradleProperty("coakkaMavenCentralGroup")
    .orElse("io.github.phuong-tran.coakka")
val androidCentralArtifactId = providers.gradleProperty("coakkaMavenCentralArtifactId")
    .orElse("coakka-runtime-android")
val androidCentralNamespaceVerified = providers.gradleProperty("coakkaMavenCentralNamespaceVerified")
    .map(String::toBoolean)
    .orElse(false)
val androidCentralUseGpgAgent = providers.gradleProperty("coakkaMavenUseGpgAgent")
    .map(String::toBoolean)
    .orElse(false)
val androidCentralGpgFingerprint = providers.gradleProperty("signing.gnupg.keyName")
    .map(String::trim)
    .filter(String::isNotEmpty)
val androidCentralLegacySigningKeyPresent =
    providers.gradleProperty("coakkaMavenSigningKey").isPresent ||
        providers.environmentVariable("COAKKA_MAVEN_SIGNING_KEY").isPresent
val androidCentralLegacySigningPasswordPresent =
    providers.gradleProperty("coakkaMavenSigningPassword").isPresent ||
        providers.environmentVariable("COAKKA_MAVEN_SIGNING_PASSWORD").isPresent
val androidSourceRepository = "https://github.com/phuong-tran/coakka-samples"
val androidSourceTag = "android-runtime-1.2.0"
val androidSourcePath = "source-mirrors/android-runtime/1.2.0"
val androidCoreRoot = providers.gradleProperty("coakkaCoreRepository")
    .orElse(providers.environmentVariable("COAKKA_CORE_REPO"))
    .map(::File)
    .orElse(layout.projectDirectory.dir("../../..").asFile)
val androidConnectorRepositoryRoot = providers.exec {
    workingDir(layout.projectDirectory)
    commandLine("git", "rev-parse", "--show-toplevel")
}.standardOutput.asText.map { output -> File(output.trim()) }
val androidSamplesRoot = providers.gradleProperty("coakkaSamplesRepository")
    .orElse(providers.environmentVariable("COAKKA_SAMPLES_REPO"))
    .map(::File)
    .orElse(androidConnectorRepositoryRoot)
val androidSourceRawBase =
    "https://raw.githubusercontent.com/phuong-tran/coakka-samples/" +
        "$androidSourceTag/$androidSourcePath"
val androidCentralRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("AndroidCentral", ignoreCase = true) ||
        taskName.contains("AndroidForMavenCentral", ignoreCase = true)
}
val androidCentralRepositoryDir = layout.buildDirectory.dir("central/repository")
val androidCentralBundleRoot = layout.buildDirectory.dir("central/bundle-root")
val androidCentralRelativePath =
    "${androidCentralGroup.get().replace('.', '/')}/${androidCentralArtifactId.get()}/${project.version}"
val androidCentralArtifactDir = androidCentralBundleRoot.map { it.dir(androidCentralRelativePath) }
val androidAar = layout.buildDirectory.file("outputs/aar/coakka-runtime-android-release.aar")
val androidSources = layout.buildDirectory.file(
    "libs/coakka-runtime-android-${project.version}-sources.jar",
)
val androidJavadoc = layout.buildDirectory.file(
    "libs/coakka-runtime-android-${project.version}-javadoc.jar",
)
val androidPom = layout.buildDirectory.file("publications/release/pom-default.xml")
val androidReleaseIdentityFile = layout.projectDirectory.file("release-identity.properties")
val androidReleaseIdentity = Properties().apply {
    androidReleaseIdentityFile.asFile.inputStream().use { input -> load(input) }
}
fun requiredAndroidReleaseIdentity(key: String): String =
    requireNotNull(androidReleaseIdentity.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)) {
        "release-identity.properties requires $key"
    }
val expectedAndroidConnectorVersion = requiredAndroidReleaseIdentity("connector.version")
val expectedAndroidCoreVersion = requiredAndroidReleaseIdentity("core.version")
val expectedAndroidCoreCommit = requiredAndroidReleaseIdentity("core.commit")
val expectedAndroidAbis = requiredAndroidReleaseIdentity("android.abis").split(',')
val expectedAndroidNativePackage = "$expectedAndroidCoreVersion+$expectedAndroidCoreCommit"

fun androidCentralDigest(bytes: ByteArray, algorithm: String): String =
    MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

fun androidCentralDigest(file: File, algorithm: String): String =
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

fun androidCentralGit(directory: File, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(directory)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("git ${arguments.joinToString(" ")} failed: $output")
    }
    return output
}

fun verifyAndroidCentralSourcePolicy(
    repository: String,
    tag: String,
    path: String,
    connectorVersion: String,
) {
    if (repository != "https://github.com/phuong-tran/coakka-samples" ||
        tag != "android-runtime-$connectorVersion" ||
        path != "source-mirrors/android-runtime/$connectorVersion"
    ) {
        error(
            "Android Central SCM must bind connector $connectorVersion to the official " +
                "coakka-samples repository, android-runtime-$connectorVersion tag, and " +
                "source-mirrors/android-runtime/$connectorVersion subtree.",
        )
    }
}

fun verifyAndroidCentralSigningPolicy(
    useGpgAgent: Boolean,
    fingerprint: String?,
    legacySigningKeyPresent: Boolean,
    legacySigningPasswordPresent: Boolean,
) {
    if (legacySigningKeyPresent || legacySigningPasswordPresent) {
        error(
            "Android Central signing rejects private key and passphrase " +
                "properties/environment variables; keep the secret key in the local GPG agent " +
                "or OS keychain.",
        )
    }
    if (!useGpgAgent) {
        error("Android Central signing requires -PcoakkaMavenUseGpgAgent=true.")
    }
    if (fingerprint == null) {
        error(
            "Android Central GPG-agent signing requires " +
                "-Psigning.gnupg.keyName=<40-character-fingerprint>.",
        )
    }
    if (!Regex("[0-9A-Fa-f]{40}").matches(fingerprint)) {
        error("Android Central signing key must be an explicit 40-character fingerprint.")
    }
}

fun androidCentralRequirePeeledTagCommit(remoteTag: String, sourceTag: String): String {
    val rows = linkedMapOf<String, String>()
    remoteTag.lineSequence()
        .map { row -> row.trim().split(Regex("\\s+"), limit = 2) }
        .filter { row -> row.size == 2 && row[0].matches(Regex("[0-9a-f]{40}")) }
        .forEach { row ->
            if (rows.put(row[1], row[0]) != null) {
                error("public Android source tag response contains duplicate ref ${row[1]}")
            }
        }
    val directRef = "refs/tags/$sourceTag"
    val peeledRef = "$directRef^{}"
    if (directRef !in rows) {
        error("public Android source tag does not exist: $sourceTag")
    }
    return rows[peeledRef]
        ?: error(
            "public Android source tag $sourceTag must be annotated and expose exact peeled ref $peeledRef",
        )
}

fun androidCentralGet(url: String, maxBytes: Int): ByteArray {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.requestMethod = "GET"
    try {
        if (connection.responseCode != 200) {
            error("public Android source URL returned HTTP ${connection.responseCode}: $url")
        }
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > maxBytes) {
                    error("public Android source file exceeds $maxBytes bytes: $url")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}

fun androidCentralChecksums(): Map<String, String> = linkedMapOf(
    "md5" to "MD5",
    "sha1" to "SHA-1",
    "sha256" to "SHA-256",
    "sha512" to "SHA-512",
)

extensions.configure<PublishingExtension> {
    repositories {
        maven {
            name = "centralBundleStaging"
            url = androidCentralRepositoryDir.get().asFile.toURI()
        }
    }
}

extensions.configure<SigningExtension> {
    if (androidCentralRequested && androidCentralUseGpgAgent.get()) {
        useGpgCmd()
        sign(extensions.getByType<PublishingExtension>().publications["release"])
    }
}

val verifyAndroidCentralReleaseConfiguration = tasks.register(
    "verifyAndroidCentralReleaseConfiguration",
) {
    group = "verification"
    description = "Rejects mutable SCM coordinates and secret-bearing signing inputs before release tasks."
    inputs.property("connectorVersion", expectedAndroidConnectorVersion)
    inputs.property("namespaceVerified", androidCentralNamespaceVerified)
    inputs.property("gpgAgentEnabled", androidCentralUseGpgAgent)
    inputs.property("gpgFingerprint", androidCentralGpgFingerprint.orElse(""))
    inputs.property("legacySigningKeyPresent", androidCentralLegacySigningKeyPresent)
    inputs.property("legacySigningPasswordPresent", androidCentralLegacySigningPasswordPresent)
    doLast {
        if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(project.version.toString()) ||
            project.version.toString() != expectedAndroidConnectorVersion
        ) {
            error(
                "Android Central requires release version $expectedAndroidConnectorVersion; " +
                    "got '${project.version}'.",
            )
        }
        if (!androidCentralNamespaceVerified.get()) {
            error(
                "Maven Central namespace '${androidCentralGroup.get()}' is not acknowledged; " +
                    "pass -PcoakkaMavenCentralNamespaceVerified=true only after Portal verification.",
            )
        }
        verifyAndroidCentralSourcePolicy(
            androidSourceRepository,
            androidSourceTag,
            androidSourcePath,
            expectedAndroidConnectorVersion,
        )
        verifyAndroidCentralSigningPolicy(
            useGpgAgent = androidCentralUseGpgAgent.get(),
            fingerprint = androidCentralGpgFingerprint.orNull,
            legacySigningKeyPresent = androidCentralLegacySigningKeyPresent,
            legacySigningPasswordPresent = androidCentralLegacySigningPasswordPresent,
        )
    }
}

tasks.register("testAndroidCentralPreconditionPolicy") {
    group = "verification"
    description = "Exercises negative SCM, annotated-tag, and signing policy cases without building artifacts."
    doLast {
        fun expectFailure(label: String, expected: String, block: () -> Unit) {
            val failure = runCatching(block).exceptionOrNull()
                ?: error("$label unexpectedly passed")
            check(failure.message?.contains(expected) == true) {
                "$label returned an unexpected diagnostic: ${failure.message}"
            }
            logger.lifecycle("[android-central-policy] $label: rejected as expected")
        }

        val fingerprint = "2FBD20F919F251E8D984A5EBF90740BDDBBE6638"
        val officialRepository = "https://github.com/phuong-tran/coakka-samples"
        val officialTag = "android-runtime-1.2.0"
        val officialPath = "source-mirrors/android-runtime/1.2.0"
        expectFailure("alternate repository", "official coakka-samples repository") {
            verifyAndroidCentralSourcePolicy(
                "https://example.invalid/coakka-samples",
                officialTag,
                officialPath,
                "1.2.0",
            )
        }
        expectFailure("alternate tag", "android-runtime-1.2.0 tag") {
            verifyAndroidCentralSourcePolicy(
                officialRepository,
                "android-runtime-1.2.0-fork",
                officialPath,
                "1.2.0",
            )
        }
        expectFailure("alternate subtree", "source-mirrors/android-runtime/1.2.0 subtree") {
            verifyAndroidCentralSourcePolicy(
                officialRepository,
                officialTag,
                "source-mirrors/android-runtime/latest",
                "1.2.0",
            )
        }
        expectFailure("GPG agent disabled", "requires -PcoakkaMavenUseGpgAgent=true") {
            verifyAndroidCentralSigningPolicy(false, fingerprint, false, false)
        }
        expectFailure("missing fingerprint", "<40-character-fingerprint>") {
            verifyAndroidCentralSigningPolicy(true, null, false, false)
        }
        expectFailure("16-character key ID", "explicit 40-character fingerprint") {
            verifyAndroidCentralSigningPolicy(true, "90740BDDBBE6638", false, false)
        }
        expectFailure("legacy private key input", "rejects private key and passphrase") {
            verifyAndroidCentralSigningPolicy(true, fingerprint, true, false)
        }
        expectFailure("legacy passphrase input", "rejects private key and passphrase") {
            verifyAndroidCentralSigningPolicy(true, fingerprint, false, true)
        }
        val lightweightTag = "${"a".repeat(40)}\trefs/tags/$officialTag"
        expectFailure("lightweight tag", "must be annotated") {
            androidCentralRequirePeeledTagCommit(lightweightTag, officialTag)
        }
        val peeledCommit = "b".repeat(40)
        val annotatedTag =
            "${"a".repeat(40)}\trefs/tags/$officialTag\n" +
                "$peeledCommit\trefs/tags/$officialTag^{}"
        check(androidCentralRequirePeeledTagCommit(annotatedTag, officialTag) == peeledCommit)
        verifyAndroidCentralSourcePolicy(officialRepository, officialTag, officialPath, "1.2.0")
        verifyAndroidCentralSigningPolicy(true, fingerprint, false, false)
        logger.lifecycle("[android-central-policy] all negative cases failed before signing or publication")
    }
}

val verifyAndroidCentralShape = tasks.register("verifyAndroidCentralShape") {
    group = "verification"
    description = "Checks the unsigned Android Central artifact shape without opening the release gate."
    mustRunAfter(verifyAndroidCentralReleaseConfiguration)
    dependsOn(
        "assembleRelease",
        "androidConnectorSourcesJar",
        "androidConnectorJavadocJar",
        "generatePomFileForReleasePublication",
    )
    inputs.files(androidAar, androidSources, androidJavadoc, androidPom)
    doLast {
        val aarFile = androidAar.get().asFile
        val sourcesFile = androidSources.get().asFile
        val javadocFile = androidJavadoc.get().asFile
        val pomFile = androidPom.get().asFile
        listOf(aarFile, sourcesFile, javadocFile, pomFile).forEach { artifact ->
            if (!artifact.isFile || artifact.length() == 0L) {
                error("missing or empty Android Central artifact: ${artifact.absolutePath}")
            }
        }

        ZipFile(aarFile).use { zip ->
            val nativeEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.matches(Regex("jni/[^/]+/libcoakka_(runtime_v2|android_jni)\\.so")) }
                .map { it.name }
                .toSet()
            val expectedNative = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                .flatMap { abi ->
                    listOf(
                        "jni/$abi/libcoakka_runtime_v2.so",
                        "jni/$abi/libcoakka_android_jni.so",
                    )
                }
                .toSet()
            if (nativeEntries != expectedNative) {
                error("Android AAR native inventory drifted: ${nativeEntries.sorted()}")
            }
            val legal = setOf(
                "assets/LICENSE",
                "assets/NATIVE-LICENSE.md",
                "assets/PACKAGE-LICENSE.md",
                "assets/NOTICE",
            )
            val missingLegal = legal.filter { zip.getEntry(it) == null }
            if (missingLegal.isNotEmpty()) {
                error("Android AAR is missing legal files: ${missingLegal.joinToString()}")
            }
            val metadataEntry = zip.getEntry("assets/coakka/runtime-package.json")
                ?: error("Android AAR is missing runtime-package.json")
            val metadata = JsonSlurper().parse(zip.getInputStream(metadataEntry)) as Map<*, *>
            if (metadata["schema_version"] != 2 ||
                metadata["connector_version"] != expectedAndroidConnectorVersion ||
                metadata["bundled_native_git_commit"] != expectedAndroidCoreCommit ||
                metadata["bundled_native_package_version"] != expectedAndroidNativePackage ||
                metadata["core_source_git_commit"] != expectedAndroidCoreCommit ||
                metadata["connector_source_git_commit"] !is String ||
                metadata["connector_source_tree_dirty"] !is Boolean ||
                metadata["core_checkout_git_commit"] !is String ||
                metadata["core_source_tree_dirty"] !is Boolean ||
                metadata["source_tree_dirty"] !is Boolean ||
                metadata["included_android_abis"] != expectedAndroidAbis ||
                metadata["native_source_verified"] != true
            ) {
                error("Android AAR runtime identity drifted: $metadata")
            }
        }

        ZipFile(sourcesFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val required = setOf(
                "coakka/v2/android/FileLane.kt",
                "coakka/v2/android/StreamLane.kt",
                "coakka/v2/android/LaneOwnerGrant.kt",
                "src/main/cpp/coakka_android_file_lane_jni.cpp",
                "src/main/cpp/coakka_android_stream_lane_jni.cpp",
                "src/hostTest/cpp/coakka_android_host_test_jni.cpp",
                "host-test/CMakeLists.txt",
                "release-identity.properties",
                "scripts/build-host-jni.sh",
                "scripts/load-release-identity.sh",
                "scripts/verify-release-source-identity.sh",
                "SYSTEMS-AUDIT.md",
                "LICENSE",
                "NATIVE-LICENSE.md",
                "PACKAGE-LICENSE.md",
                "NOTICE",
            )
            val missing = required - entries
            if (missing.isNotEmpty()) {
                error("Android sources jar is missing: ${missing.sorted().joinToString()}")
            }
        }

        ZipFile(javadocFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val required = setOf(
                "coakka/v2/android/FileLane.html",
                "coakka/v2/android/StreamLane.html",
                "META-INF/LICENSE",
                "META-INF/COAKKA-NATIVE-LICENSE.md",
                "META-INF/PACKAGE-LICENSE.md",
                "META-INF/NOTICE",
            )
            val missing = required - entries
            if (missing.isNotEmpty() || entries.none { it.endsWith(".html") }) {
                error("Android Javadoc jar is incomplete: ${missing.sorted().joinToString()}")
            }
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val pom = factory.newDocumentBuilder().parse(pomFile)
        fun requirePom(localName: String, expected: String) {
            val nodes = pom.getElementsByTagNameNS("*", localName)
            val values = (0 until nodes.length).map { nodes.item(it).textContent.trim() }
            if (expected !in values) {
                error("Android POM is missing $localName='$expected'; found ${values.joinToString()}")
            }
        }
        requirePom("groupId", androidCentralGroup.get())
        requirePom("artifactId", androidCentralArtifactId.get())
        requirePom("version", project.version.toString())
        requirePom("tag", androidSourceTag)
        requirePom("name", "CoAkka Package License Map (free for application use)")
        if (pom.getElementsByTagNameNS("*", "repositories").length != 0) {
            error("Android Central POM must not declare dependency repositories")
        }
    }
}

val verifyAndroidCentralPreconditions = tasks.register("verifyAndroidCentralPreconditions") {
    group = "verification"
    description = "Fails closed unless clean source, public tag, namespace, and signing inputs are exact."
    dependsOn(verifyAndroidCentralReleaseConfiguration, verifyAndroidCentralShape)
    doLast {
        val connectorRoot = File(
            androidCentralGit(layout.projectDirectory.asFile, "rev-parse", "--show-toplevel"),
        )
        val connectorStatus = androidCentralGit(
            connectorRoot,
            "status",
            "--porcelain",
            "--untracked-files=normal",
        )
        if (connectorStatus.isNotBlank()) {
            error("Android Central AAR requires a clean connector source tree:\n$connectorStatus")
        }
        val connectorCommit = androidCentralGit(connectorRoot, "rev-parse", "HEAD")

        val coreRoot = androidCoreRoot.get().absoluteFile
        val coreStatus = androidCentralGit(
            coreRoot,
            "status",
            "--porcelain",
            "--untracked-files=normal",
        )
        if (coreStatus.isNotBlank()) {
            error("Android Central AAR requires a clean Core source tree:\n$coreStatus")
        }
        val coreCheckoutCommit = androidCentralGit(coreRoot, "rev-parse", "HEAD")
        if (coreCheckoutCommit != expectedAndroidCoreCommit) {
            error(
                "Android Central AAR requires Core checkout $expectedAndroidCoreCommit; " +
                    "got $coreCheckoutCommit.",
            )
        }
        val packagedConnectorCommit = ZipFile(androidAar.get().asFile).use { zip ->
            val metadata = JsonSlurper().parse(
                zip.getInputStream(zip.getEntry("assets/coakka/runtime-package.json")),
            ) as Map<*, *>
            if (metadata["source_tree_dirty"] != false ||
                metadata["connector_source_tree_dirty"] != false ||
                metadata["core_source_tree_dirty"] != false ||
                metadata["connector_source_git_commit"] != connectorCommit ||
                metadata["core_source_git_commit"] != expectedAndroidCoreCommit ||
                metadata["core_checkout_git_commit"] != expectedAndroidCoreCommit
            ) {
                error(
                    "Android AAR is not bound to clean connector=$connectorCommit and " +
                        "Core=$expectedAndroidCoreCommit: $metadata",
                )
            }
            metadata["connector_source_git_commit"] as String
        }

        val samplesRoot = androidSamplesRoot.get().absoluteFile
        val sourceRelativePath = androidSourcePath
        val localMirror = samplesRoot.resolve(sourceRelativePath)
        val samplesStatus = androidCentralGit(
            samplesRoot,
            "status",
            "--porcelain",
            "--untracked-files=all",
            "--",
            sourceRelativePath,
        )
        if (samplesStatus.isNotBlank()) {
            error("Android public source mirror must be committed before tagging:\n$samplesStatus")
        }
        val remoteTag = androidCentralGit(
            samplesRoot,
            "ls-remote",
            "--tags",
            "$androidSourceRepository.git",
            "refs/tags/$androidSourceTag",
            "refs/tags/$androidSourceTag^{}",
        )
        if (remoteTag.isBlank()) {
            error("public Android source tag does not exist: $androidSourceTag")
        }
        val remoteSourceCommit = androidCentralRequirePeeledTagCommit(remoteTag, androidSourceTag)
        if (connectorCommit != remoteSourceCommit || packagedConnectorCommit != remoteSourceCommit) {
            error(
                "Android AAR connector source must be built from public tag commit " +
                    "$remoteSourceCommit; project=$connectorCommit package=$packagedConnectorCommit.",
            )
        }

        val localManifest = localMirror.resolve("SOURCE-MANIFEST.sha256")
        if (!localManifest.isFile) {
            error("missing local Android source manifest: ${localManifest.absolutePath}")
        }
        val remoteManifest = androidCentralGet(
            "$androidSourceRawBase/SOURCE-MANIFEST.sha256",
            256 * 1024,
        )
        if (!remoteManifest.contentEquals(localManifest.readBytes())) {
            error("public Android source manifest bytes drifted from the local projection")
        }
        localManifest.readLines().filter(String::isNotBlank).forEach { line ->
            val match = Regex("^([0-9a-f]{64})  (.+)$").matchEntire(line)
                ?: error("invalid Android source manifest row: '$line'")
            val expected = match.groupValues[1]
            val relative = match.groupValues[2]
            val localFile = localMirror.resolve(relative)
            if (!localFile.isFile || androidCentralDigest(localFile, "SHA-256") != expected) {
                error("local Android source mirror drifted for $relative")
            }
            val remoteFile = androidCentralGet(
                "$androidSourceRawBase/$relative",
                4 * 1024 * 1024,
            )
            if (androidCentralDigest(remoteFile, "SHA-256") != expected) {
                error("public Android source bytes drifted for $relative")
            }
        }
        listOf("README.md", "LICENSE", "NOTICE").forEach { name ->
            val local = localMirror.resolve(name)
            val remote = androidCentralGet("$androidSourceRawBase/$name", 256 * 1024)
            if (!local.isFile || !remote.contentEquals(local.readBytes())) {
                error("public Android source support file drifted: $name")
            }
        }
    }
}

if (androidCentralRequested && androidCentralUseGpgAgent.get()) {
    tasks.named<Sign>("signReleasePublication") {
        dependsOn(verifyAndroidCentralPreconditions)
    }
}

val publishAndroidCentral = tasks.named<PublishToMavenRepository>(
    "publishReleasePublicationToCentralBundleStagingRepository",
) {
    dependsOn(verifyAndroidCentralPreconditions)
}

val stageAndroidCentralBundle = tasks.register<Sync>("stageAndroidCentralBundle") {
    group = "distribution"
    description = "Stages the signed Android publication and writes Central checksums."
    dependsOn(publishAndroidCentral)
    val prefix = "${androidCentralArtifactId.get()}-${project.version}"
    val repositoryArtifactDir = androidCentralRepositoryDir.map {
        it.dir(androidCentralRelativePath)
    }
    from(repositoryArtifactDir) {
        include("$prefix.pom")
        include("$prefix.aar")
        include("$prefix-sources.jar")
        include("$prefix-javadoc.jar")
        include("$prefix.module")
        include("$prefix.pom.asc")
        include("$prefix.aar.asc")
        include("$prefix-sources.jar.asc")
        include("$prefix-javadoc.jar.asc")
        include("$prefix.module.asc")
        into(androidCentralRelativePath)
    }
    into(androidCentralBundleRoot)
    doLast {
        val artifactDir = androidCentralArtifactDir.get().asFile
        val published = artifactDir.listFiles()?.filter(File::isFile)?.sortedBy(File::getName)
            ?: emptyList()
        if (published.isEmpty()) {
            error("Android Central staging produced no files in ${artifactDir.absolutePath}")
        }
        published.forEach { artifact ->
            androidCentralChecksums().forEach { (suffix, algorithm) ->
                artifactDir.resolve("${artifact.name}.$suffix")
                    .writeText(androidCentralDigest(artifact, algorithm) + "\n")
            }
        }
    }
}

val verifyAndroidCentralBundle = tasks.register("verifyAndroidCentralBundle") {
    group = "verification"
    description = "Validates signed Android Central artifacts, checksums, and exact layout."
    dependsOn(stageAndroidCentralBundle)
    inputs.dir(androidCentralBundleRoot)
    doLast {
        val artifactDir = androidCentralArtifactDir.get().asFile
        val prefix = "${androidCentralArtifactId.get()}-${project.version}"
        val baseNames = listOf(
            "$prefix.pom",
            "$prefix.aar",
            "$prefix-sources.jar",
            "$prefix-javadoc.jar",
            "$prefix.module",
        )
        val checksums = androidCentralChecksums()
        baseNames.forEach { baseName ->
            val artifact = artifactDir.resolve(baseName)
            val signature = artifactDir.resolve("$baseName.asc")
            if (!artifact.isFile || artifact.length() == 0L ||
                !signature.isFile || signature.length() == 0L ||
                !signature.readText().contains("BEGIN PGP SIGNATURE")
            ) {
                error("missing artifact or ASCII-armored signature for $baseName")
            }
            listOf(artifact, signature).forEach { signedFile ->
                checksums.forEach { (suffix, algorithm) ->
                    val expected = androidCentralDigest(signedFile, algorithm)
                    val actual = artifactDir.resolve("${signedFile.name}.$suffix")
                        .takeIf(File::isFile)?.readText()?.trim()
                    if (actual != expected) {
                        error("invalid $algorithm checksum for ${signedFile.name}")
                    }
                }
            }
        }
        val expectedNames = baseNames.flatMap { baseName ->
            listOf(baseName, "$baseName.asc") +
                checksums.keys.flatMap { suffix ->
                    listOf("$baseName.$suffix", "$baseName.asc.$suffix")
                }
        }.toSet()
        val actualNames = artifactDir.listFiles()?.filter(File::isFile)?.map(File::getName)?.toSet()
            ?: emptySet()
        if (actualNames != expectedNames) {
            error(
                "Android Central bundle layout drifted; missing=${(expectedNames - actualNames).sorted()} " +
                    "unexpected=${(actualNames - expectedNames).sorted()}",
            )
        }
        val misplaced = androidCentralBundleRoot.get().asFile.walkTopDown()
            .filter(File::isFile)
            .filter { it.parentFile.canonicalFile != artifactDir.canonicalFile }
            .map { it.relativeTo(androidCentralBundleRoot.get().asFile).invariantSeparatorsPath }
            .toList()
        if (misplaced.isNotEmpty()) {
            error("Android Central files escaped the coordinate directory: ${misplaced.joinToString()}")
        }
    }
}

tasks.register<Zip>("bundleAndroidForMavenCentral") {
    group = "distribution"
    description = "Builds the verified user-managed Android Maven Central upload bundle."
    dependsOn(verifyAndroidCentralBundle)
    from(androidCentralBundleRoot)
    destinationDirectory.set(layout.buildDirectory.dir("central"))
    archiveFileName.set(
        "${androidCentralArtifactId.get()}-${project.version}-central-bundle.zip",
    )
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    doLast {
        val bundle = archiveFile.get().asFile
        if (bundle.length() > 1024L * 1024L * 1024L) {
            error("Android Maven Central bundle exceeds the 1 GiB portal limit")
        }
    }
}
