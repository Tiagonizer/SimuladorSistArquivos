# Simulador de Sistema de Arquivos com Journaling

**Projeto:** Simulador de Sistema de Arquivos (Java) — versão educativa

Este repositório contém um simulador simples de sistema de arquivos escrito em Java, com suporte a journaling (log de operações) para garantir integridade e recuperação após falhas. O objetivo é didático: demonstrar conceitos de organização de arquivos, operações de manipulação e um mecanismo básico de journaling.

**Observação:** Este README deve ser a primeira página do repositório no GitHub e também convertido em PDF para envio ao AVA.

---

**Sumário**

- **Introdução**
- **Metodologia**
- **Parte 1 — Introdução ao Sistema de Arquivos com Journaling**
- **Parte 2 — Arquitetura do Simulador**
- **Parte 3 — Implementação em Java**
- **Parte 4 — Instalação e Funcionamento**
- **Link do GitHub**

---

**Introdução**

O gerenciamento de arquivos é um componente central dos sistemas operacionais. Este trabalho apresenta um simulador que permite experimentar operações básicas de arquivos e diretórios (criar, apagar, renomear, copiar, listar) e demonstra um mecanismo de journaling para preservar a consistência do sistema em caso de falhas.

**Metodologia**

O simulador foi implementado em Java. As operações são expostas por métodos da classe `FileSystemSimulator`. Antes de executar cada operação relevante, o simulador grava uma entrada no `Journal` com o status `START`; após a operação bem-sucedida escreve `COMMIT`. Na inicialização, o sistema lê o `journal.log` e reaplica operações `START` sem `COMMIT` (replay) para garantir consistência.

Parte do trabalho inclui um modo interativo (um shell simples) para executar comandos como em um terminal.

---

**Parte 1 — Introdução ao Sistema de Arquivos com Journaling**

- O que é um sistema de arquivos: mecanismo que organiza, armazena e recupera arquivos em um dispositivo de armazenamento. Fornece hierarquia de diretórios, metadados e acesso a conteúdo.
- Journaling: técnica que registra modificações propostas em um log antes de aplicá-las ao sistema de arquivos. Se ocorrer uma falha (queda de energia, crash), o sistema pode aplicar (replay) ou descartar operações incompletas a partir do log, restaurando um estado consistente.
- Tipos de journaling (breve):
  - Write-ahead logging (WAL): registra as mudanças antes de aplicá-las.
  - Log-structured: escreve diretamente em um log que serve como estrutura principal.
  - Metadata-only journaling: apenas metadados são logados (mais rápido), usado por ext3 em modo ordered.

---

**Parte 2 — Arquitetura do Simulador**

- Estruturas de dados principais (classes Java):
  - `FSNode` (abstrata): representa um nó do sistema (arquivo ou diretório) com nome e timestamp.
  - `FSFile`: representa arquivo, contém conteúdo (String) e métodos de leitura/escrita.
  - `FSDirectory`: representa diretório, mantém listas de arquivos e subdiretórios.
  - `Journal`: gerencia o arquivo `journal.log`, grava entradas serializadas e fornece leitura para recuperação.
  - `FileSystemSimulator`: expõe as operações (mkdir, rmdir, touch, rm, cp, mv, write, ls, cat) e integra a gravação no journal.
  - `Main`: REPL (shell) que interpreta comandos do usuário e chama `FileSystemSimulator`.

- Formato de log: cada entrada é gravada em linha única em formato simples (campo `id`, `op`, `ts`, `status`, `params`). Exemplo:

```
{"id":1,"op":"MKDIR","ts":...,"status":"START","params":{"path":"/docs"}}
{"id":1,"op":"MKDIR","ts":...,"status":"COMMIT","params":{"path":"/docs"}}
```

---

**Parte 3 — Implementação em Java**

Arquivos principais (agora separados em arquivos `.java` individuais no diretório do projeto):

- `Main.java` — shell (REPL) que interpreta comandos do usuário e delega para o simulador.
- `FileSystemSimulator.java` — implementação do simulador e integração com o journal.
- `Journal.java` — implementação do journal (grava e lê `journal.log`).
- `FSNode.java`, `FSFile.java`, `FSDirectory.java` — modelos de nó/arquivo/diretório.
- `PathParts.java` — helper para manipulação de caminhos.
- `FSException.java` — exceção personalizada usada pelo simulador.

Principais operações suportadas (mesma lista funcional):

- `mkdir <path>` — cria diretório (cria intermediários quando aplicável durante recuperação)
- `rmdir <path>` — remove diretório (deve estar vazio)
- `touch <path>` — cria arquivo vazio
- `rm <path>` — remove arquivo
- `cp <src> <dest>` — copia arquivo ou diretório recursivamente
- `mv <src> <dest>` — renomeia/move arquivo ou diretório
- `write <path> <content>` — grava (substitui) conteúdo do arquivo
- `cat <path>` — mostra conteúdo de arquivo
- `ls <path>` — lista conteúdo do diretório (padrão `/`)
- `journal show` / `journal clear` — ver/limpar journal
- `persist` — força persistência do estado atual em `fs.data`

O `Journal` grava uma entrada `START` antes da execução e `COMMIT` após sucesso; somente operações com `COMMIT` são consideradas seguras sem replay.
---


**Parte 4 — Instalação e Funcionamento**

Requisitos:

- Java JDK 11+ instalado

Compilar e executar (Windows PowerShell) — usando os arquivos `.java` separados:

```powershell
javac *.java
java Main
```

Se você preferir manter o código em um pacote ou em `src/`, posicione os arquivos dentro de `src/simulador/` e ajuste o `package` nas classes; então compile com `javac` apontando para a pasta `src`.

Ao executar, você verá o prompt `fs>`. Exemplos de uso:

- `mkdir /docs`
- `write /docs/relatorio.txt "Trabalho de Sistemas Operacionais"`
- `ls /docs`
- `cat /docs/relatorio.txt`
- `cp /docs/relatorio.txt /docs/relatorio_backup.txt`
- `mv /docs/relatorio_backup.txt /docs/relatorio_v2.txt`
- `rm /docs/relatorio_v2.txt`
- `journal show`
- `persist`
- `exit`

Observações:

- O comando `write` trata aspas para permitir espaços no conteúdo; se precisar gravar dados binários, converta-os para Base64 no cliente e salve como texto (ou eu posso estender o simulador para suportar base64 nativamente).
- O estado persistido é armazenado em `fs.data` (serialização Java). O `journal.log` mantém o histórico de operações para recuperação.

---

**Conversão para PDF**

No GitHub, abra o `README.md` e use a opção de impressão/exportação do navegador para gerar PDF. Alternativamente, localmente, você pode usar ferramentas como `pandoc`:

```powershell
pandoc README.md -o relatorio.pdf
```

---

**Link do GitHub**

- Substitua no topo do documento o placeholder pelo link do repositório quando disponível: `https://github.com/SEU-USUARIO/SEU-REPO`

---

Se quiser, eu posso:
- adicionar um `Makefile`/script PowerShell para compilar e executar automaticamente;
- separar as classes em arquivos `.java` distintos dentro de `src/` e criar uma estrutura de projeto (ex.: `src/simulador/*`);
- adicionar testes unitários simples;
- ou gerar o PDF já pronto a partir deste `README.md`.

Informe qual desses itens deseja que eu faça em seguida.
