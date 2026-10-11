# library-cloud-benchmark
Uma API de biblioteca para avaliar arquiteturas em nuvem.

# library-cloud-benchmark

API REST de biblioteca usada como parte prática de um TCC de Engenharia de Software:

> **"Análise dos impactos da escolha entre arquiteturas Serverless e baseadas em Containers
> na escalabilidade, desempenho e custos de aplicações web."**

A mesma lógica de negócio será executada em **Azure Container Apps** (Spring Boot em container)
e em **Azure Functions** (Flex Consumption), e submetida aos mesmos testes de carga (k6).

## Status do projeto

| Etapa | Descrição | Status |
|---|---|---|
| 1 | Planejamento | Concluída |
| 2 | Ambiente + núcleo (`core`) | **Gerada — validar com `mvn test` na sua máquina** |
| 3–11 | Infraestrutura/JDBC, API Spring Boot, testes, Functions, Docker, k6, monitoramento, Azure, experimentos, documentação final | Não iniciadas |

Documentação completa e atualizada a cada etapa em [`docs/`](docs/README.md).

## Estrutura atual

```
library-cloud-benchmark/
├── pom.xml                 POM pai (versões e plugins centralizados)
├── core/                   Regras de negócio e casos de uso (Java puro)
│   ├── pom.xml
│   └── src/{main,test}/java/br/edu/tcc/librarycloud/
│       ├── domain/         Book + validações
│       └── application/    BookService, portas, paginação, exceções
└── docs/                   Arquitetura, ADRs, metodologia, diário, notas do TCC
```

Os módulos `infrastructure`, `adapter-container` e `adapter-functions` serão adicionados nas próximas etapas.

## Pré-requisitos (Etapa 2)

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | **21** (Temurin recomendado) | A versão 21 é a usada em todo o projeto |
| Apache Maven | **3.9.x** (a série estável mais recente) | Não usar a série 4.x (prévia) |
| Git | qualquer versão recente | Controle de versão |
| VS Code + *Extension Pack for Java* | atual | Inclui Maven e execução de testes |

Docker Desktop **não** é necessário nesta etapa (será na Etapa 3/4).

### Instalação no Windows (PowerShell)

```powershell
# 1) JDK 21 (Temurin). Se o winget não encontrar o pacote, baixe em https://adoptium.net
winget install --id EclipseAdoptium.Temurin.21.JDK -e

# 2) Git
winget install --id Git.Git -e
```

**Maven:** baixe o arquivo `apache-maven-3.9.x-bin.zip` em <https://maven.apache.org/download.cgi>,
extraia em `C:\tools` (ficará, por exemplo, `C:\tools\apache-maven-3.9.11`) e execute
(ajuste o nome da pasta para a versão que você baixou):

```powershell
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\tools\apache-maven-3.9.11", "User")
$p = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$p;C:\tools\apache-maven-3.9.11\bin", "User")
```

**Feche e reabra o VS Code** (as variáveis só valem em terminais novos).

### Verificar a instalação

```powershell
java -version      # deve mostrar 21.x
javac -version     # deve mostrar javac 21.x
mvn -v             # deve mostrar Apache Maven 3.9.x e "Java version: 21.x"
git --version
echo $env:JAVA_HOME   # deve apontar para a pasta do JDK 21
```

Se `JAVA_HOME` estiver vazio:

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium"      # descubra o nome exato da pasta
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\<pasta-do-jdk-21>", "User")
```

## Executar os testes

Na raiz do projeto (pasta que contém o `pom.xml` pai), no terminal do VS Code:

```powershell
mvn test
```

Resultado esperado ao final:

```
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Linhas `INFO: Livro criado: id=...` no meio da saída são normais (logs do serviço durante os testes).

Outros comandos úteis:

```powershell
mvn -pl core test            # só o módulo core
mvn -pl core -Dtest=BookServiceTest test   # só uma classe de teste
mvn clean test               # limpa e recompila tudo
```

No VS Code também é possível rodar pelo painel **Testing** (ícone de frasco) após abrir a pasta.

## Segurança

Nunca versione senhas, chaves ou strings de conexão. O `.gitignore` já exclui `.env` e
`local.settings.json`. Configurações reais virão de variáveis de ambiente (Etapa 3).
