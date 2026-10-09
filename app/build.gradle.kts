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

    // A release keystore, from the environment (CI decodes it from a secret). Every build must be signed with the same
    // key, or Android refuses to install it over the one already on the device. Without the variables, the build falls
    // back to the debug key, which only works for builds made on the same machine.
    val releaseKeystore = providers.environmentVariable("KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
