import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
//    alias(libs.plugins.google.services)
}

android {
    namespace = "com.queukat.train"
    compileSdk = 35

    val signingProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

    fun signingValue(key: String): String? =
        System.getenv("TRAINAPP_$key")
            ?.takeIf { it.isNotBlank() }
            ?: signingProps.getProperty(key)?.takeIf { it.isNotBlank() }

    val releaseKeystoreFile = signingValue("KEYSTORE_FILE")
    val releaseStorePassword = signingValue("KEYSTORE_PASSWORD")
    val releaseKeyAlias = signingValue("KEY_ALIAS")
    val releaseKeyPassword = signingValue("KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseKeystoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "com.queukat.train"
        minSdk = 24
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 2
        versionName = System.getenv("VERSION_NAME") ?: "1.0.1"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("Release signing config not provided; assembling an unsigned release artifact.")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {


    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildFeatures { buildConfig = true }

}
dependencies {
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    debugImplementation(libs.androidx.ui.tooling)
    ksp(libs.room.compiler)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.compose.pullrefresh)
    implementation(libs.compose.ui.graphics)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.uiautomator)
}


