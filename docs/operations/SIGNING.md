---
title: "App Signing — keystore e credenciais"
description: "Assinatura de release do app Android: setup local, GitHub Secrets e validação."
type: "runbook"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# App Signing — Keystore e Credenciais

- **Fonte de verdade:** `android/app/build.gradle.kts` e `.github/workflows/release.yml`
- **Escopo:** signing local e CI do app Android

## Visão geral

O SignallQ Android usa assinatura de release para distribuir builds assinados em produção. Todas as credenciais (senhas, alias, keystore) ficam **fora do git** — nunca são comitadas nem expostas no repositório.

## Estrutura local

O keystore fica organizado assim:

```
<raiz-do-repo>/android/
├── segredos/
│   └── signallq.jks              # ← Keystore local, NÃO vai ao git
├── key.properties             # ← Credenciais locais, NÃO vai ao git
└── key.properties.template    # ← Template sem credenciais, RASTREADO no git
```

## Setup local — Primeira vez

### 1. Copiar template

```powershell
cd android
Copy-Item key.properties.template key.properties
```

### 2. Preencher credenciais

Edite `key.properties` e preencha os 4 campos:

```properties
storePassword=SUA_SENHA_DO_KEYSTORE
keyPassword=SUA_SENHA_DA_CHAVE
keyAlias=signallq
storeFile=segredos/signallq.jks
```

**Nota:** `keyAlias` é sempre `signallq` (fixo). Os dois campos de senha vêm de quem controla o keystore.

### 3. Colocar keystore

O arquivo `segredos/signallq.jks` já deve estar disponível localmente (transferido de forma segura, não via git).

```
android\segredos\signallq.jks
```

Se ainda não existe, veja seção "Gerar novo keystore" abaixo.

## Como funciona (build.gradle.kts)

O script de build (`app/build.gradle.kts`) carrega `key.properties` assim:

```kotlin
private val keyPropertiesFile = rootProject.file("key.properties")
private val keyProperties = Properties().apply {
    if (keyPropertiesFile.exists()) load(keyPropertiesFile.inputStream())
}
```

E usa as credenciais para configurar o signing do release:

```kotlin
signingConfigs {
    create("release") {
        if (keyPropertiesFile.exists()) {
            keyAlias = keyProperties["keyAlias"] as String
            keyPassword = keyProperties["keyPassword"] as String
            storeFile = keyProperties["storeFile"]?.let { rootProject.file(it as String) }
            storePassword = keyProperties["storePassword"] as String
        }
    }
}

buildTypes {
    release {
        if (keyPropertiesFile.exists()) {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Se `key.properties` não existir, o build de release falha (como esperado).

## Build release assinado

Com `key.properties` e `segredos/signallq.jks` presentes e preenchidos:

```powershell
.\scripts\build-apk-release.ps1
```

ou:

```powershell
.\gradlew.bat archiveReleaseApk
```

O APK assinado sai em:

```
builds\apk\release\<versionName>\signallq-android-v<versionName>+<versionCode>-release-<timestamp>.apk
```

## CI/CD — GitHub Secrets (já configurados, não é mais "futuro")

O release automatizado via GitHub Actions (`.github/workflows/release.yml` e
`firebase-distribution.yml`) já usa 4 GitHub Secrets de assinatura (demais secrets em `RELEASE.md`):

| Secret                | Valor                                |
|-----------------------|--------------------------------------|
| `KEYSTORE_BASE64`     | Arquivo `signallq.jks` em base64        |
| `KEY_ALIAS`           | `signallq`                              |
| `KEY_PASSWORD`        | Senha da chave privada               |
| `STORE_PASSWORD`      | Senha do keystore                    |

O workflow CI:

1. Decodifica `KEYSTORE_BASE64` de volta para `signallq.jks`
2. Cria `key.properties` com os secrets
3. Roda `assembleRelease`/`bundleRelease` (via `gradlew`)
4. Assina e publica (Firebase App Distribution ou Play Console conforme o workflow)

### Para gerar KEYSTORE_BASE64

```powershell
$bytes = [System.IO.File]::ReadAllBytes("android\segredos\signallq.jks")
$base64 = [System.Convert]::ToBase64String($bytes)
Write-Output $base64 | Set-Clipboard
```

Cole o valor em GitHub Secrets → Repository secrets → `KEYSTORE_BASE64`.

## Gerar novo keystore (se necessário)

Se não tiver um keystore existente, crie um com:

```powershell
$keystorePath = "android\segredos\signallq.jks"
$storePassword = "SENHA_FORTE_AQUI"
$keyPassword = "SENHA_DA_CHAVE_AQUI"

keytool -genkey `
    -alias signallq `
    -keyalg RSA `
    -keysize 2048 `
    -keystore $keystorePath `
    -validity 10000 `
    -storepass $storePassword `
    -keypass $keyPassword `
    -dname "CN=SignallQ, O=SignallQ, C=BR"
```

Depois preencha `key.properties` com essas senhas e o alias `signallq`.

## Segurança — Checklist

- [ ] `key.properties` **nunca** é commitado (verificar `.gitignore`)
- [ ] `*.jks` **nunca** é commitado (verificar `.gitignore`)
- [ ] `segredos/` **nunca** é commitado (verificar `.gitignore`)
- [ ] `key.properties.template` **é** rastreado (serve como referência)
- [ ] Senhas do keystore não são compartilhadas em plain text no git/email
- [ ] Credenciais CI (GitHub Secrets) são criadas apenas quando necessário automatizar
- [ ] Keystore local é protegido em sistema de arquivos (permissões)

## Validação

Depois de um build release bem-sucedido, valide a assinatura:

```powershell
$apk = "android\builds\apk\release\<versionName>\signallq-android-v<versionName>+<versionCode>-release-<timestamp>.apk"
jarsigner -verify $apk
```

Saída esperada:

```
jar verified.
```

## Referências

- `app/build.gradle.kts` — configuração de signing
- `docs/operations/RELEASE.md` — fluxo completo de release
- `.gitignore` — arquivos sempre ignorados
