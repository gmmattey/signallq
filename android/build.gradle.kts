plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.kapt") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.appdistribution") version "5.3.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// kotlin.plugin.compose 2.4.0 emite @Metadata versao 2.4.0, mas o leitor kotlin-metadata-jvm
// bundlado nas versoes atuais do Room compiler (2.8.4, usado direto por :coreDatabase e via
// copia shadada dentro do Dagger/Hilt compiler nos modulos com Hilt) so suporta ler ate 2.3.0 --
// quebra kaptDebugKotlin com IllegalArgumentException. Forcar a versao mais nova nas configs
// kapt* resolve sem tocar em Room nem migrar KAPT->KSP. Remover quando Room publicar uma versao
// com kotlin-metadata-jvm >= 2.4.0 (nao existia em 2026-07-15).
subprojects {
    configurations.matching {
        it.name.startsWith("kapt") || it.name.contains("annotationProcessor", ignoreCase = true)
    }.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.20")
    }
}

val signallQVersionName = libs.versions.versionName.get()
val signallQVersionCode = libs.versions.versionCode.get()

fun registerApkArchiveTask(
    taskName: String,
    assembleTaskPath: String,
    buildType: String,
    rawApkName: String,
) {
    tasks.register<Copy>(taskName) {
        group = "distribution"
        description = "Gera e arquiva o APK $buildType com versao e timestamp em builds/apk/$buildType/$signallQVersionName."

        dependsOn(assembleTaskPath)

        val timestamp = providers.provider {
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        }
        val apkFileName = timestamp.map {
            "signallq-android-v$signallQVersionName+$signallQVersionCode-$buildType-$it.apk"
        }

        from(layout.projectDirectory.file("app/build/outputs/apk/$buildType/$rawApkName"))
        into(layout.projectDirectory.dir("builds/apk/$buildType/$signallQVersionName"))
        rename { apkFileName.get() }

        doFirst {
            val rawApk = layout.projectDirectory.file("app/build/outputs/apk/$buildType/$rawApkName").asFile
            if (!rawApk.exists()) {
                throw GradleException("APK bruto nao encontrado: ${rawApk.absolutePath}")
            }
        }
    }
}

registerApkArchiveTask(
    taskName = "archiveDebugApk",
    assembleTaskPath = ":app:assembleDebug",
    buildType = "debug",
    rawApkName = "app-debug.apk",
)

registerApkArchiveTask(
    taskName = "archiveReleaseApk",
    assembleTaskPath = ":app:assembleRelease",
    buildType = "release",
    rawApkName = "app-release.apk",
)
