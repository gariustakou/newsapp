import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // alias(libs.plugins.androidMultiplatformLibrary) // désactivé : sharedUI ne cible plus Android (UI Android = Jetpack Compose natif dans androidApp). Réactiver pour re-partager l'UI Android.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    /* Android désactivé pour sharedUI (cible désormais Desktop + Web uniquement) — UI Android = Jetpack Compose natif dans androidApp. Décommenter pour re-partager l'UI Android.
    android {
       namespace = "com.ggdevhub.newsapp.sharedUI"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    */

    sourceSets {
        /* Android désactivé pour sharedUI — décommenter pour re-partager l'UI Android
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        */
        commonMain.dependencies {
            api(project(":sharedLogic"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Injection de dépendances (UI partagée Desktop + Web)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // Images d'articles
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

/* Android désactivé pour sharedUI — décommenter pour re-partager l'UI Android
dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
*/