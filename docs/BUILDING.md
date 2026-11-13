# Compilação e testes

Este guia descreve o ambiente compatível com a configuração atualmente versionada no repositório.

## Requisitos

| Ferramenta | Versão |
| --- | --- |
| JDK usado pelo Gradle | 17 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.3.1 |
| Kotlin Gradle Plugin | 2.2.10 |
| Android SDK Platform | 36.1 |
| Android SDK Build-Tools | 36.0.0 |
| Android mínimo declarado | API 24 |
| Android alvo declarado | API 36 |

O AGP 9.1.1 requer Gradle 9.3.1 e JDK 17. O projeto compila bytecode Java com compatibilidade 11, mas o processo de build deve ser executado com JDK 17.

Referências oficiais:
- [Notas de versão e compatibilidade do AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Configuração do SDK Android 16 QPR2 / API 36.1](https://developer.android.com/about/versions/16/qpr2/setup-sdk)
- [Gradle no GitHub Actions](https://docs.gradle.org/current/userguide/github-actions.html)

## Preparar o ambiente

1. Instale o Android Studio compatível com AGP 9.1.1.
2. Instale um JDK 17 e configure o Android Studio/terminal para utilizá-lo.
3. No SDK Manager, instale o **Android SDK Platform 36.1** e o **Android SDK Build-Tools 36.0.0**.
4. Configure `ANDROID_HOME` ou `ANDROID_SDK_ROOT` caso o Android Studio não configure essas variáveis automaticamente.
5. Instale Gradle 9.3.1 no PATH.

Confirme as versões:

```bash
java -version
gradle --version
```

## Estado do Gradle Wrapper

O repositório contém `gradle/wrapper/gradle-wrapper.properties`, mas ainda não contém os scripts `gradlew`, `gradlew.bat` nem `gradle-wrapper.jar`. Por isso, os comandos abaixo utilizam uma instalação local de Gradle 9.3.1.

Para gerar e versionar o Wrapper posteriormente, com Gradle 9.3.1 instalado:

```bash
gradle wrapper --gradle-version 9.3.1
```

Revise e inclua os arquivos gerados em um commit antes de trocar as instruções para `./gradlew`.

## Abrir no Android Studio

1. Clone o repositório.
2. Abra a pasta raiz que contém `settings.gradle.kts`.
3. Aguarde a sincronização do Gradle.
4. Selecione a configuração `app`.
5. Execute em um dispositivo ou emulador com Android API 24 ou superior.

## Build pela linha de comando

Na raiz do repositório:

```bash
# Lista as tarefas disponíveis
gradle tasks

# Executa os testes unitários do variant debug
gradle testDebugUnitTest

# Gera o APK debug
gradle assembleDebug

# Executa testes e build em sequência
gradle testDebugUnitTest assembleDebug
```

O APK é produzido em `app/build/outputs/apk/debug/app-debug.apk`.

## Testes

Os testes locais estão em:

- `app/src/test/` — testes unitários e testes com Robolectric;
- `app/src/androidTest/` — testes instrumentados executados em dispositivo/emulador.

Para executar somente os testes unitários:

```bash
gradle testDebugUnitTest
```

Para testes instrumentados, inicie um emulador/dispositivo e execute a tarefa `connectedDebugAndroidTest`.

## Configuração e credenciais

- `.env.example` é um modelo comentado para o plugin de Secrets Gradle. Crie um `.env` local somente quando o ambiente ou uma funcionalidade exigir valores adicionais.
- Nunca coloque uma chave real em `.env.example`, no código-fonte ou em arquivos versionados.
- `google-services.json` não é fornecido pelo repositório. O plugin Google Services está configurado para emitir aviso quando esse arquivo estiver ausente; integrações que dependam de uma configuração Firebase válida precisam das credenciais correspondentes no ambiente de desenvolvimento.
- Arquivos de assinatura Android (`.jks` e `.keystore`) e propriedades locais não devem ser enviados ao Git.

## Problemas comuns

**Gradle informa incompatibilidade de versão:** confirme que o terminal usa Gradle 9.3.1 e JDK 17.

**SDK Platform 36.1 não encontrado:** abra o SDK Manager, expanda Android 16 e instale o pacote Android SDK Platform 36.1.

**Sincronização do Gradle não encontra o SDK:** configure `ANDROID_HOME`/`ANDROID_SDK_ROOT` e instale os pacotes listados na tabela de requisitos.

**Funcionalidade Firebase indisponível:** a compilação e a configuração dos serviços são coisas diferentes. Confirme se o projeto Firebase correspondente foi configurado no ambiente, sem adicionar credenciais ao repositório.
