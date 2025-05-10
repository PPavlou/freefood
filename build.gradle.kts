plugins {
    alias(libs.plugins.android.application) apply false
}

buildscript {
    dependencies {
        classpath(libs.google.services)
        classpath(libs.org.jacoco.core)
        classpath(libs.gson)
    }

    repositories {
        google()
        mavenCentral()
    }
}