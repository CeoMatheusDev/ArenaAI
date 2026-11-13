# Extensões e plugins

O Arena AI permite carregar complementos locais de dois tipos: **extensões** e **plugins**. Ambos são arquivos JavaScript e podem, opcionalmente, fornecer CSS. O carregador encontra esses arquivos no armazenamento do aplicativo, em documentos públicos ou em uma pasta autorizada pelo usuário.

## Diretórios reconhecidos

Estrutura recomendada:

```text
ArenaAI/
├── settings.json
├── document.md
├── extension/
│   ├── meu-tema/
│   │   ├── manifest.json
│   │   ├── index.js
│   │   └── style.css
│   └── atalho.js
└── plugin/
    └── meu-plugin/
        ├── manifest.json
        └── index.js
```

Os nomes de diretório reconhecidos incluem:

- extensões: ``extensions/`, `extencao/` e `extensão/`;
- plugins: `plugin/` e `plugins/`.

O carregador procura arquivos `.js`, `.mjs` e `.json`. Um diretório de complemento pode conter `manifest.json` e arquivos de entrada/estilo referenciados por ele.

## Formato A — diretório com manifest.json

Exemplo:

```json
{
  "id": "meu-tema-neon",
  "name": "Tema Neon",
  "version": "1.0.0",
  "type": "extension",
  "description": "Personaliza elementos visuais do Arena AI.",
  "author": "Seu Nome",
  "minAppVersion": "2.0.0",
  "compatible": true,
  "matchUrls": ["*://arena.ai/*"],
  "entryPoint": "index.js",
  "styles": "style.css"
}
```

### Campos reconhecidos

| Campo | Tipo | Valor padrão/comportamento |
| --- | --- | --- |
| `id` | string | Se omitido, é derivado do caminho de origem. Use um identificador estável e único. |
| `name` | string | Usa o ID quando omitido. |
| `version` | string | `1.0.0`. |
| `type` | string | Inferido pela pasta; valores explícitos aceitos: `extension` ou `plugin`. |
| `description` | string | Texto genérico do aplicativo. |
| `author` | string | `Desenvolvedor`. |
| `minAppVersion` | string | `1.0.0`. |
| `compatible` | boolean | `true`. Defina `false` para impedir o carregamento. |
| `matchUrls` | array de strings | `["*://arena.ai/*"]`. |
| `entryPoint` | string | Arquivo JavaScript principal. Se omitido, procura `index.js` ao lado do manifesto. |
| `script` | string | JavaScript inline; tem precedência sobre `entryPoint`. |
| `styles` | string | Caminho para um arquivo CSS opcional. |
| `css` | string | CSS inline; tem precedência sobre `styles`. |

O campo `type` pode usar `extension`, `plugin`, `extensao` ou `extensão`. Os nomes recomendados para diretórios são os apresentados na seção anterior.

## Formato B — arquivo JavaScript único

Um arquivo `.js` ou `.mjs` pode ser colocado diretamente em `extension/` ou `plugin/`. O cabeçalho utiliza comentários `@tag valor`:

```javascript
// ==ArenaAI==
// @id            atalho-prompt
// @name          Atalho de Prompt
// @version       1.0.0
// @type          plugin
// @description   Adiciona um atalho de prompt.
// @author        Seu Nome
// @minAppVersion 2.0.0
// @match         *://arena.ai/*
// @compatible    true
// ==/ArenaAI==

(function (ArenaAI) {
    ArenaAI.registerPlugin({
        id: "atalho-prompt",
        onReady: function () {
            ArenaAI.ui.injectCSS(
                ".arena-ai-shortcut { position: fixed; z-index: 9999; }"
            );
        }
    });
})(window.ArenaAI);
```

O tipo padrão é inferido da pasta. Os campos reconhecidos no cabeçalho incluem `@id`, `@name`, `@version`, `@type`, `@description`, `@author`, `@minAppVersion`, `@compatible` e `@match`. O valor de `@match` pode conter padrões separados por vírgula.

## SDK JavaScript — window.ArenaAI

O SDK é inicializado pelo aplicativo no WebView. As APIs implementadas incluem:

| API | Descrição |
| --- | --- |
| `ArenaAI.version` | Versão do aplicativo Android. |
| `ArenaAI.platform` | Identificador `android-webview`. |
| `ArenaAI.connectedEmail` | E-mail selecionado pelo usuário no aplicativo. |
| `ArenaAI.requestLogin()` | Solicita o fluxo de seleção de conta do Android. |
| `ArenaAI.notify(title, message)` | Solicita uma notificação nativa. |
| `ArenaAI.storage.get(key)` | Lê o valor de `localStorage` com prefixo `arena_addon_`. |
| `ArenaAI.storage.set(key, value)` | Persiste um valor em `localStorage` com o mesmo prefixo. |
| `ArenaAI.ui.injectCSS(css)` | Insere uma tag `style` na página. |
| `ArenaAI.registerExtension(config)` | Registra uma extensão e chama `config.onReady`, quando presente. |
| `ArenaAI.registerPlugin(config)` | Registra um plugin e chama `config.onReady`, quando presente. |
| `ArenaAI.onResponseComplete(callback)` | Registra callback após a detecção de conclusão da resposta. |
| `ArenaAI.onPromptSubmit(callback)` | Registra callback quando o monitor detecta o envio do prompt. |
| `ArenaAI.onAccountConnected(callback)` | Registra callback quando o e-mail conectado é publicado no SDK. |
| `ArenaAI.fs` | Referência à ponte nativa `window.ArenaFS`, quando disponível. |

Exemplo:

```javascript
window.ArenaAI.onResponseComplete(function (preview) {
    console.log("Resposta concluída:", preview);
});

window.ArenaAI.onPromptSubmit(function (prompt) {
    console.log("Prompt enviado:", prompt);
});
```

## Ponte de arquivos — window.ArenaFS

A interface nativa registrada no WebView expõe os seguintes métodos síncronos:

| Método | Retorno/uso |
| --- | --- |
| `getArenaDir()` | Caminho da pasta ArenaAI usada pelo aplicativo. |
| `getAuthorizedDirectoryUri()` | URI da pasta autorizada, quando configurada. |
| `listFiles(path)` | JSON com `ok`, `path` e entradas contendo nome, caminho, tipo, tamanho e data de modificação. |
| `readFile(path)` | Conteúdo textual UTF-8 ou `null`. |
| `writeFile(path, content)` | `true` quando a gravação é concluída; caso contrário, `false`. |
| `exists(path)` | Informa se o caminho existe. |
| `mkdir(path)` | Cria diretório. |
| `deleteFile(path)` | Remove arquivo. |
| `pickDirectory()` | Abre o seletor de diretórios Android e retorna a URI autorizada, ou texto vazio quando cancelado/falha. |

Exemplo:

```javascript
const result = JSON.parse(window.ArenaFS.listFiles("extencao"));

if (result.ok) {
    console.log(result.entries);
}

window.ArenaFS.writeFile("plugin/notas.txt", "Salvo pelo plugin.");
const content = window.ArenaFS.readFile("plugin/notas.txt");
```

Caminhos relativos são resolvidos em relação à pasta ArenaAI. A implementação também verifica raízes de armazenamento permitidas e rejeita caminhos que escapem dessas raízes. O acesso a diretórios escolhidos pelo usuário depende da autorização do SAF.

## Compatibilidade e limitações

- A versão atual declarada no Gradle é `2.0.0`.
- `minAppVersion` é comparado com a versão do aplicativo.
- Complementos sem script nem CSS são considerados incompatíveis.
- O teste de `matchUrls` é simplificado: reconhece padrões amplos e textos que contenham `arena.ai`. Ele não implementa um mecanismo completo de correspondência de URLs e não deve ser usado como controle de segurança.
- Quando o carregamento automático está habilitado, os complementos ativos e considerados compatíveis são montados no WebView.
- Os callbacks de prompt e conclusão dependem de heurísticas sobre o DOM do site remoto; podem parar de funcionar após mudanças na interface web.

## Segurança

**Um complemento equivale a código executável.** Seus scripts são executados no contexto da página e podem acessar o SDK e as pontes nativas disponibilizadas pelo aplicativo. Não existe isolamento independente ou aplicação de permissões por complemento no carregador atual.

Não instale complementos de origem desconhecida. Leia [SECURITY.md](../SECURITY.md) antes de desenvolver ou distribuir scripts.
