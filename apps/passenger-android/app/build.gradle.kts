plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val cloudOrigin = providers.gradleProperty("YIJ_CLOUD_ORIGIN").orElse("https://yijiexing-demo.hbzjhs.xyz").get()
require(cloudOrigin.matches(Regex("https://[a-z0-9.-]+"))) { "Cloud origin must be plain HTTPS" }
android {
    namespace = "cn.hbzjhs.passenger"
    compileSdk = 34
    defaultConfig {
        applicationId = "cn.hbzjhs.dc.passenger.cloud"
        minSdk = 26
        targetSdk = 34
        versionCode = 26
        versionName = "1.1.24-experience"
        buildConfigField("String", "CLOUD_API_ORIGIN", "\"$cloudOrigin\"")
    }
    // Compress native map libraries in sideload APKs; AGP sets extraction for installation.
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
        // Public sideload experience: non-debuggable, no debug tooling. Keep the existing
        // experience certificate so installed test apps and AMap bindings remain compatible.
        create("experience") {
            initWith(getByName("release"))
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("com.amap.api:3dmap-location-search:11.2.100_loc11.2.100_sea9.8.1")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
