import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from android/keystore.properties, which is gitignored - see
// android/RELEASE_SIGNING.md for how to generate it. Debug builds work without it.
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}

val debugBaseUrl = (findProperty("retailapp.debugBaseUrl") as String?)?.takeIf { it.isNotBlank() } ?: "http://13.215.157.19/"
val releaseBaseUrl = (findProperty("retailapp.releaseBaseUrl") as String?).orEmpty().trim()

// A release APK must never talk plain HTTP (Cycle 5 section 2.4), so refuse to build one
// until an https:// backend URL is configured.
tasks.matching { it.name.startsWith("pre") && it.name.endsWith("ReleaseBuild") }.configureEach {
    doFirst {
        require(releaseBaseUrl.startsWith("https://") && releaseBaseUrl.endsWith("/")) {
            "Set retailapp.releaseBaseUrl to the HTTPS API address (e.g. https://api.example.com/) " +
                "in android/gradle.properties or with -Pretailapp.releaseBaseUrl=... before building a release."
        }
    }
}

android {
    namespace = "com.retailapp.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.retailapp.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    // Cycle 5: the backend URL lives here instead of NetworkModule, so debug builds can keep
    // talking to the plain-HTTP test server (src/debug/res/xml allows it) while release builds
    // are HTTPS-only. Set retailapp.releaseBaseUrl (gradle.properties or -P) to the API domain.
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseBaseUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Networking. The backend uses cookie-based sessions, so OkHttp keeps a persistent
    // cookie jar (see session/PersistentCookieJar.kt) instead of a bearer-token scheme.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Cycle 4 proof photos: Coil loads them through our OkHttp client (so the session cookie is
    // sent), ExifInterface keeps camera photos upright when we re-encode them before upload.
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // Cycle 5 barcode/SKU scanning. The Google code scanner runs inside Play services, so the
    // app itself needs no camera permission.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
