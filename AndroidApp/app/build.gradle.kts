plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ozzybozy.qrhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ozzybozy.qrhub"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("androidx.camera:camera-core:1.4.2")
    implementation ("androidx.camera:camera-camera2:1.4.2")
    implementation ("androidx.camera:camera-lifecycle:1.4.2")
    implementation ("androidx.camera:camera-view:1.4.2")
    implementation ("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.3")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation ("com.google.code.gson:gson:2.10.1")


}