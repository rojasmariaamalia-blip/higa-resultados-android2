plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.higa.resultados"; compileSdk = 35
    defaultConfig { applicationId = "com.higa.resultados"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
