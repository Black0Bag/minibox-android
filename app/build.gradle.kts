import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// 版本契约实现：与 ci/script/release_version.py 保持同一套规则。
// VERSION 文件内容为 MAJOR.MINOR.PATCH；versionCode = major*1_000_000 + minor*1_000 + patch + 1。
data class RepositoryVersion(
    val name: String,
    val code: Int,
)

fun loadRepositoryVersion(): RepositoryVersion {
    val versionFile = rootProject.file("VERSION")
    require(versionFile.isFile) {
        "Repository VERSION file is required: ${versionFile.path}"
    }
    val versionName = versionFile.readText(Charsets.UTF_8).trim()
    val match = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(versionName)
    require(match != null) {
        "VERSION must contain exactly MAJOR.MINOR.PATCH without prefixes, suffixes, or leading zeroes: $versionName"
    }
    val major = match.groupValues[1].toLong()
    val minor = match.groupValues[2].toLong()
    val patch = match.groupValues[3].toLong()
    require(minor <= 999 && patch <= 999) {
        "VERSION minor and patch components must be <= 999: $versionName"
    }
    val versionCode = major * 1_000_000L + minor * 1_000L + patch + 1L
    require(versionCode <= 2_100_000_000L) {
        "VERSION maps to Android versionCode $versionCode, above 2100000000: $versionName"
    }
    return RepositoryVersion(versionName, versionCode.toInt())
}

val repositoryVersion = loadRepositoryVersion()

android {
    namespace = "com.blackbag.minibox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blackbag.minibox"
        minSdk = 29
        targetSdk = 34
        versionCode = repositoryVersion.code
        versionName = repositoryVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.window)
    implementation(libs.kotlinx.serialization)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(platform(libs.compose.bom))

    coreLibraryDesugaring(libs.desugar.jdk)
}
