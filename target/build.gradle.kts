plugins {
    kotlin("jvm") version "2.1.0"
    id("org.example.architect")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.example:library:1.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)

    sourceSets {
        main {
            kotlin.srcDir("build/generated/architect")
        }
    }
}

architect {
    packageName = "com.exmaple.target"
}