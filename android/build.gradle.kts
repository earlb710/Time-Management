import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.timemanagement.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.timemanagement.android"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

tasks.register("buildAndroidApp") {
    group = "application"
    description = "Builds the Android debug app."
    dependsOn("assembleDebug")
}

tasks.register("runAndroidApp") {
    group = "application"
    description = "Installs and launches the Android debug app on a connected device or emulator."
    dependsOn("installDebug")
    doLast {
        val adbExecutable = findAdbExecutable()
            ?: throw GradleException(
                "Could not find adb. Set ANDROID_SDK_ROOT or ANDROID_HOME, or define sdk.dir in local.properties."
            )

        exec {
            commandLine(
                adbExecutable.absolutePath,
                "shell",
                "am",
                "start",
                "-n",
                "com.timemanagement.android/.MainActivity"
            )
        }
    }
}

fun findAdbExecutable(): File? {
    val sdkDir = findAndroidSdkDirectory() ?: return null
    val candidates = listOf(
        sdkDir.resolve("platform-tools/adb"),
        sdkDir.resolve("platform-tools/adb.exe")
    )
    return candidates.firstOrNull(File::exists)
}

fun findAndroidSdkDirectory(): File? {
    val envSdk = System.getenv("ANDROID_SDK_ROOT")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
    if (envSdk?.exists() == true) {
        return envSdk
    }

    val envHome = System.getenv("ANDROID_HOME")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
    if (envHome?.exists() == true) {
        return envHome
    }

    val localProperties = rootProject.file("local.properties")
    if (!localProperties.exists()) {
        return null
    }

    return localProperties.inputStream().use { stream ->
        Properties().apply { load(stream) }
    }.getProperty("sdk.dir")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::exists)
}
