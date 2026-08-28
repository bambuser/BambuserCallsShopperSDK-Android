// Top-level build file. Plugin classpaths declared via the version catalog
// (gradle/libs.versions.toml) and applied per-module.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
