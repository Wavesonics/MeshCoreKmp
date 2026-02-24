plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.blue.falcon)
            api(libs.napier)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }

}

android {
    namespace = "com.darkrockstudios.libs.meshcorekmp"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }
}

//Publishing your Kotlin Multiplatform library to Maven Central
//https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    coordinates("com.darkrockstudios.libs.meshcorekmp", "MeshCore", providers.gradleProperty("library.version").get())

    pom {
        name = "MeshCoreKmp"
        description = "Kotlin Multiplatform MeshCore companion library"
        url = "https://github.com/Wavesonics/MeshCoreKmp"

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                name.set("Adam Brown")
                id.set("Wavesonics")
                email.set("adamwbrown@gmail.com")
            }
        }

        scm {
            url = "https://github.com/Wavesonics/MeshCoreKmp"
        }
    }
    if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) signAllPublications()
}
