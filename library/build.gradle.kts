import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("multiplatform") version "2.1.0"
    id("com.android.library")
    `maven-publish`
}

group = "org.example"
version = "1.0-SNAPSHOT"

kotlin {

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "library"
        }
    }

    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        iosMain {

        }

        androidMain {

        }
    }
}

android {
    compileSdk = 31
    namespace = "com.homecraft.familylist"

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        withType<MavenPublication> {
            groupId = "org.example"
            artifactId = project.name
            version = "1.0-SNAPSHOT"
            println("$groupId:$artifactId:$version")

            // Stub javadoc.jar artifact
            artifact(javadocJar.get())

            // Provide artifacts information requited by Maven Central
            pom {
                name.set("NDM Achitect")
                description.set("Architecture component system")
                url.set("https://github.com/IzzzGoy/event-thread")

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("FromGoy")
                        name.set("Alexey")
                        email.set("xzadmoror@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/IzzzGoy/event-thread")
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
