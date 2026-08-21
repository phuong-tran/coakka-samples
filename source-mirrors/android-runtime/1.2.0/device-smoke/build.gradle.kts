import java.util.Properties

plugins {
    id("com.android.application") version "9.2.1"
}

val candidateAarPath = providers.gradleProperty("coakkaAndroidAar").orNull
    ?: "../build/outputs/aar/coakka-runtime-android-release.aar"
val candidateAar = file(candidateAarPath)
check(candidateAar.isFile) {
    "missing candidate AAR: ${candidateAar.absolutePath}; run ../gradlew assembleRelease first"
}
val expectedAbi = providers.gradleProperty("coakkaDeviceSmokeAbi").orElse("arm64-v8a").get()
val expectedConnectorSourceCommit = providers.gradleProperty("coakkaConnectorSourceCommit").get()
val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
check(expectedAbi in supportedAbis) { "unsupported coakkaDeviceSmokeAbi=$expectedAbi" }
check(expectedConnectorSourceCommit.matches(Regex("[0-9a-f]{40}"))) {
    "coakkaConnectorSourceCommit must be an exact lowercase commit"
}
val releaseIdentity = Properties().apply {
    file("../release-identity.properties").inputStream().use { input -> load(input) }
}
fun requiredReleaseIdentity(key: String): String =
    checkNotNull(releaseIdentity.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)) {
        "release-identity.properties requires $key"
    }
val expectedConnectorVersion = requiredReleaseIdentity("connector.version")
val expectedCoreVersion = requiredReleaseIdentity("core.version")
val expectedCoreCommit = requiredReleaseIdentity("core.commit")
val expectedNativePackage = "$expectedCoreVersion+$expectedCoreCommit"

android {
    namespace = "coakka.v2.android.devicesmoke"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "coakka.v2.android.devicesmoke"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testProguardFiles("test-proguard-rules.pro")
        buildConfigField("String", "EXPECTED_ABI", "\"$expectedAbi\"")
        buildConfigField("String", "EXPECTED_CONNECTOR_VERSION", "\"$expectedConnectorVersion\"")
        buildConfigField("String", "EXPECTED_CONNECTOR_SOURCE_COMMIT", "\"$expectedConnectorSourceCommit\"")
        buildConfigField("String", "EXPECTED_CORE_VERSION", "\"$expectedCoreVersion\"")
        buildConfigField("String", "EXPECTED_CORE_COMMIT", "\"$expectedCoreCommit\"")
        buildConfigField("String", "EXPECTED_NATIVE_PACKAGE", "\"$expectedNativePackage\"")
        ndk {
            abiFilters += expectedAbi
        }
    }

    testBuildType = "release"

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-smoke-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(files(candidateAar))
    implementation("com.google.protobuf:protobuf-javalite:4.31.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

val releaseMapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
val releaseApk = layout.buildDirectory.file(
    "outputs/apk/release/coakka-android-device-smoke-release.apk",
)

val verifyMinifiedJniNames = tasks.register<Exec>("verifyMinifiedJniNames") {
    group = "verification"
    description = "Verifies that AAR consumer rules preserve the name-based JNI surface under R8."
    dependsOn("assembleRelease")
    inputs.files(releaseMapping, releaseApk, "../scripts/verify-minified-jni.sh")
    outputs.upToDateWhen { false }
    workingDir = projectDir
    commandLine(
        "bash",
        "../scripts/verify-minified-jni.sh",
        releaseMapping.get().asFile.absolutePath,
        releaseApk.get().asFile.absolutePath,
    )
}

tasks.matching { it.name == "connectedReleaseAndroidTest" }.configureEach {
    dependsOn(verifyMinifiedJniNames)
}
