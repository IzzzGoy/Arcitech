import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties


plugins {
    kotlin("multiplatform") version "2.1.0"
    id("com.android.library")
    `maven-publish`
    signing
}

group = "com.ndmatrix.parameter"
version = "1.0.1"

kotlin {

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
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

    jvm {

    }

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
    namespace = "com.ndmatrix.parameter"

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
} else {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

fun getExtraString(name: String) = ext[name]?.toString()

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        withType<MavenPublication> {
            groupId = "io.github.izzzgoy"
            artifactId = when (name) {
                "androidRelease" -> "${project.name}-android"
                "jvm" -> "${project.name}-jvm"
                "iosX64" -> "${project.name}-iosX64"
                "iosArm64" -> "${project.name}-iosArm64"
                "iosSimulatorArm64" -> "${project.name}-iosSimulatorArm64"
                else -> project.name
            }
            version = "1.0.1"

            // Stub javadoc.jar artifact
            artifact(tasks["javadocJar"])
            // Provide artifacts information requited by Maven Central
            pom {
                name.set("NDM Achitect")
                description.set("Architecture component system")
                url.set("https://github.com/IzzzGoy/Arcitech")

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
                    url.set("https://github.com/IzzzGoy/Arcitech")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
        mavenLocal()
    }
}

signing {
    sign(publishing.publications)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}