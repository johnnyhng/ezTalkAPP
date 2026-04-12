plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.services)
    jacoco
}

import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

android {
    namespace = "tw.com.johnnyhng.eztalk.asr"
    compileSdk = 34

    defaultConfig {
        applicationId = "tw.com.johnnyhng.eztalk.asr"
        minSdk = 26
        targetSdk = 34
        versionCode = 20250918
        versionName = "1.12.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    androidResources {
        generateLocaleConfig = false
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_INSECURE_TLS_BY_DEFAULT", "true")
        }
        release {
            buildConfigField("boolean", "ALLOW_INSECURE_TLS_BY_DEFAULT", "false")
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Force extraction of native libs on device. This is a runtime workaround
            // for native components that do not load correctly directly from the APK.
            useLegacyPackaging = true
        }
    }
}

jacoco {
    toolVersion = "0.8.12"
}

val jacocoExecutionData = fileTree(layout.buildDirectory.get().asFile) {
    include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
    )
}

val jacocoSourceDirectories = files(
    "$projectDir/src/main/java",
    "$projectDir/src/main/kotlin"
)

val jacocoBaseExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*${'$'}Companion.class",
    "**/*${'$'}Lambda${'$'}*.*",
    "**/*${'$'}inlined${'$'}*.*"
)

val jacocoFullModuleExcludes = jacocoBaseExcludes

val jacocoCoreLogicIncludes = listOf(
    "tw/com/johnnyhng/eztalk/asr/utils/ApiKt.class",
    "tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtilsKt.class",
    "tw/com/johnnyhng/eztalk/asr/utils/UtilsKt.class",
    "tw/com/johnnyhng/eztalk/asr/utils/WavUtilKt.class",
    "tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.class",
    "tw/com/johnnyhng/eztalk/asr/data/classes/Model.class",
    "tw/com/johnnyhng/eztalk/asr/workflow/TranscriptWorkflowKt.class",
    "tw/com/johnnyhng/eztalk/asr/datacollect/DataCollectQueueKt.class",
    "tw/com/johnnyhng/eztalk/asr/managers/ModelManager.class",
    "tw/com/johnnyhng/eztalk/asr/managers/SettingsManagerKt.class",
    "tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.class",
    "tw/com/johnnyhng/eztalk/asr/managers/SettingsManager${'$'}*.class"
)

val jacocoStableCoreIncludes = listOf(
    "tw/com/johnnyhng/eztalk/asr/workflow/TranscriptWorkflowKt.class",
    "tw/com/johnnyhng/eztalk/asr/datacollect/DataCollectQueueKt.class"
)

fun jacocoDebugClassTrees(
    includes: List<String>? = null,
    excludes: List<String> = emptyList()
) = files(
    fileTree("${layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
        if (includes != null) {
            include(includes)
        }
        exclude(excludes)
    },
    fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/classes") {
        if (includes != null) {
            include(includes)
        }
        exclude(excludes)
    }
)

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.7")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.androidx.test.core)
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(jacocoDebugClassTrees(excludes = jacocoFullModuleExcludes))
    sourceDirectories.setFrom(jacocoSourceDirectories)
    executionData.setFrom(jacocoExecutionData)
}

tasks.register<JacocoReport>("jacocoCoreLogicReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates a Jacoco report for the core refactoring-risk logic."

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        jacocoDebugClassTrees(
            includes = jacocoCoreLogicIncludes,
            excludes = jacocoBaseExcludes
        )
    )
    sourceDirectories.setFrom(jacocoSourceDirectories)
    executionData.setFrom(jacocoExecutionData)
}

tasks.register<JacocoCoverageVerification>("jacocoStableCoreVerification") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Verifies line coverage for the stabilized reducer core scope."

    classDirectories.setFrom(
        jacocoDebugClassTrees(
            includes = jacocoStableCoreIncludes,
            excludes = jacocoBaseExcludes
        )
    )
    sourceDirectories.setFrom(jacocoSourceDirectories)
    executionData.setFrom(jacocoExecutionData)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.register("verificationReport") {
    group = "verification"
    description = "Runs unit tests, core logic coverage, and connected Android behavior tests."

    dependsOn(
        "testDebugUnitTest",
        "jacocoCoreLogicReport",
        "connectedDebugAndroidTest"
    )
}
