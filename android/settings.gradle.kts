pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "linkaAndroidKotlin"

include(
    ":app",
    ":coreNetwork",
    ":corePermissions",
    ":coreDatabase",
    ":coreDatastore",
    ":coreTelephony",
    ":coreRecommendation",
    ":featureHome",
    ":featureWifi",
    ":featureDevices",
    ":featureDns",
    ":featureSpeedtest",
    ":featureDiagnostico",
    ":featureFibra",
    ":featureRouter",
    ":featureHistory",
    ":featureSettings",
    // Modulos novos nascem hierarquicos (":core:foo", nao ":coreFoo"), conforme
    // .claude/rules/higiene-e-padronizacao-repositorio.md §5. Pasta fisica ja bate com o alias
    // por convencao padrao do Gradle — sem override de projectDir, ao contrario dos aliases
    // flat legados abaixo.
    ":core:relatorio",
    ":core:diagnostico",
    // Fundacao de Feature Flags do Consumer (issue #1477, Epico #1347) -- modulo novo,
    // nasce hierarquico (":core:featureflags", nao ":coreFeatureFlags"), conforme
    // .claude/rules/higiene-e-padronizacao-repositorio.md §5. Consumido apenas por :app
    // e modulos core/feature do Consumer.
    ":core:featureflags",
    // Camada de rede e contrato do NDS (Network Diagnostics Service), fatia NDS-01
    // (issue #1744, ADR-017). Modulo dedicado -- nao core:network (que e infra de
    // conectividade on-device: probes, gateway, wifi scan, topologia) nem
    // core-utils generico -- porque o NDS vira a espinha dorsal de diagnostico e
    // IA do app (substitui core:diagnostico, ai-diagnosis-worker e
    // signallq-diagnostic-worker), com contrato proprio versionado e multiplos
    // consumidores futuros (NDS-02+). Decisao registrada na PR da fatia NDS-01.
    ":core:nds",
    // Client UDP determinístico do beacon regional AWS GameLift (Architecture Plan "Modo gamer —
    // medição real de rota...", aprovado por Luiz 2026-09-20). Kotlin puro, sem dependência de
    // :core:diagnostico nem :feature:speedtest -- so DatagramSocket. Consumido por :app.
    ":core:probejogo",
)

project(":coreNetwork").projectDir    = File("core/network")
project(":coreDatabase").projectDir   = File("core/database")
project(":coreDatastore").projectDir  = File("core/datastore")
project(":corePermissions").projectDir = File("core/permissions")
project(":coreTelephony").projectDir  = File("core/telephony")
project(":coreRecommendation").projectDir = File("core/recommendation")
project(":featureHome").projectDir        = File("feature/home")
project(":featureWifi").projectDir        = File("feature/wifi")
project(":featureDevices").projectDir     = File("feature/devices")
project(":featureDns").projectDir         = File("feature/dns")
project(":featureSpeedtest").projectDir   = File("feature/speedtest")
project(":featureDiagnostico").projectDir = File("feature/diagnostico")
project(":featureFibra").projectDir       = File("feature/fibra")
project(":featureRouter").projectDir      = File("feature/router")
project(":featureHistory").projectDir     = File("feature/history")
project(":featureSettings").projectDir    = File("feature/settings")
