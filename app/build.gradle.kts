import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.goodbrainrot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.goodbrainrot.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 118
        versionName = "3.98"

        // Date de build injectee dans l'app (visible dans les Options).
        val buildTime = SimpleDateFormat("dd/MM/yyyy HH:mm").apply {
            timeZone = TimeZone.getTimeZone("Europe/Paris")
        }.format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    // Signature release : keystore locale HORS dépôt, dans le dossier frère ../GoodBrainRot-keys/.
    // Si le fichier keystore.properties est absent, le build release reste possible mais non signé.
    val ksProps = Properties().apply {
        val f = rootProject.file("../GoodBrainRot-keys/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    if (ksProps.getProperty("storeFile") != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ksProps.getProperty("storeFile"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 sur le code uniquement. PAS de shrinkResources : beaucoup de drawables sont
            // chargés par nom via getIdentifier() et seraient supprimés à tort.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // tests JVM : banc d'équilibrage du jeu de voiture (robots qui jouent sur le vrai modèle)
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
