plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.moyavpn.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moyavpn.app"
        minSdk = 24
        targetSdk = 34
        // versionCode kommt in CI aus der GitHub-Run-Nummer → jeder Build ist ein Update.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        // Basis-URL des Config-Servers. Kann pro Build ueberschrieben werden.
        buildConfigField("String", "API_BASE_URL", "\"https://register.moyabot.ru:8443\"")
    }

    // Fester Debug-Signatur-Key (im Repo) — gleiche Signatur über alle Builds,
    // damit Android Updates ohne Neuinstallation erlaubt.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Verteilungs-Varianten:
    //  direct = Website/APK/Telegram → voller Funktionsumfang inkl. Kauf-Link
    //  play   = Google Play Store    → ohne Kauf-Steering (Play-Billing-konform)
    flavorDimensions += "dist"
    productFlavors {
        create("direct") {
            dimension = "dist"
            buildConfigField("boolean", "SHOW_PURCHASE", "true")
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "SHOW_PURCHASE", "false")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Persistenz fuer den App-Token
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Netzwerk
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── VPN-Tunnel: AmneziaWG (DPI-resistent) ─────────────────────────────────
    // Das tunnel-AAR wird in CI aus amnezia-vpn/amneziawg-android (v2.0.1)
    // gebaut (nativer Go/NDK-Build) und nach app/libs/awg-tunnel.aar kopiert.
    // Siehe .github/workflows/android.yml.
    implementation(files("libs/awg-tunnel.aar"))
    // Transitive Abhaengigkeiten des tunnel-Moduls (bei AAR nicht automatisch):
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.collection:collection:1.4.0")
}
