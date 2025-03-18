buildscript {
    dependencies {
        classpath(libs.google.services)
        classpath(libs.gson)
    }

    repositories {
        google()
        mavenCentral()
    }
}