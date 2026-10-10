// Top-level build file for the Clicker project.
plugins {
    alias(libs.plugins.android.application) apply false
    // No kotlin-android: AGP 9 has built-in Kotlin support and rejects it.
    alias(libs.plugins.kotlin.compose) apply false
}
