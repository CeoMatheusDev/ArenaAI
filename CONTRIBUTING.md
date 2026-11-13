# Contribuindo

Obrigado pelo interesse em contribuir com o Arena AI. Este documento descreve o fluxo esperado para mudanças no código e na documentação.

## Antes de começar

1. Consulte o [README](README.md), a [arquitetura](docs/ARCHITECTURE.md) e as instruções de [compilação](docs/BUILDING.md).
2. Para mudanças maiores, abra uma issue antes de iniciar o trabalho. Isso ajuda a alinhar escopo e evitar implementações duplicadas.
3. Não inclua credenciais, arquivos de assinatura, dados pessoais, dumps de configurações ou APKs no commit.

## Fluxo de trabalho

1. Crie uma branch a partir de `main`, usando um nome descritivo, por exemplo `fix/webview-navigation`, `feat/addon-validation` ou `docs/setup-guide`.
2. Faça mudanças focadas e mantenha o diff tão pequeno quanto possível.
3. Atualize a documentação quando alterar comandos, APIs, configurações, comportamento ou requisitos.
4. Execute os testes e a compilação descritos em [docs/BUILDING.md](docs/BUILDING.md).
5. Abra um pull request com contexto, motivação, resumo técnico, testes executados e eventuais limitações.

## Padrões de código

- Kotlin: siga o [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) e mantenha o estilo já utilizado no projeto.
- Gradle Kotlin DSL: preserve o catálogo de versões em `gradle/libs.versions.toml`; evite declarar versões dispersas.
- Android: respeite o ciclo de vida dos componentes, o comportamento por nível de API e o princípio de menor privilégio.
- WebView: trate conteúdo remoto e JavaScript de complementos como entradas não confiáveis; alterações nas pontes nativas exigem revisão de segurança.
- Documentação: use exemplos executáveis, informe limitações conhecidas e não documente comportamentos ainda não implementados.

## Commits

Prefira mensagens curtas e no imperativo. Prefixos como `feat:`, `fix:`, `docs:`, `test:`, `refactor:` e `chore:` ajudam a identificar a intenção da alteração.

## Pull requests

Um pull request deve:

- explicar o problema e a solução;
- manter o escopo coerente com o título;
- informar os testes executados e os que não puderam ser executados;
- incluir capturas de tela quando alterar a interface;
- atualizar os documentos pertinentes;
- não introduzir chaves, tokens, keystores ou dados pessoais.

A aprovação da integração contínua é necessária quando os checks estiverem disponíveis. Mudanças que afetem autenticação, navegação, WebView, permissões ou acesso a arquivos devem explicar também as implicações de segurança.
