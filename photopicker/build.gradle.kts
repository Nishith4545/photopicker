plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kapt)
    //alias(libs.plugins.hilt)
    id("maven-publish")
}

android {
    namespace = "com.nishith.mediapicker"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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
   // implementation(libs.android.image.cropper)
    implementation(libs.glide)
    //implementation(libs.hilt.android)
    //kapt(libs.hilt.android.compiler)
}

kapt {
    correctErrorTypes = true
}

/*plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.dagger.hilt.android'
    id 'maven-publish'
}

android {
    compileSdkVersion 34
    namespace "com.nishith.mediapicker"

    defaultConfig {
        minSdkVersion 23
        targetSdkVersion 34
        versionCode 1
        versionName "1.0"
        // Define the namespace for the module
        namespace "com.nishith.mediapicker"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }

    packagingOptions {
        resources {
            excludes += ["/META-INF/*.kotlin_module"]
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX dependencies
    implementation "androidx.core:core-ktx:1.13.1"
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation "com.google.android.material:material:1.9.0"

    // Hilt dependencies
    implementation "com.google.dagger:hilt-android:2.44"
    kapt "com.google.dagger:hilt-android-compiler:2.44"

    // Glide for image loading
    implementation "com.github.bumptech.glide:glide:4.14.2"
    kapt "com.github.bumptech.glide:compiler:4.14.2"

    // Optional: JUnit for unit tests
    testImplementation "junit:junit:4.13.2"

    // Optional: AndroidX JUnit for instrumented tests
    androidTestImplementation "androidx.test.ext:junit:1.2.1"

}*/
