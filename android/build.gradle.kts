// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Cycle 5: AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the kotlin-android plugin is gone;
// the Compose compiler plugin still has to match the Kotlin version.
plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
