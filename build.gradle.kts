// AGP 9 compiles Kotlin with built-in support and pins KGP 2.2.x.
// Firebase 35.x is built with Kotlin 2.4 metadata, so KGP is upgraded here
// (documented AGP 9 approach: add KGP to the buildscript classpath).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
