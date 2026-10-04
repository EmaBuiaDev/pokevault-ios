import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "com.emabuia.pokevault.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.io.core)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

compose.resources {
    // Un pacchetto fisso: altrimenti Res cambia nome insieme al progetto.
    packageOfResClass = "com.emabuia.pokevault.resources"
}

/**
 * La configurazione Firebase dell'app iOS (GoogleService-Info.plist) diventa
 * un oggetto Kotlin generato in build/, mai nel repo: il repo e' pubblico e
 * GitHub segnala le chiavi Google pubblicate.
 *
 * Il plist arriva da FIREBASE_IOS_PLIST_PATH (la CI, dal segreto
 * FIREBASE_IOS_PLIST) o da `firebaseIosPlist` in local.properties (in locale,
 * dalla cartella pokevault-keys). Senza, i valori restano vuoti e l'app mostra
 * il login come non configurato invece di non compilare.
 */
abstract class GenerateFirebaseConfig : DefaultTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val plist: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val text = plist.orNull?.asFile?.takeIf { it.exists() }?.readText().orEmpty()
        fun value(key: String) =
            Regex("<key>$key</key>\\s*<string>([^<]*)</string>").find(text)?.groupValues?.get(1).orEmpty()
        val file = outputDir.file("com/emabuia/pokevault/firebase/FirebaseConfig.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.emabuia.pokevault.firebase
            |
            |// Generato da generateFirebaseConfig: non modificare e non copiare nel repo.
            |internal object FirebaseConfig {
            |    const val API_KEY = "${value("API_KEY")}"
            |    const val PROJECT_ID = "${value("PROJECT_ID")}"
            |    const val IOS_CLIENT_ID = "${value("CLIENT_ID")}"
            |    const val REVERSED_CLIENT_ID = "${value("REVERSED_CLIENT_ID")}"
            |    val isConfigured: Boolean get() = API_KEY.isNotEmpty() && PROJECT_ID.isNotEmpty()
            |}
            |""".trimMargin()
        )
    }
}

val firebasePlistPath: String? = providers.environmentVariable("FIREBASE_IOS_PLIST_PATH").orNull
    ?: Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }.getProperty("firebaseIosPlist")

val generateFirebaseConfig = tasks.register<GenerateFirebaseConfig>("generateFirebaseConfig") {
    firebasePlistPath?.let { path -> File(path).takeIf { it.exists() }?.let { plist.set(it) } }
    outputDir.set(layout.buildDirectory.dir("generated/firebase"))
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateFirebaseConfig.map { it.outputDir })
}
