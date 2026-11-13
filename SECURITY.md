# Segurança

Agradecemos relatos responsáveis de vulnerabilidades. **Não publique detalhes exploráveis em issues, pull requests ou discussões públicas.**

## Como reportar

Use o recurso **Security Advisories / Report a vulnerability** na página do repositório para enviar um relato privado, quando estiver habilitado. Caso o recurso não esteja disponível, entre em contato com o mantenedor pelo perfil do GitHub e solicite um canal privado.

Inclua, quando possível:

- versão do aplicativo e modelo/versão do Android;
- componente afetado e condições necessárias para reproduzir;
- passos mínimos de reprodução;
- impacto observado e evidências sem dados pessoais, tokens ou credenciais.

Não inclua informações sensíveis de usuários nem publique uma prova de conceito que exponha terceiros.

## Superfície de segurança relevante

O aplicativo carrega conteúdo remoto em um WebView com JavaScript habilitado e registra as interfaces nativas `AndroidBridge` e `ArenaFS`. O WebView também habilita armazenamento web, cookies de terceiros, acesso a conteúdo/arquivos e compatibilidade com conteúdo misto.

Extensões e plugins locais são scripts executados no contexto da página. Não existe isolamento JavaScript independente por complemento nem um sistema de permissões por complemento aplicado pelo carregador atual. O campo `matchUrls` é usado na avaliação básica de compatibilidade e **não deve ser tratado como uma política de segurança ou como uma barreira de origem**.

A ponte `ArenaFS` valida caminhos locais contra raízes permitidas e pode operar em uma pasta escolhida pelo usuário via SAF, mas isso não torna seguro executar código de terceiros dentro do WebView.

## Recomendações aos usuários

- Instale apenas extensões e plugins de fontes confiáveis e revise os scripts antes de habilitá-los.
- Não compartilhe `settings.json` sem revisar seu conteúdo; ele pode conter o e-mail conectado e a URI de uma pasta autorizada.
- Nunca envie `.env`, `google-services.json`, keystores, senhas, tokens ou dados privados em issues.
- Mantenha o Android e o aplicativo atualizados.

## Recomendações aos mantenedores

Mudanças na configuração do WebView, nos hosts permitidos, nas interfaces expostas por `addJavascriptInterface`, no carregamento de scripts e na ponte de arquivos exigem revisão de segurança. Priorize uma allowlist explícita de origens antes de expor interfaces nativas a conteúdo remoto e evite conceder acesso nativo além do estritamente necessário.
