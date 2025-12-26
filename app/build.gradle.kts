import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.set(listOf(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi"
        ))
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

        resourceConfigurations.addAll(
            listOf("en", "ru", "uk", "it", "fr", "es", "de", "pl", "zh-rCN")
        )

        setProperty("archivesBaseName", "LeanbackOnFire_v$versionName")
    }

    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
//    implementation(libs.local.weather)

    implementation(libs.kotlinx.coroutines.android)
}