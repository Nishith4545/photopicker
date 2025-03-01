plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kapt)
    //alias(libs.plugins.hilt)
    id("maven-publish")
}

android {
    namespace = "com.nishith.photopicker"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        testOptions.targetSdk = 34
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
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding= true
    }
}

publishing {
    publications {
       register<MavenPublication>("release"){
           afterEvaluate{
               from(components["release"])
           }
       }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.glide)
    implementation( "androidx.activity:activity-ktx:1.7.0")
}

kapt {
    correctErrorTypes = true
}

