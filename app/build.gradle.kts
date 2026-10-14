import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.teamomuito.colony"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.teamomuito.colony"
        minSdk = 26
        targetSdk = 35
        val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "2.0.0"
    }

    // The release key is committed on purpose: every build, from any machine, is signed the same way, so an installed
    // copy can be updated. Anyone with this file can sign an app that installs over this one.
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "c56f062f0e3a6b362abcae6d95759975"
            keyAlias = "colony"
            keyPassword = "c56f062f0e3a6b362abcae6d95759975"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sim"))
}
