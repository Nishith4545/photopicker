plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
    id("maven-publish")

}

/*plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.dagger.hilt.android'
    id("maven-publish")
}*/

android {
    namespace = "com.nishith.justtestmodule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nishith.justtestmodule"
        minSdk = 29
        targetSdk = 34
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

/*android {
    namespace = "com.nishith.justtestmodule"
    compileSdk 34
    defaultConfig {
        applicationId "com.nishith.justtestmodule"
        minSdk 23
        targetSdk 34
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }
}


dependencies {
    implementation(project(":mediapicker"))

    *//*implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    androidTestImplementation(libs.androidx.espresso.core)*//*


    implementation fileTree(dir: 'libs', include: ['*.jar'])

    // Appcompat, core and constraint layout
    implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.appcompat:appcompat-resources:1.6.1'
    implementation 'com.google.android.material:material:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'

    // Hilt
    implementation "com.google.dagger:hilt-android:2.44"
    implementation 'androidx.test:monitor:1.7.2'
    implementation 'androidx.test.ext:junit-ktx:1.2.1'
    androidTestImplementation 'org.testng:testng:6.9.6'
    kapt "com.google.dagger:hilt-compiler:2.44"

    // Ktx
    implementation "androidx.fragment:fragment-ktx:1.5.6"


    //implementation("com.github.Nishith4545:mediapicker:1.0.1")
}*/
// Allow references to generated code

dependencies {
    implementation(project(":mediapicker"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    androidTestImplementation(libs.androidx.espresso.core)

    //implementation("com.github.Nishith4545:mediapicker:1.0.1")
}
kapt {
    correctErrorTypes = true
}