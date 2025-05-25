import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

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
                // DO NOT TOUCH VERSION
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        iosMain {

        }

        iosSimulatorArm64 {

        }

        iosArm64Main {

        }

        iosX64Main {

        }

        androidMain {

        }
    }
}

android {
    compileSdk = 31
    namespace = "com.ndmatrix.core"

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
            androidVariantsToPublish = listOf("debug", "release"),
        )
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(
        rootProject.group.toString(),
        "ndimmatrix-$name",
        rootProject.version.toString()
    )

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
