plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val cloudOrigin = providers.gradleProperty("YIJ_CLOUD_ORIGIN").orElse("https://driver-demo-491829048305.us-west1.run.app").get()
require(cloudOrigin.matches(Regex("https://[a-z0-9.-]+"))) { "Cloud origin must be plain HTTPS" }
android {
    namespace = "cn.hbzjhs.passenger"
    compileSdk = 34
    defaultConfig {
        applicationId = "cn.hbzjhs.dc.passenger.cloud"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.1.5-experience"
        buildConfigField("String", "CLOUD_API_ORIGIN", "\"$cloudOrigin\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
