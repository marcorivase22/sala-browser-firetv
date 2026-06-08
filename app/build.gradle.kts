import java.util.Properties

plugins {
    id("com.android.application")
}

val keystorePropertiesFile = rootProject.file("release/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.salabrowser.app"
    compileSdk = 35
    flavorDimensions += "device"

    defaultConfig {
        applicationId = "org.salabrowser.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.1"
    }

    productFlavors {
        create("tv") {
            dimension = "device"
        }
        create("mobile") {
            dimension = "device"
            applicationIdSuffix = ".mobile"
            versionNameSuffix = "-mobile"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
