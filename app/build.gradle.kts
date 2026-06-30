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
        versionCode = 1
        versionName = "0.1.0"

        // Basis-URL des Config-Servers. Kann pro Build ueberschrieben werden.
        buildConfigField("String", "API_BASE_URL", "\"https://register.moyabot.ru:8443\"")
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

    // ── VPN-Tunnel ───────────────────────────────────────────────────────────
    // MVP: offizielle WireGuard-Android-Tunnel-Library (Maven Central, baut ohne
    // Go/NDK). Verbindet sich mit Standard-WireGuard-Configs.
    //
    // PRODUKTION / AmneziaWG (DPI-resistent fuer RU): diese Zeile ersetzen durch
    //   implementation("com.github.amnezia-vpn:amneziawg-android:<version>")
    // (via JitPack, siehe settings.gradle.kts) und in TunnelManager.kt die
    // org.amnezia.awg.* Klassen statt com.wireguard.android.* verwenden.
    implementation("com.wireguard.android:tunnel:1.0.20230706")
}
