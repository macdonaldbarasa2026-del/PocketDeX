plugins {
    id("com.android.application")
}

android {
    namespace = "com.pocketdex.launcher"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.pocketdex.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.2.0"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.18.0")
}
