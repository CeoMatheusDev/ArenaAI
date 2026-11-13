# Arena AI

[![Android CI](https://github.com/CeoMatheusDev/ArenaAI/actions/workflows/android-ci.yml/badge.svg)](https://github.com/CeoMatheusDev/ArenaAI/actions/workflows/android-ci.yml)
[![Licença: MIT](https://img.shields.io/badge/Licen%C3%A7a-MIT-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)

Aplicativo Android nativo que fornece uma experiência integrada para o [Arena AI](https://arena.ai/agent), combinando um WebView com recursos específicos do Android: notificações, execução em segundo plano, conveniência de autenticação e suporte a extensões e plugins locais.

> **Importante:** o conteúdo de IA é carregado do serviço web remoto `arena.ai/agent`. Este repositório implementa o cliente Android e sua integração com o site; ele não contém nem hospeda o modelo de IA.

## Funcionalidades

- **Experiência Android nativa:** interface baseada em Jetpack Compose e conteúdo do Arena AI carregado em WebView.
- **Navegação integrada:** voltar, avançar, recarregar, tela cheia, compartilhamento e abertura de links externos.
- **Integração de conta:** permite selecionar uma conta disponível no dispositivo e utilizar o e-mail em fluxos de autenticação compatíveis.
- **Notificações:** acompanha o estado de geração detectado na página e pode notificar quando uma resposta termina.
- **Segundo plano:** serviço em primeiro plano e wake lock com limite de segurança de 45 minutos.
- **Extensões e plugins:** descoberta de arquivos JavaScript/JSON, verificação básica de compatibilidade e injeção de scripts e estilos.
- **Arquivos locais:** ponte JavaScript para operações na pasta ArenaAI e em diretórios autorizados pelo usuário via Storage Access Framework (SAF).
- **Persistência:** configurações locais, preferências e identificadores de complementos ativos.

## Requisitos

| Componente | Versão/configuração |
| --- | --- |
| Android mínimo | API 24 (Android 7.0) |
| Android alvo | API 36 |
| SDK de compilação | API 36.1 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.3.1 |
| Kotlin Gradle Plugin | 2.2.10 |
| JDK para executar o Gradle | 17 |
| Compatibilidade de bytecode Java | 11 |

As versões refletem a configuração versionada no projeto. Consulte [Como compilar](docs/BUILDING.md) para instalar o SDK correto e executar os testes.

## Compilação rápida

O repositório ainda não versiona os arquivos padrão do Gradle Wrapper. Portanto, é necessário ter o Gradle **9.3.1** instalado no ambiente:

```bash
java -version
gradle --version

# Executa os testes unitários
gradle testDebugUnitTest

# Gera o APK de depuração
gradle assembleDebug
```

O APK de depuração é gerado em `app/build/outputs/apk/debug/app-debug.apk`. O procedimento completo está em [docs/BUILDING.md](docs/BUILDING.md).

## Estrutura do repositório

```text
ArenaAI/
├── .github/                   # CI, modelos de issues/PR e Dependabot
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/  # Código Kotlin do aplicativo
│       │   ├── res/               # Recursos, temas e ícones Android
│       │   └── AndroidManifest.xml
│       ├── test/                  # Testes locais/Robolectric
│       └── androidTest/           # Testes instrumentados
├── docs/
│   ├── ADDONS.md              # Formato e API de extensões/plugins
│   ├── ARCHITECTURE.md        # Componentes e fluxo de execução
│   └── BUILDING.md            # Ambiente, compilação e testes
├── gradle/
│   └── libs.versions.toml     # Catálogo central de versões
├── .env.example               # Modelo sem credenciais reais
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE
└── README.md
```

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md) — responsabilidades dos componentes e fluxo de integração.
- [Compilação e testes](docs/BUILDING.md) — requisitos, comandos e configuração local.
- [Extensões e plugins](docs/ADDONS.md) — diretórios, manifestos, scripts, SDK JavaScript e ponte de arquivos.
- [Segurança](SECURITY.md) — limitações conhecidas e canal recomendado para reportar vulnerabilidades.
- [Contribuição](CONTRIBUTING.md) — fluxo de desenvolvimento e critérios para pull requests.
- [Histórico de alterações](CHANGELOG.md).

## Extensões e plugins

O aplicativo procura complementos na pasta `ArenaAI` do armazenamento do app, em `Documents/ArenaAI` e em diretórios que o usuário tenha autorizado pelo SAF. Os diretórios principais são:

```text
ArenaAI/
├── settings.json
├── documento.md
├── extencao/
└── plugin/
```

Também são reconhecidos os nomes alternativos `extensão` e `plugins` em seus respectivos fluxos de leitura. Para criar um complemento, comece por [docs/ADDONS.md](docs/ADDONS.md).

**Instale somente complementos cujo código você examinou e em cuja origem confia.** Scripts de complementos são executados no contexto do WebView e podem interagir com as pontes JavaScript nativas descritas na documentação de segurança.

## Testes

Os testes existentes cobrem, entre outros pontos, persistência de configurações, criação da estrutura de diretórios, reconhecimento de URLs de autenticação e operações da ponte de arquivos. Execute:

```bash
gradle testDebugUnitTest
```

A integração contínua executa os testes unitários e a montagem do APK de depuração em pull requests e atualizações da branch principal.

## Licença

O código deste projeto é disponibilizado sob a licença [MIT](LICENSE). Bibliotecas, serviços, marcas e materiais de terceiros continuam sujeitos às respectivas licenças e termos. A licença MIT não concede direitos sobre o serviço ou a marca Arena AI de terceiros.

## Aviso

Este aplicativo depende de um serviço web externo. Mudanças no site, em seus fluxos de autenticação ou em sua estrutura DOM podem afetar recursos como detecção de geração, login assistido e compatibilidade de complementos. A compatibilidade deve ser validada novamente quando o serviço remoto mudar.
