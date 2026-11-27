plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.edgarguitarist.clima"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edgarguitarist.clima"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }

    // Misma llave de release que Lince si está configurada (CLIMA_* o, si no, LINCE_*); si no, la debug.
    fun clave(nombre: String): String? =
        (project.findProperty("CLIMA_$nombre") ?: project.findProperty("LINCE_$nombre")) as String?
            ?: System.getenv("CLIMA_$nombre") ?: System.getenv("LINCE_$nombre")
    val almacen = clave("STORE_FILE")?.takeIf { file(it).exists() }
    signingConfigs {
        if (almacen != null) {
            create("release") {
                storeFile = file(almacen)
                storePassword = clave("STORE_PASSWORD")
                keyAlias = clave("KEY_ALIAS")
                keyPassword = clave("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // en los tests de JVM el org.json de Android es un stub
}
