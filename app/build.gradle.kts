import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

android {
    namespace = "com.amazon.tv.leanbacklauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amazon.tv.leanbacklauncher"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion,OldTargetApi
        targetSdk = 29
        versionCode = 62
        versionName = "1.62"

        vectorDrawables.useSupportLibrary = true

        // Syntax for adding list of resources in KTS
        resourceConfigurations.addAll(
            listOf("en", "ru", "uk", "it", "fr", "es", "de", "pl", "zh-rCN")
        )

        // Setting the output apk name
        setProperty("archivesBaseName", "LeanbackOnFire_v$versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
        }
    }

    buildTypes {
        release {
            // Note: Lint options inside build types are deprecated in newer AGP versions.
            // Usually, these go into the top-level lint {} block (see below).
            // If you must keep them here specifically for release, strict KTS mapping is difficult
            // without using the top-level block.

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
        // Moved from release build type for better compatibility
        disable += setOf("MissingTranslation", "ResourceType")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.palette.ktx)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Google / Third Party
    implementation(libs.google.guava)
    implementation(libs.google.gson)
    implementation(libs.google.material)

    // LocalWeather
    implementation(libs.local.weather)

    implementation(libs.kotlinx.coroutines.android)
}