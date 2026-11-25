plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.codewall"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codewall"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "3.0-stage2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Итоговый файл называется poipaper.apk (а не app-debug.apk).
    applicationVariants.all {
        outputs.all {
            try {
                javaClass.getMethod("setOutputFileName", String::class.java).invoke(this, "poipaper.apk")
            } catch (e: Exception) {
                // если у версии Gradle нет такого метода, имя задаст шаг в GitHub Actions
            }
        }
    }
}
