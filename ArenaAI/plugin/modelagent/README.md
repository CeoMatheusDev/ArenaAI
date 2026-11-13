# ModelAgent 0.1.0

Plugin experimental para ArenaAI: três agentes (Build, Diálogo e Chat), catálogo OpenRouter, streaming SSE e handoff assistido para o composer da Arena AI.

## Instalação para teste

Copie a pasta `modelagent` inteira para:

```text
Documents/ArenaAI/plugin/modelagent/
  manifest.json
  index.js
  style.css
```

Também pode ser colocada na pasta `ArenaAI/plugin/modelagent` que o aplicativo já utiliza, desde que essa pasta seja um dos diretórios reconhecidos pelo ArenaAI. Se o armazenamento SAF estiver configurado, use o diretório autorizado pelo aplicativo.

Depois:

1. Abra o ArenaAI e carregue `https://arena.ai/agent`.
2. Abra as configurações de extensões/plugins e execute uma nova varredura ou habilite o carregamento automático.
3. Recarregue a página da Arena AI.
4. Toque no botão roxo **MA** no canto inferior direito.
5. Informe sua chave OpenRouter e toque em **Conectar**.
6. Escolha um agente e um modelo. Modelos gratuitos aparecem com o marcador FREE.
7. No agente Build, descreva a tarefa, gere o plano e use **Inserir na Arena**. Revise o texto no composer da Arena AI e envie manualmente.

## Agentes

- **Build:** o modelo OpenRouter atua como gerente/arquiteto e produz um handoff técnico para o modelo executor da Arena AI.
- **Diálogo:** agente generalista; pode habilitar o plugin de pesquisa web do OpenRouter na solicitação.
- **Chat:** respostas curtas e diretas.

Cada agente mantém histórico separado em memória durante a página atual.

## Segurança e limitações desta versão

- A chave OpenRouter permanece somente na memória JavaScript da página; não é gravada em localStorage, arquivos ou configurações. Fechar/recarregar a página exige informar novamente a chave.
- O plugin faz chamadas diretas do WebView para `https://openrouter.ai/api/v1`. Isso depende de CORS e da disponibilidade de streaming no WebView. Se o serviço bloquear a origem ou a rede, a conexão falhará; a próxima etapa adequada é usar HTTP nativo do ArenaAI.
- O handoff Build apenas preenche o composer. O plugin não clica em Enviar e não executa ações sem revisão humana.
- A bridge atual do ArenaAI expõe somente um preview de até 1.000 caracteres do fim da resposta. O plugin mostra esse preview, não o texto completo.
- O carregador executa plugins no contexto da página confiável. Shadow DOM isola a interface visual, não é uma sandbox de segurança.
- Esta versão não executa comandos arbitrários no sistema, não cria arquivos automaticamente e não possui permissões granulares por plugin.
- Catálogo de modelos é obtido de `GET /models`; detalhes exibidos dependem dos metadados que o OpenRouter fornece para cada modelo. O catálogo é paginado visualmente em lotes de 100 para evitar criar milhares de elementos de uma só vez.

## Arquivos

- `manifest.json`: metadados reconhecidos pelo carregador ArenaAI.
- `index.js`: UI, agentes, OpenRouter, SSE e integração assistida com composer.
- `style.css`: estilos isolados; o JS também injeta os estilos no Shadow DOM para manter compatibilidade com o carregador atual.

## API utilizada

- `GET https://openrouter.ai/api/v1/models`
- `POST https://openrouter.ai/api/v1/chat/completions` com `stream: true`.

Consulte https://openrouter.ai/docs/quickstart.
