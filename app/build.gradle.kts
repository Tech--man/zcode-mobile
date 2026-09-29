import java.util.Properties

// 签名材料本地生成（release.keystore + keystore.properties，均不入库）；
// 缺失时不签名，保证裸 clone 也能构建
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreFile = rootProject.file(keystoreProps.getProperty("storeFile") ?: "release.keystore")
val hasReleaseSigning = keystoreFile.exists() &&
    !keystoreProps.getProperty("storePassword").isNullOrBlank() &&
    !keystoreProps.getProperty("keyAlias").isNullOrBlank() &&
    !keystoreProps.getProperty("keyPassword").isNullOrBlank()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.xray.zcode"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.xray.zcode"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.0.3"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        // 版本标识必须进运行时日志：此前设备上跑的是哪次构建无法从日志判定
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.androidx.webkit)
}
