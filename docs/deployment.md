# Implantação e custos

Status: execução local do núcleo, da API, do Functions e da imagem **TESTADA** (Etapas 2 a 6); scripts de carga **TESTADOS** localmente (Etapa 7); **implantação no Azure (Etapa 9) IMPLANTADA e TESTADA em smokes em 25/11/2026** (ver `testing.md`, seção "Etapa 9"). Todas as saídas foram informadas pelo autor.

## Regra de aprovação (definida pelo autor)

**Nenhum recurso pago é criado sem confirmação explícita do autor**, depois da lista de recursos, dos custos possíveis e da conferência do crédito. Plano de remoção: apagar o grupo de recursos ao final do projeto (e, enquanto durar, parar o banco ao fim de cada sessão).

## Assinatura e crédito (informado pelo autor)

- Conta pessoal do Azure criada em 22/11/2026; a assinatura aparece como "Azure subscription 1" ("Plano do Azure"). Nos primeiros minutos a tela de créditos estava vazia; depois apareceu **R$ 1.075,17 em créditos, com validade até 22/12/2026** (avaliação gratuita: os serviços são pausados ao fim da avaliação ou quando o crédito acaba; o cartão não é cobrado enquanto o autor não migrar para pagamento conforme o uso).
- Captura de 25/11/2026: custos incorridos R$ 4,10; créditos disponíveis R$ 1.071; término da avaliação em 22/12/2026. Por recurso: banco R$ 2,58; registro de contêiner R$ 1,52; conta de armazenamento menos de R$ 0,01.
- O botão "Atualizar para pagamento conforme o uso" **não** deve ser usado (ver `azure-resource-plan.md`).

## Recursos criados (grupo `rg-library-tcc`, região Brazil South)

| Recurso | Nome | Configuração |
|---|---|---|
| PostgreSQL servidor flexível | `pglibtcc76151` | General Purpose `Standard_D2ds_v5` (2 vCores), 32 GiB, PostgreSQL 17.11, sem alta disponibilidade, backup de 7 dias, acesso público com regra para serviços do Azure e para o IP do autor |
| Registro de contêiner | `acrlibtcc77218` | Basic; imagem `library-api:v1` (enviada do `library-api:local`, 444 MB em disco, 120 MB de conteúdo) |
| Identidade gerenciada | `id-library-acr` | função `AcrPull` no registro |
| Ambiente do Container Apps | `cae-library-tcc` | plano Consumption; espaço de trabalho do Log Analytics criado automaticamente (`workspace-rglibrarytccIhlv`) |
| Container App | `ca-library-api` | 1 vCPU, 2 GiB, 0 a 5 réplicas, ingresso externo na porta 8080; senha do banco como segredo do app |
| Conta de armazenamento | `stlibtcc76352` | Standard_LRS (usada pelo Functions) |
| Function App (Flex Consumption) | `fn-library-tcc-25367` | Java 21, instância de 2.048 MB, máximo de 5 instâncias, 0 instâncias sempre prontas; Application Insights ligado automaticamente |

Os endereços públicos e os identificadores da assinatura **não** são registrados aqui. A senha do banco fica só em `.env.azure` (no `.gitignore`); o arquivo também guarda os nomes acima.

## Procedimento executado (PowerShell 5.1, Azure CLI)

Todo terminal novo precisa recarregar as variáveis de `.env.azure` (T-021). Os comandos abaixo são os que funcionaram; os que falharam estão em `troubleshooting.md` (T-015 a T-023).

1. **Entrar e registrar serviços:** `az login`; `az provider register --namespace <ns>` para `Microsoft.DBforPostgreSQL`, `Microsoft.ContainerRegistry`, `Microsoft.App`, `Microsoft.OperationalInsights`, `Microsoft.Web`, `Microsoft.Storage` e `Microsoft.Insights` (o `Microsoft.ManagedIdentity` foi registrado automaticamente, T-022); `az group create --name rg-library-tcc --location brazilsouth --tags projeto=tcc`; `az functionapp list-flexconsumption-locations` (Brazil South presente).
2. **Banco:** senha aleatória gravada em `.env.azure`; `az postgres flexible-server create ... --tier GeneralPurpose --sku-name Standard_D2ds_v5 --storage-size 32 --version 17 --backup-retention 7 --public-access 0.0.0.0` (sem `--high-availability`, T-015); regra de firewall do IP do autor (`firewall-rule create --server-name <servidor> --name meu-ip ...`, T-016); `CREATE DATABASE library`, `db/schema.sql` e `db/seed.sql -v rows=10000` com o `psql` da imagem `postgres:17` (via Docker). Conferido: 10.000 linhas, PostgreSQL 17.11, collation `en_US.utf8`, `max_connections` 859.
3. **Registro e imagem:** `az acr create --sku Basic`; `az acr login`; `docker tag` e `docker push` de `library-api:local` para `<registro>.azurecr.io/library-api:v1` (digest idêntico ao da imagem local).
4. **Container App:** extensão `containerapp`; identidade gerenciada com `AcrPull`; `az containerapp env create`; `az containerapp create` com `--cpu 1.0 --memory 2.0Gi --min-replicas 0 --max-replicas 5`, segredo `db-password` e as variáveis `DB_URL` (`...?sslmode=require`), `DB_USER`, `DB_PASSWORD` (referência ao segredo), `DB_POOL_MAX=5`, `DB_POOL_MIN_IDLE=1` e `DB_CONNECTION_TIMEOUT_MS=5000`.
5. **Functions:** `az functionapp list-flexconsumption-runtimes` (Java 21 é o padrão); conta de armazenamento; `az functionapp create --flexconsumption-location brazilsouth --runtime java --runtime-version 21`; `az functionapp scale config set --maximum-instance-count 5`; `az functionapp config appsettings set` com as mesmas variáveis `DB_*`; `mvn -pl adapter-functions -am clean package -DskipTests`; zip da pasta `target\azure-functions\library-cloud-benchmark-fn-local` **sem** `local.settings.json`, montado entrada por entrada com `/` e com `host.json` na raiz (T-018, T-019); `az functionapp deployment source config-zip`.
6. **Conferência:** `scripts/api-smoke.ps1` contra os dois endereços e `Compare-Object` com os registros locais (idênticos); `scripts/run-k6.ps1 ... -BaseUrl <endereço> -OutDir results\azure` (smokes de leitura e escrita).

## Parar e remover (plano de custos)

- Fim de cada sessão: `az postgres flexible-server stop --resource-group rg-library-tcc --name <servidor>` (o Azure religa o servidor sozinho depois de 7 dias, mensagem do próprio comando). Início: `... start`. Não foi verificado se o armazenamento continua cobrado com o servidor parado; os dados de custo atrasam algumas horas.
- Fim do projeto, **depois de exportar as evidências** (métricas do Azure Monitor e `results\azure`): `az group delete --name rg-library-tcc --yes --no-wait`. Isso apaga também o banco, o registro, as duas implantações e os logs.
- Meta do autor: terminar as Etapas 8 a 10 e exportar as evidências por volta de 10/12/2026 (a avaliação vence em 22/12/2026).

## Segurança (situação)

- As duas APIs ficam **públicas e sem autenticação** (inclusive `POST`, `PUT` e `DELETE`) durante a janela do experimento; os dados são sintéticos. Não divulgar os endereços; remover o grupo de recursos ao final.
- O banco aceita conexões de qualquer serviço do Azure (regra `0.0.0.0`) e do IP do autor; a senha é aleatória e fica fora do Git. O usuário usado pelas aplicações é o administrador do servidor (simplificação deste trabalho; não é o desenho recomendado para produção).
- Pendente: decidir se o acesso anônimo permanece nas baterias da Etapa 10.

## Fontes consultadas

Ver `tcc-notes.md` (C31 a C41). Preços em dinheiro vêm da calculadora e da ferramenta de custos, e os valores reais estão em `azure-resource-plan.md`.
