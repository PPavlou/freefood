plugins {
    alias(libs.plugins.android.application) apply false
    id("java")
}

group = "org.example"
version = "unspecified"

dependencies {
    testImplementation(libs.org.jacoco.core)
    implementation(libs.google.services)
    implementation(libs.gson)
    testImplementation(libs.android.junit5)
}

tasks.test {
    useJUnitPlatform()
}