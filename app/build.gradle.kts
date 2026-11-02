import com.android.build.api.artifact.SingleArtifact
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    // AGP 9 trae Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma del release: la clave y sus datos quedan fuera del repo. Llegan como propiedades
// de Gradle (~/.gradle/gradle.properties, -P o variables ORG_GRADLE_PROJECT_...); aca solo
// estan los nombres. Sin ellas el release sale sin firmar y signedRelease no publica nada.
val releaseStoreFile: String? = providers.gradleProperty("boardpilotStoreFile").orNull
// El AAB puede ir con otra clave, la de subida a Google Play: propiedades boardpilotBundle*
// (StoreFile, StorePassword, KeyAlias, KeyPassword), tambien fuera del repo. Sin ellas, el AAB
// va firmado con la misma clave que el APK.
val bundleStoreFile: String? = providers.gradleProperty("boardpilotBundleStoreFile").orNull

/**
 * Cambia la firma del AAB: le saca la del release y lo firma con jarsigner (el del JDK que
 * corre Gradle) con la clave de subida. Las contraseñas van por entorno, no en la linea de comandos.
 */
fun resignBundle(aab: File, store: File, storePassword: String, alias: String, keyPassword: String) {
    val unsigned = File(aab.parentFile, "${aab.name}.sin-firma")
    ZipFile(aab).use { zip ->
        ZipOutputStream(unsigned.outputStream()).use { out ->
            for (entry in zip.entries().asSequence()) {
                val name = entry.name.uppercase()
                val signature = name.startsWith("META-INF/") &&
                    (name == "META-INF/MANIFEST.MF" || name.substringAfterLast('.') in setOf("SF", "RSA", "DSA", "EC"))
                if (signature) continue
                out.putNextEntry(ZipEntry(entry.name))
                zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
    val jarsigner = File(System.getProperty("java.home"), "bin/jarsigner").path
    val command = ProcessBuilder(
        jarsigner, "-keystore", store.path, "-storepass:env", "BOARDPILOT_STORE_PASSWORD",
        "-keypass:env", "BOARDPILOT_KEY_PASSWORD", unsigned.path, alias,
    ).redirectErrorStream(true)
    command.environment()["BOARDPILOT_STORE_PASSWORD"] = storePassword
    command.environment()["BOARDPILOT_KEY_PASSWORD"] = keyPassword
    val process = command.start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
        unsigned.delete()
        throw GradleException("jarsigner no pudo firmar el AAB: $output")
    }
    unsigned.copyTo(aab, overwrite = true)
    unsigned.delete()
}

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
        versionCode = 3
        versionName = "0.1.2"
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

// APK y AAB de release firmados, en release/ (fuera de git: se publican en GitHub Releases) y
// con el applicationId y la version en el nombre (ar.com.mt0.boardpilot-0.1.2.apk / .aab):
// ./gradlew signedRelease
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
            if (bundleStoreFile != null) {
                val store = file(bundleStoreFile)
                val storePassword = providers.gradleProperty("boardpilotBundleStorePassword")
                val alias = providers.gradleProperty("boardpilotBundleKeyAlias")
                val keyPassword = providers.gradleProperty("boardpilotBundleKeyPassword").orElse(storePassword)
                doLast {
                    resignBundle(
                        destinationDir.resolve("${published.get()}.aab"), store,
                        storePassword.get(), alias.get(), keyPassword.get(),
                    )
                }
            }
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
