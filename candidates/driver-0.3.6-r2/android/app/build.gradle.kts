plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val cloudOrigin = providers.gradleProperty("YIJ_CLOUD_ORIGIN").orElse("https://yijiexing-demo.hbzjhs.xyz").get()
require(cloudOrigin.isEmpty() || cloudOrigin.matches(Regex("https://[a-z0-9.-]+"))) { "YIJ_CLOUD_ORIGIN must be a plain HTTPS origin" }

android {
    namespace = "cn.hbzjhs.driver.preview"
    compileSdk = 34
    defaultConfig {
        applicationId = "cn.hbzjhs.dc.driver.cloud"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "0.3.12-cloud"
        buildConfigField("String", "CLOUD_API_ORIGIN", "\"$cloudOrigin\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.1")
}
