import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    // AGP 9 trae Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma del release: la clave y sus datos quedan fuera del repo. Llegan como propiedades
// de Gradle (~/.gradle/gradle.properties, -P o variables ORG_GRADLE_PROJECT_...); aca solo
// estan los nombres. Sin ellas el release sale sin firmar y signedRelease no publica nada.
val releaseStoreFile: String? = providers.gradleProperty("boardpilotStoreFile").orNull
// Pedir signedRelease sin firma falla al arrancar, no despues de compilar todo el release.
if (releaseStoreFile == null && gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "signedRelease" }) {
    throw GradleException(
        "Falta la firma del release: definir boardpilotStoreFile, boardpilotStorePassword, " +
            "boardpilotKeyAlias y boardpilotKeyPassword (por ejemplo en ~/.gradle/gradle.properties).",
    )
}

android {
    namespace = "ar.com.mt0.boardpilot"
    compileSdk = 37

    defaultConfig {
        applicationId = "ar.com.mt0.boardpilot"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("boardpilotStorePassword").orNull
                keyAlias = providers.gradleProperty("boardpilotKeyAlias").orNull
                keyPassword = providers.gradleProperty("boardpilotKeyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// APK y AAB de release firmados, en release/ del repo y con el applicationId y la version
// en el nombre (ar.com.mt0.boardpilot-0.1.0.apk / .aab): ./gradlew signedRelease
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val published = variant.applicationId.zip(variant.outputs.single().versionName) { id, version ->
            "$id-$version"
        }
        tasks.register<Copy>("signedRelease") {
            group = "distribution"
            description = "APK y AAB de release firmados, copiados a release/ con applicationId y version."
            from(variant.artifacts.get(SingleArtifact.APK)) {
                include("*.apk")
                rename { "${published.get()}.apk" }
            }
            from(variant.artifacts.get(SingleArtifact.BUNDLE)) {
                rename { "${published.get()}.aab" }
            }
            into(rootProject.layout.projectDirectory.dir("release"))
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
