import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ffmpegKitLocalAar = file("libs/ffmpeg-kit-next-7.1.0-lame-armeabi-v7a-arm64-v8a.aar")
check(ffmpegKitLocalAar.isFile) {
    "Missing self-built FFmpegKitNext AAR at ${ffmpegKitLocalAar.path}. " +
        "Build the recorded arthenica/ffmpeg-kit-next package before Gradle sync."
}
val smartExceptionCommonLocalJar = file("libs/smart-exception-common-0.2.1.jar")
val smartExceptionJavaLocalJar = file("libs/smart-exception-java-0.2.1.jar")

android {
    namespace = "org.zenconverter.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.zenconverter.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"

        ndk {
            abiFilters += "armeabi-v7a"
            abiFilters += "arm64-v8a"
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    fun localProperty(name: String): String? =
        localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

    val releaseStoreFile = localProperty("RELEASE_STORE_FILE")
        ?.let { rootProject.file(it) }
        ?: rootProject.file("ZenConverter.jks")
    val releaseStorePassword = localProperty("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = localProperty("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = localProperty("RELEASE_KEY_PASSWORD")
    val hasReleaseSigning =
        releaseStoreFile.isFile &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
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
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Some vendor linkers fail RELRO setup when FFmpegKit libs are mmap'ed
            // directly from base.apk. Extracting them avoids that device-specific path.
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.media3:media3-transformer:1.10.1")
    implementation(files(ffmpegKitLocalAar))
    if (smartExceptionCommonLocalJar.isFile && smartExceptionJavaLocalJar.isFile) {
        implementation(files(smartExceptionCommonLocalJar, smartExceptionJavaLocalJar))
    } else {
        implementation("com.arthenica:smart-exception-common:0.2.1")
        implementation("com.arthenica:smart-exception-java:0.2.1")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
}
