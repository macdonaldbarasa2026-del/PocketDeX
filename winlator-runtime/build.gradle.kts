plugins {
    id("com.android.library")
}

val upstreamApp = rootProject.file("third_party/winlator-app/app/src/main")

android {
    namespace = "com.winlator"
    compileSdk = 36
    ndkVersion = "24.0.8215888"

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(upstreamApp.resolve("AndroidManifest.xml"))
            java.setSrcDirs(listOf(upstreamApp.resolve("java")))
            res.setSrcDirs(listOf(upstreamApp.resolve("res")))
            assets.setSrcDirs(listOf(upstreamApp.resolve("assets")))
            assets.exclude("wincomponents/*.tzst", "soundfont/*.sf2")
            jniLibs.setSrcDirs(listOf(upstreamApp.resolve("jniLibs")))
        }
    }

    externalNativeBuild {
        cmake {
            path = upstreamApp.resolve("cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.github.luben:zstd-jni:1.5.2-3@aar")
    implementation("org.tukaani:xz:1.7")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("androidx.lifecycle:lifecycle-process:2.5.1")
}

val preparePocketDeXWinlatorSources by tasks.registering {
    val patchFile = rootProject.file("tools/patches/winlator-pocketdex-scoped-storage.patch")
    val upstreamMain = upstreamApp.resolve("java/com/winlator/MainActivity.java")
    val upstreamUtils = upstreamApp.resolve("java/com/winlator/core/AppUtils.java")

    inputs.file(patchFile)
    doLast {
        if (!upstreamMain.isFile || !upstreamUtils.isFile || !patchFile.isFile) {
            throw GradleException("The pinned Winlator sources or PocketDeX patch are missing.")
        }
        if (upstreamMain.readText().contains("requestAppPermissions()")) {
            exec {
                workingDir = rootProject.file("third_party/winlator-app")
                commandLine("git", "apply", patchFile.absolutePath)
            }
        }
        if (upstreamMain.readText().contains("requestAppPermissions()")
                || !upstreamUtils.readText().contains("configurePocketDeXStorage")) {
            throw GradleException("Winlator private-storage adaptation did not apply cleanly.")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(preparePocketDeXWinlatorSources)
}
