plugins {
    `kotlin-dsl`
    `maven-publish`
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("org.example:library:1.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    //implementation(project("library"))
}

gradlePlugin {
    plugins {
        create("architect") {
            id = "org.example.architect"
            implementationClass = "org.example.architect.ArchitectPlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}