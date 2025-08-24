
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kingdom13.mylibrary"
    compileSdk = 36

    defaultConfig {

        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        //noinspection ExpiredTargetSdkVersion

        signingConfig = signingConfigs.getByName("debug")



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

    buildToolsVersion = "36.0.0"
    compileSdk = 36


}
dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}