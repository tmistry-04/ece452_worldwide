import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Empty (rather than the literal string "null") when unset, so a fresh clone can
// still build and run unit tests; API calls will fail with a clear 401 message.
val spoonacularApiKey = localProperties.getProperty("SPOONACULAR_API_KEY") ?: ""
if (spoonacularApiKey.isBlank()) {
    logger.warn("WARNING: SPOONACULAR_API_KEY is not set in local.properties — Spoonacular calls will fail. Get a key at https://spoonacular.com/food-api")
}

// Optional: LLM-assisted receipt parsing via OpenRouter. Without a key the scanner
// just uses the built-in heuristic parser, so no warning when unset.
val openRouterApiKey = localProperties.getProperty("OPENROUTER_API_KEY") ?: ""
val openRouterModel = localProperties.getProperty("OPENROUTER_MODEL") ?: "google/gemini-2.5-flash"

android {
    namespace = "com.example.pantryparty"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.pantryparty"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SPOONACULAR_API_KEY", "\"$spoonacularApiKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"$openRouterModel\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time (LocalDate for purchase/expiry dates) below API 26.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)   // nav + action icons
    implementation(libs.coil.compose)                               // recipe/ingredient images
    implementation(libs.androidx.camera.core)                       // receipt scanning: capture pipeline
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)                       // PreviewView + LifecycleCameraController
    // On-device OCR. Bundled rather than the play-services- variant so a scan never
    // waits on a model download — at the cost of ~46 MB of APK, nearly all of it
    // native OCR libraries across the four ABIs (the model itself is only 1.4 MB).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)       // viewModel() in Compose (MVVM)
    implementation(libs.androidx.lifecycle.runtime.compose)         // collectAsStateWithLifecycle
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}