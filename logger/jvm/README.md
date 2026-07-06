# JVM Logger Samples

JVM samples consume the published `coakka-jvm-native-logger` jar from the
static Maven repository in `coakka-publish`.

Gradle dependency shape:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/maven")
    }
}

dependencies {
    implementation("coakka.logger:coakka-jvm-native-logger:1.2.1-gf50756ebff0d")
}
```

Current samples:

- `basic`: load the embedded native logger, print version info, emit one record,
  drain it, and print basic counters
- `java-basic`: the same one-record flow using Java source
- `pressure`: fill a queue with capacity `2`, observe rejected writes, drain the
  accepted records, and print dropped counters
- `java-pressure`: the same bounded queue pressure flow using Java source

Run:

```sh
./gradlew :logger:jvm:basic:run
./gradlew :logger:jvm:java-basic:run
./gradlew :logger:jvm:pressure:run
./gradlew :logger:jvm:java-pressure:run
```

Each JVM sample directory also has a local wrapper:

```sh
cd logger/jvm/basic && bash run.sh
cd logger/jvm/java-basic && bash run.sh
cd logger/jvm/pressure && bash run.sh
cd logger/jvm/java-pressure && bash run.sh
```

For IDE runs, open/import the `coakka-samples` repository root as the Gradle
project. Opening only a leaf sample directory leaves the JVM sample without the
root Gradle wrapper and included-project settings.
