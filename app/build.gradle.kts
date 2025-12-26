@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.apk.dist)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
}

android {
    namespace = "com.amazon.tv.leanbacklauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amazon.tv.leanbacklauncher"
        minSdk = 23
        //noinspection ExpiredTargetSdkVersion,OldTargetApi
        targetSdk = 36
        versionCode = 72
        versionName = "2.0.2"

        vectorDrawables.useSupportLibrary = true

        androidResources.localeFilters.addAll(
            listOf("en", "ru", "uk", "it", "fr", "es", "de", "pl", "zh-rCN")
        )
    }


    val enableApkSplits = (providers.gradleProperty("enableApkSplits").orNull ?: "true").toBoolean()
    val includeUniversalApk =
        (providers.gradleProperty("includeUniversalApk").orNull ?: "false").toBoolean()
    val targetAbi = providers.gradleProperty("targetAbi").orNull

    splits {
        abi {
            isEnable = enableApkSplits
            reset()
            if (enableApkSplits) {
                if (targetAbi != null) {
                    include(targetAbi)
                } else {
                    include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                }
            }
            isUniversalApk = includeUniversalApk
        }
    }

    signingConfigs {
        create("release") {
            storeFile =
                file(System.getenv("KEYSTORE_PATH") ?: "${rootProject.projectDir}/release.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
            enableV4Signing = false
        }
    }


    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isShrinkResources = false
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Long", "BUILD_TIME", "${System.currentTimeMillis()}L")
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

    dependenciesInfo {
        includeInApk = false
    }
}

apkDist {
    artifactNamePrefix.set("forbendlauncher")
}


dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")

    implementation("androidx.fragment:fragment-ktx:1.8.5")
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