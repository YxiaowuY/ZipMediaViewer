plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.example.zipmedia"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.zipmedia"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.2.5"
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Room 历史记录数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // 压缩包解析：ZIP(内置，用于图片) / RAR(junrar)
    implementation(libs.junrar)
    // junrar 依赖 SLF4J，在 Android 上提供 no-op 绑定，避免控制台警告（对功能无影响）
    implementation("org.slf4j:slf4j-nop:1.7.36")

    // 视频播放
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // 图片缩放
    implementation(libs.subsampling.scale.image.view)

    implementation(libs.kotlinx.coroutines.android)
}