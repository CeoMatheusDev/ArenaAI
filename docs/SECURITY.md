# Notas de segurança para desenvolvimento

Este documento complementa a política de [SECURITY.md](../SECURITY.md) com considerações técnicas para quem modifica o código.

## WebView e origem do conteúdo

A configuração atual habilita JavaScript, DOM Storage, database, acesso a arquivos/conteúdo, cookies de terceiros e modo de conteúdo misto compatível. O conteúdo inicial é `https://arena.ai/agent`, mas a navegação geral não aplica uma allowlist de hosts antes de manter as interfaces nativas registradas.

As interfaces `AndroidBridge` e `ArenaFS` são adicionadas com `addJavascriptInterface`. Essa superfície deve ser tratada com cuidado: interfaces JavaScript nativas podem ser invocadas por código carregado no WebView e não devem ser expostas a origens não confiáveis.

Antes de ampliar a integração, avalie:

1. restringir a navegação a origens explicitamente confiáveis;
2. abrir links externos fora do WebView quando apropriado;
3. remover permissões de WebView que não sejam necessárias;
4. reduzir a exposição de interfaces nativas e validar rigorosamente todos os argumentos;
5. revisar o uso de cookies de terceiros e conteúdo misto;
6. validar a origem/necessidade de cada script injetado.

## Complementos

O carregador injeta JavaScript/CSS no contexto da página. As funções de registro do SDK não constituem um sandbox. O campo `permissions` não faz parte do modelo de complemento atualmente aplicado pelo carregador; não presuma que permissões declaradas em um manifesto sejam fiscalizadas.

O carregador atual usa `matchUrls` como verificação básica de compatibilidade, não como allowlist de origem. Uma validação positiva não significa que o script seja confiável.

## Dados locais

O arquivo `settings.json` pode conter o e-mail selecionado e a URI persistente de uma árvore SAF autorizada. Trate-o como informação privada. Não o inclua em capturas de tela, logs ou relatórios públicos sem higienização.

Arquivos `.env`, `google-services.json` e materiais de assinatura devem permanecer fora do controle de versão. O arquivo `.env.example` deve conter apenas nomes e valores fictícios/comentados.

## Revisão de alterações

Mudanças em `MainActivity.kt`, `ArenaJsInterface.kt`, `ArenaFileBridge.kt`, `ArenaAddonManager.kt` ou nas opções de WebView devem incluir uma descrição explícita da superfície afetada e dos testes feitos. Alterações em caminhos de armazenamento precisam incluir testes para traversal, autorização SAF e falhas de permissão.
