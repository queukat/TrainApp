import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    jacoco
//    alias(libs.plugins.google.services)
}

android {
    namespace = "com.queukat.train"
    compileSdk = 36

    val signingProps =
        Properties().apply {
            val file = rootProject.file("keystore.properties")
            if (file.exists()) {
                file.inputStream().use(::load)
            }
        }

    fun signingValue(key: String): String? =
        System
            .getenv("TRAINAPP_$key")
            ?.takeIf { it.isNotBlank() }
            ?: signingProps.getProperty(key)?.takeIf { it.isNotBlank() }

    val releaseKeystoreFile = signingValue("KEYSTORE_FILE")
    val releaseStorePassword = signingValue("KEYSTORE_PASSWORD")
    val releaseKeyAlias = signingValue("KEY_ALIAS")
    val releaseKeyPassword = signingValue("KEY_PASSWORD")
    val hasReleaseSigning =
        listOf(
            releaseKeystoreFile,
            releaseStorePassword,
            releaseKeyAlias,
            releaseKeyPassword,
        ).all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "com.queukat.train"
        minSdk = 24
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 127
        versionName = System.getenv("VERSION_NAME") ?: "1.0.8"

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
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("Release signing config not provided; assembling an unsigned release artifact.")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        disable +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "OldTargetApi",
            )
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    basePath = rootDir.absolutePath
}

ktlint {
    android.set(true)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val coverageClassExclusions =
    listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/*ComposableSingletons*.*",
        "**/*\$Companion.*",
        "**/*\$DefaultImpls.*",
        "**/*\$WhenMappings.*",
        "**/*_Impl*.*",
        "**/*_Factory*.*",
        "**/*Database_Impl*.*",
        "**/*Dao_Impl*.*",
    )

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    group = "verification"
    description = "Generates JaCoCo coverage reports for debug unit tests."

    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val debugClassDirectories =
        listOf(
            "tmp/kotlin-classes/debug",
            "intermediates/javac/debug/classes",
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ).map { relativePath ->
            fileTree(layout.buildDirectory.dir(relativePath)) {
                exclude(coverageClassExclusions)
            }
        }

    classDirectories.setFrom(debugClassDirectories)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)

    ksp(libs.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.monitor)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.uiautomator)
}
