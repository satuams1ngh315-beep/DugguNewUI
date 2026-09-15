import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.duggustore.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.duggustore.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "https://your-project.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("SUPABASE_ANON_KEY", "your-anon-key")}\"")
        buildConfigField("String", "BACKGROUND_REMOVAL_API_URL", "\"${localProps.getProperty("BACKGROUND_REMOVAL_API_URL", "https://your-api.com")}\"")
        // Public Razorpay key id (rzp_test_... / rzp_live_...). Blank means
        // online payment is not configured — checkout silently stays COD-only.
        buildConfigField("String", "RAZORPAY_KEY_ID", "\"${localProps.getProperty("RAZORPAY_KEY_ID", "")}\"")
    }

    // Only defined when the four RELEASE_* properties are actually present
    // (locally via local.properties, or in CI via secrets written into it) —
    // a release build without them stays unsigned exactly as before, rather
    // than failing the Gradle configuration for anyone who hasn't set up
    // signing yet.
    val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE")
    val hasReleaseSigning = !releaseStoreFile.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
        // Committed rather than left to Gradle's own auto-generated one,
        // which lives outside the repo (~/.android/debug.keystore) and is
        // unique per machine — on a CI runner that's a fresh keystore every
        // build, so each new debug APK has a different signature and won't
        // install over the last one without an uninstall first, wiping all
        // local app data. A shared, checked-in key keeps every debug build
        // signature-compatible everywhere.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Material2 pullrefresh APIs (rememberPullRefreshState/pullRefresh/PullRefreshIndicator) —
    // material3 in this BOM (1.1.x) doesn't yet have PullToRefreshBox, so pull-to-refresh
    // screens borrow the experimental Material2 implementation instead.
    implementation("androidx.compose.material:material")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Coroutines (Dispatchers.IO for the Supabase calls). Pinned to the version the
    // lifecycle artifacts already resolve to, so declaring it explicitly does not bump
    // the graph and change what R8 has to chew through on the release build.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // OkHttp HTTP Client (all Supabase API calls via OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Online payments (UPI / cards / netbanking) at checkout. The key secret
    // never ships in the app — order creation and signature verification run
    // in the create/verify-razorpay-payment Edge Functions instead.
    implementation("com.razorpay:checkout:1.6.33")

    // In-app map for the rider's route (pickup/drop) — OpenStreetMap tiles via
    // osmdroid, no API key or billing account needed, unlike Google Maps SDK.
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Cuts a seller's product photo out from its background on-device — free,
    // no API key, no server round trip. Model downloads on first use via
    // Google Play Services rather than shipping in the APK.
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    // Search by photo. CameraX runs the viewfinder and the still capture so
    // the screen is ours to theme; ML Kit's image labeller names what is in
    // the shot on-device, and that word goes into the ordinary search. Play
    // Services build, downloaded on demand like the segmenter above rather
    // than bundled into the APK.
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")

    // SLF4J nop (runtime dependency)
    implementation("org.slf4j:slf4j-nop:2.0.9")

    // Push notifications — delivered even while the app is closed, unlike
    // the in-app DB-backed notifications above which only surface while
    // the app is open to fetch them.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Crash + non-fatal error reports in the Firebase console.
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Unit tests (app/src/test) — plain JVM tests, no device or emulator needed.
    testImplementation("junit:junit:4.13.2")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
