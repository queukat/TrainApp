plugins {

    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    // Если нужно, репозитории для всех подпроектов
    repositories {
        google()
        mavenCentral()
    }
}
