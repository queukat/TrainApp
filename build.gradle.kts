plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
//    alias(libs.plugins.google.services)
}

sonar {
    properties {
        property(
            "sonar.projectKey",
            providers.environmentVariable("SONAR_PROJECT_KEY").orElse(rootProject.name).get(),
        )
        property(
            "sonar.projectName",
            providers.environmentVariable("SONAR_PROJECT_NAME").orElse(rootProject.name).get(),
        )
        property(
            "sonar.coverage.exclusions",
            listOf(
                "app/src/main/java/com/queukat/train/MainActivity.kt",
                "app/src/main/java/com/queukat/train/SettingsActivity.kt",
                "app/src/main/java/com/queukat/train/data/api/RetrofitClient.kt",
                "app/src/main/java/com/queukat/train/data/db/AppDatabase.kt",
                "app/src/main/java/com/queukat/train/data/repository/FakeTrainRepository.kt",
                "app/src/main/java/com/queukat/train/data/repository/TrainRepository.kt",
                "app/src/main/java/com/queukat/train/ui/AutoCompleteTextField.kt",
                "app/src/main/java/com/queukat/train/ui/FullRouteDialog.kt",
                "app/src/main/java/com/queukat/train/ui/MainScreen.kt",
                "app/src/main/java/com/queukat/train/ui/PreviewTrainViewModel.kt",
                "app/src/main/java/com/queukat/train/ui/ReminderChoiceDialog.kt",
                "app/src/main/java/com/queukat/train/ui/RouteCard.kt",
                "app/src/main/java/com/queukat/train/ui/SavedRoutesBlock.kt",
                "app/src/main/java/com/queukat/train/ui/SearchPanel.kt",
                "app/src/main/java/com/queukat/train/ui/SettingsScreen.kt",
                "app/src/main/java/com/queukat/train/ui/StatusBanner.kt",
                "app/src/main/java/com/queukat/train/ui/TrainViewModel.kt",
                "app/src/main/java/com/queukat/train/ui/TrainViewModelFactory.kt",
                "app/src/main/java/com/queukat/train/ui/theme/**",
                "app/src/main/java/com/queukat/train/util/AppLocaleOverrides.kt",
                "app/src/main/java/com/queukat/train/util/Dbg.kt",
                "app/src/main/java/com/queukat/train/util/NotificationHelper.kt",
                "app/src/main/java/com/queukat/train/util/ReminderReceiver.kt",
                "app/src/main/java/com/queukat/train/util/ReminderUtils.kt",
            ).joinToString(","),
        )
    }
}

project(":app") {
    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                layout.buildDirectory
                    .file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml")
                    .get()
                    .asFile
                    .absolutePath,
            )
        }
    }
}

tasks.named("sonar") {
    dependsOn(":app:jacocoDebugUnitTestReport")
}


allprojects {
    // Если нужно, репозитории для всех подпроектов
    repositories {
        google()
        mavenCentral()
    }
}
