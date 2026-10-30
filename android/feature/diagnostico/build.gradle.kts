plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.signallq.app.feature.diagnostico"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "AI_WORKER_URL",
            "\"https://linka-ai-diagnosis-worker.gmmattey.workers.dev\"",
        )
        // GH#962/#965 — worker signallq-diagnostic (motor de diagnostico remoto +
        // diretorio de provedores). Deployado em producao em 2026-07-14 (GH#967) —
        // URL abaixo confirmada contra o deploy real via GET /health (200).
        buildConfigField(
            "String",
            "DIAGNOSTIC_WORKER_URL",
            "\"https://signallq-diagnostic.gmmattey.workers.dev\"",
        )
        buildConfigField(
            "String",
            "APP_VERSION",
            "\"${libs.versions.versionName.get()}\"",
        )
        buildConfigField(
            "int",
            "VERSION_CODE",
            libs.versions.versionCode.get(),
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude("**/*.kts")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt.yml")
    baseline = file("$rootDir/config/detekt-baseline.xml")
}

ktlint {
    version = "1.3.1"
    android = true
    ignoreFailures = false
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    }
}

dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // GH#1682 — implementation(project(":featureSpeedtest")) removido: unico consumidor era
    // SignallQOrchestrator.kt (motor SignallQ Pulse), que importava ExecutorSpeedtest/
    // ResultadoSpeedtest/ModoSpeedtest/SpeedtestQualityClassifier. Resolve a violacao
    // "feature nao pode depender de feature" (.claude/rules/higiene-e-padronizacao-
    // repositorio.md §4.9) documentada em docs/modulos/feature-diagnostico.md.
    implementation(project(":coreDatabase"))
    implementation(project(":coreDatastore"))
    implementation(project(":coreNetwork"))
    implementation(project(":coreRecommendation"))
    // Migracao do kill switch do shadow mode (issue #1497) — unico consumidor real do
    // sistema legado SIG-13 agora le a flag via o catalogo tipado deste modulo.
    implementation(project(":core:featureflags"))
    // Dominio de causa-raiz extraido (issue #1157 Fase 1a) — FindingEngine, ScoreEngine,
    // DiagnosticInput/Report/Result, engines por dominio, topology/model+correlation+internet.
    implementation(project(":core:diagnostico"))
    // NDS-02k (#1759) — NdsClient/NdsDiagnosticsRequest/Response e os mappers
    // DiagnosticInput<->NDS (core/nds ja depende de core/diagnostico). Primeiro
    // consumidor real de :core:nds em :featureDiagnostico -- nenhum outro modulo
    // dependia dele ate esta fatia (confirmado no inventario da issue #1759).
    implementation(project(":core:nds"))
    implementation(libs.timber)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    // org.json.JSONObject vem do Android SDK em runtime, mas nao esta disponivel
    // nos unit tests JVM (testDebugUnitTest). Sem este dep, qualquer teste que
    // chame AiDiagnosisRepository.parseResult cai no `catch (Throwable)` e
    // recebe null. Ref: https://stackoverflow.com/q/24197773
    testImplementation("org.json:json:20260719")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
}
