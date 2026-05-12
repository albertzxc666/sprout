import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val yandexDictKey: String = (localProps.getProperty("yandex.dict.key")
    ?: System.getenv("YANDEX_DICT_KEY")
    ?: "").trim()

val sproutApiBaseUrl: String = (localProps.getProperty("sprout.api.base.url")
    ?: System.getenv("SPROUT_API_BASE_URL")
    ?: "http://85.198.69.145").trim()

val generatedConfigDir = layout.buildDirectory.dir("generated/source/sproutConfig/commonMain")
val generateSproutConfig = tasks.register("generateSproutConfig") {
    val outDir = generatedConfigDir.get().asFile
    outputs.dir(outDir)
    inputs.property("yandexKey", yandexDictKey)
    inputs.property("apiBaseUrl", sproutApiBaseUrl)
    doLast {
        if (yandexDictKey.isEmpty()) {
            logger.warn("[Sprout] Yandex Dict key is EMPTY — online translations will not work in this build.")
        } else {
            logger.lifecycle("[Sprout] Yandex Dict key configured (length: ${yandexDictKey.length}).")
        }
        logger.lifecycle("[Sprout] API_BASE_URL = $sproutApiBaseUrl")
        val pkg = File(outDir, "com/transcard/config")
        pkg.mkdirs()
        File(pkg, "Config.kt").writeText(
            """
            // Generated file. Do not edit.
            package com.transcard.config

            internal object Config {
                /** Yandex Dictionary API key, loaded from local.properties at build time. */
                const val YANDEX_DICT_KEY: String = "$yandexDictKey"

                /** sprout-server API base URL (no trailing slash). Override via local.properties:sprout.api.base.url or env SPROUT_API_BASE_URL. */
                const val API_BASE_URL: String = "$sproutApiBaseUrl"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(libs.koin.core)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedConfigDir)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.primitive.adapters)

            implementation(libs.kotlinx.serialization.json)

            api(libs.koin.core)
            implementation(libs.koin.compose)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)

            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.coroutines)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqldelight.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.jvm)
                implementation(libs.ktor.client.okhttp)
            }
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native)
            implementation(libs.ktor.client.darwin)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateSproutConfig)
}

android {
    namespace = "com.transcard.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("TransCardDatabase") {
            packageName.set("com.transcard.db")
        }
    }
}

compose.resources {
    packageOfResClass = "com.transcard.resources"
    publicResClass = true
}
