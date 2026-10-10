import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android: AGP 9 has built-in Kotlin support and rejects it.
    alias(libs.plugins.kotlin.compose)
}

val signingPropsFile = rootProject.file("signing.properties")
val hasSigning = signingPropsFile.exists()

android {
    namespace = "id.anemona.clicker"
    // 37: required by miuix-ui-android 0.9.4's AAR metadata (minCompileSdk 37).
    // compileSdk is independent of targetSdk/minSdk — those stay put.
    compileSdk = 37

    defaultConfig {
        applicationId = "id.anemona.clicker"
        // 26: TYPE_APPLICATION_OVERLAY minimum; dispatchGesture needs 24+.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasSigning) {
            val props = Properties().apply { load(signingPropsFile.inputStream()) }
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Real keystore when signing.properties exists, debug key otherwise.
            signingConfig = if (hasSigning) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 built-in Kotlin: configure the compiler via the kotlin extension.
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // MIUIX — MIUI-style Compose UI library (theme + components).
    implementation(libs.miuix.ui.android)
}
