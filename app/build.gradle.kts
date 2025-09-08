plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.kingdom13.mimodulo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingdom13.mimodulo"
        minSdk = 21
        targetSdk = 36


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

    dependenciesInfo {
        includeInBundle = true
    }


}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
