import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Values come only from the environment or the user's external Gradle properties.
val releaseCredentialNames = listOf(
    "ARACHNODE_KEYSTORE_PATH", "ARACHNODE_KEYSTORE_PASSWORD",
    "ARACHNODE_KEY_ALIAS", "ARACHNODE_KEY_PASSWORD",
)
val releaseCredentials = releaseCredentialNames.associateWith { name ->
    providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull
}

abstract class ValidateReleaseSigning : DefaultTask() {
    @get:Input abstract val configured: MapProperty<String, Boolean>
    @get:Input @get:Optional abstract val keystorePath: Property<String>
    @get:Input abstract val repositoryRoot: Property<String>

    @TaskAction fun validate() {
        val missing = configured.get().filterValues { !it }.keys
        if (missing.isNotEmpty()) {
            throw GradleException("Release signing: missing ${missing.joinToString()}. See RELEASING.md; Debug needs no release credentials.")
        }
        val key = File(keystorePath.get())
        val root = File(repositoryRoot.get()).canonicalFile.toPath()
        if (!key.isAbsolute || key.canonicalFile.toPath().startsWith(root)) {
            throw GradleException("Release signing: ARACHNODE_KEYSTORE_PATH must be absolute and outside the repository.")
        }
        if (!key.isFile || !key.canRead()) {
            throw GradleException("Release signing: keystore must be an existing readable file. See RELEASING.md.")
        }
    }
}

val validateReleaseSigning = tasks.register<ValidateReleaseSigning>("validateReleaseSigning") {
    configured.set(releaseCredentials.mapValues { !it.value.isNullOrBlank() })
    releaseCredentials["ARACHNODE_KEYSTORE_PATH"]?.let { keystorePath.set(it) }
    repositoryRoot.set(rootDir.absolutePath)
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseSigning)
}

android {
    namespace = "com.r0ybt.arachn0de"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.r0ybt.arachn0de"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseCredentials["ARACHNODE_KEYSTORE_PATH"]?.let { storeFile = file(it) }
            storePassword = releaseCredentials["ARACHNODE_KEYSTORE_PASSWORD"]
            keyAlias = releaseCredentials["ARACHNODE_KEY_ALIAS"]
            keyPassword = releaseCredentials["ARACHNODE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("test").resources.directories.add("$projectDir/schemas")

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
