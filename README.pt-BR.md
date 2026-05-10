# Card Vault API

API REST segura para cadastro e consulta de números de cartão de crédito/débito (PANs).  
Construída com **Java 21**, **Spring Boot**, **MySQL 8**, **Testcontainers** e **Gradle**.

---

## Índice

- [Visão Geral da Arquitetura](#visão-geral-da-arquitetura)
- [Modelo de Segurança](#modelo-de-segurança)
- [Pré-requisitos](#pré-requisitos)
- [Executando Localmente — Docker Compose (Recomendado)](#executando-localmente--docker-compose-recomendado)
- [Executando Localmente — Sem Docker](#executando-localmente--sem-docker)
- [Executando os Testes](#executando-os-testes)
- [Referência da API](#referência-da-api)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Considerações de Escalabilidade](#considerações-de-escalabilidade)

---

## Visão Geral da Arquitetura

```
┌─────────────────────────────────────────────────────┐
│                   Cliente (HTTPS)                   │
└──────────────────────┬──────────────────────────────┘
                       │ Bearer JWT
┌──────────────────────▼──────────────────────────────┐
│  Cadeia de Filtros do Spring Security               │
│  ├─ AuditLoggingFilter  (audita req/resp selecionados) │
│  └─ JwtAuthenticationFilter  (valida o token)       │
├─────────────────────────────────────────────────────┤
│  Controllers                                        │
│  ├─ POST /api/v1/auth/login                         │
│  ├─ POST /api/v1/cards          (cartão único)      │
│  ├─ POST /api/v1/cards/batch    (upload TXT)        │
│  ├─ GET  /api/v1/cards/batch/{jobId}/status         │
│  └─ GET  /api/v1/cards/{pan}    (consulta)          │
├─────────────────────────────────────────────────────┤
│  Services                                           │
│  ├─ CardService  (cadastro / consulta)              │
│  ├─ BatchProcessingService  (importação async)      │
│  ├─ AuthService  (emissão de JWT)                   │
│  └─ AuditLogService  (persistência assíncrona)      │
├─────────────────────────────────────────────────────┤
│  Utilitários de Segurança                           │
│  └─ CardEncryptionUtil  (AES-256-GCM + HMAC-SHA256) │
├─────────────────────────────────────────────────────┤
│  MySQL 8  (migrações via Flyway)                    │
│  ├─ users        (senhas com BCrypt)                │
│  ├─ cards        (PAN criptografado, hash HMAC)     │
│  ├─ batch_jobs                                      │
│  └─ audit_logs   (corpos criptografados)            │
└─────────────────────────────────────────────────────┘
```

---

## Modelo de Segurança

| Camada | Mecanismo |
|---|---|
| Autenticação | JWT assinado com HMAC, validade de 24 h |
| Número do cartão em repouso | AES-256-GCM (IV aleatório por registro) |
| Consulta de cartão | Hash HMAC-SHA256 com índice dedicado |
| Senhas | BCrypt (força 12) |
| Auditoria | Endpoints sensíveis têm corpos criptografados com AES-256-GCM antes de persistir; chamadas do Swagger, Actuator e requisições anônimas são ignoradas |

### Por que HMAC e não SHA-256 puro?

SHA-256 puro permite ataques de rainbow table: um atacante com acesso ao banco pode verificar se um número de cartão existe sem conhecer a chave. O HMAC-SHA256 usa uma chave secreta — sem ela, o hash não pode ser reproduzido.

### Como funciona a auditoria de dados sensíveis?

O `AuditLoggingFilter` classifica cada requisição antes de persistir:

- **Endpoints sensíveis** (`/api/v1/auth`, `/api/v1/cards`) — os corpos da requisição e da resposta são **criptografados com AES-256-GCM** antes de serem armazenados. Operadores autorizados com acesso à chave de criptografia podem descriptografar e inspecionar o conteúdo completo quando necessário.
- **Demais endpoints** — corpos armazenados em texto simples.
- **Requisições anônimas e de ferramentas** (Swagger, Actuator) — ignoradas, nenhum registro criado.

---

## Pré-requisitos

| Ferramenta | Versão | Finalidade |
|---|---|---|
| Java | 21+ | Compilar / executar a aplicação |
| Docker | Latest | Executar o MySQL e a aplicação |
| Docker Compose | v2+ | Orquestrar o ambiente local |
| Git | Qualquer | Clonar o repositório |

> **Docker é o único pré-requisito obrigatório para rodar o stack completo localmente.**  
> Java é necessário apenas se você quiser compilar ou rodar sem Docker.

---

## Executando Localmente — Docker Compose (Recomendado)

Esta é a forma mais rápida de colocar a API em funcionamento — nenhuma configuração prévia é necessária.

### 1. Clone o repositório

```bash
git clone https://github.com/lucas-amaral/card-vault.git
cd card-vault
```

### 2. (Opcional, mas recomendado) Defina seus próprios segredos

Crie um arquivo `.env` na raiz do projeto e substitua os valores de exemplo:

```env
DB_USERNAME=cardvault
DB_PASSWORD=cardvault123
JWT_SECRET=substitua_por_pelo_menos_32_caracteres_aleatorios
CARD_ENCRYPTION_KEY=substitua_por_exatamente_32_chars!!
```

O Docker Compose conecta o container da aplicação ao MySQL via `db:3306`. Use uma URL JDBC com `localhost` apenas quando executar a aplicação diretamente pelo IDE ou Gradle.

> Se este passo for ignorado, a aplicação iniciará com **valores padrão de desenvolvimento**, adequados para testes locais mas que **não devem ser usados em ambientes reais**.

### 3. Inicie tudo

```bash
docker compose up --build
```

O Docker Compose irá:
1. Baixar e iniciar um container MySQL 8
2. Aguardar o MySQL estar saudável
3. Compilar a imagem da aplicação Spring Boot
4. Iniciar a API (o Flyway executa as migrações automaticamente na inicialização)

### 4. Verifique se está funcionando

```bash
curl http://localhost:8080/actuator/health
# Esperado: {"status":"UP"}
```

**Swagger UI:** http://localhost:8080/swagger-ui.html

### Credenciais padrão (criadas pelo Flyway)

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `Admin@123` | `ROLE_ADMIN` |

### Parar a aplicação

```bash
docker compose down          # Para os containers (dados preservados)
docker compose down -v       # Para e apaga o volume do banco de dados
```

---

## Executando Localmente — Sem Docker

Use esta abordagem para desenvolvimento ativo com hot-reload via Gradle.

### 1. Inicie apenas o banco de dados

```bash
docker compose up db -d
```

### 2. Configure as variáveis de ambiente

```bash
export DB_URL=jdbc:mysql://localhost:3306/cardvault?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
export DB_USERNAME=cardvault
export DB_PASSWORD=cardvault123
export JWT_SECRET=segredo_local_com_pelo_menos_32_caracteres
export CARD_ENCRYPTION_KEY=chavelocal1234567890123456789012
```

### 3. Execute a aplicação

```bash
./gradlew bootRun
```

A API estará disponível em http://localhost:8080.

---

## Executando os Testes

### Testes unitários (sem Docker)

```bash
./gradlew test
```

### Testes de integração (requer Docker para o Testcontainers)

O Testcontainers sobe um container MySQL real automaticamente durante a execução dos testes.

```bash
./gradlew integrationTest
```

### Todos os testes

```bash
./gradlew test integrationTest
```

### Relatórios

```bash
# Resultado dos testes
open build/reports/tests/test/index.html

# Cobertura de código (JaCoCo)
open build/reports/jacoco/test/html/index.html
```

---

## Referência da API

### Autenticação

#### `POST /api/v1/auth/login`

```json
// Requisição
{
  "username": "admin",
  "password": "Admin@123"
}

// Resposta 200
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "<JWT>",
    "tokenType": "Bearer",
    "expiresIn": 86400000
  },
  "timestamp": "2024-05-08T10:00:00"
}
```

Use o `accessToken` em todas as requisições seguintes:

```
Authorization: Bearer <accessToken>
```

---

### Cadastrar um Único Cartão

#### `POST /api/v1/cards`

```json
// Requisição
{
  "cardNumber": "4456897999999999"
}

// Resposta 201 — cartão cadastrado com sucesso
{
  "success": true,
  "message": "Card registered successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "batchName": null,
    "createdAt": "2024-05-08T10:01:00"
  }
}

// Resposta 200 — cartão já existia
{
  "success": true,
  "message": "Card already registered",
  "data": { "id": "550e8400-e29b-41d4-a716-446655440000", ... }
}
```

---

### Upload em Lote (Arquivo TXT)

#### `POST /api/v1/cards/batch` — `multipart/form-data`

O processamento é **assíncrono**. O endpoint retorna imediatamente um `jobId` para acompanhamento.

```bash
curl -X POST https://<seu-host>/api/v1/cards/batch \
  -H "Authorization: Bearer <token>" \
  -F "file=@DESAFIO-HYPERATIVA.txt"
```

```json
// Resposta 202
{
  "success": true,
  "message": "Batch job accepted. Poll /api/v1/cards/batch/550e8400-.../status for progress.",
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "filename": "DESAFIO-HYPERATIVA.txt",
    "totalParsed": null,
    "inserted": null,
    "skipped": null,
    "error": null,
    "createdAt": "2024-05-08T10:01:00",
    "updatedAt": "2024-05-08T10:01:00"
  }
}
```

#### `GET /api/v1/cards/batch/{jobId}/status`

Use este endpoint para verificar o progresso do processamento. Os status possíveis são: `PENDING`, `PROCESSING`, `DONE` e `FAILED`.

```json
// Resposta 200
{
  "success": true,
  "message": "Job status retrieved",
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "DONE",
    "filename": "DESAFIO-HYPERATIVA.txt",
    "totalParsed": 9,
    "inserted": 7,
    "skipped": 2,
    "error": null,
    "createdAt": "2024-05-08T10:01:00",
    "updatedAt": "2024-05-08T10:01:03"
  }
}
```

#### Formato do Arquivo TXT

```
DESAFIO-HYPERATIVA 20180524LOTE0001000010   ← cabeçalho
C1 4456897999999999                          ← linha de cartão (C + sequência + espaço + PAN)
C2 4456897922969999
...
LOTE0001000010                               ← rodapé
```

- Coluna 01 = `C` (identificador de linha de cartão)
- Colunas 02–07 = número sequencial no lote
- Colunas 08–26 = número do cartão (alinhado à esquerda, preenchido com espaços)
- Números inválidos (não numéricos, menos de 13 ou mais de 19 dígitos) são ignorados

---

### Consultar um Cartão

#### `GET /api/v1/cards/{cardNumber}`

```bash
curl https://<seu-host>/api/v1/cards/4456897999999999 \
  -H "Authorization: Bearer <token>"
```

```json
// Resposta 200 — encontrado
{
  "success": true,
  "message": "Card found",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "batchName": "LOTE0001",
    "createdAt": "2024-05-08T10:01:00"
  }
}

// Resposta 404 — não encontrado
{
  "success": false,
  "message": "Card not found"
}
```

---

## Variáveis de Ambiente

| Variável | Padrão (apenas dev) | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/cardvault...` | URL de conexão JDBC. Quando executado via Docker Compose, aponta para `db:3306` |
| `DB_USERNAME` | `root` | Usuário do banco de dados |
| `DB_PASSWORD` | `root` | Senha do banco de dados |
| `JWT_SECRET` | *(padrão inseguro de dev)* | Chave de assinatura HMAC do JWT — **obrigatório alterar em produção** |
| `JWT_EXPIRATION` | `86400000` | Validade do token em milissegundos (24 h) |
| `CARD_ENCRYPTION_KEY` | *(padrão inseguro de dev)* | Chave AES-256 para criptografia dos cartões e dos corpos de auditoria — **obrigatório alterar em produção** |

---

## Estrutura do Projeto

```
src/
├── main/java/br/com/amaral/cardvault/
│   ├── config/          # Configuração de Segurança, Async e OpenAPI
│   ├── controller/      # Endpoints REST
│   ├── dto/             # Records de Requisição e Resposta
│   ├── entity/          # Entidades JPA
│   ├── exception/       # Tratamento global de erros
│   ├── filter/          # Filtros de JWT e Auditoria
│   ├── repository/      # Interfaces Spring Data JPA
│   ├── service/         # Lógica de negócio (interfaces + implementações)
│   └── util/            # Criptografia de cartões e payloads de auditoria
├── main/resources/
│   ├── application.properties
│   └── db/migration/    # Scripts SQL do Flyway (V1–V4)
└── test/java/...        # Testes unitários + testes de integração com Testcontainers
```

---

## Considerações de Escalabilidade

- **Consulta por hash** — `cards.card_hash` é indexado; verificações de existência são O(1) independente do volume de dados.
- **Processamento assíncrono de lotes** — o arquivo TXT é lido linha a linha (streaming), nunca carregado inteiro na memória. Cartões são persistidos em chunks de 500 por transação, preservando o progresso em caso de falha.
- **JWT stateless** — a aplicação é horizontalmente escalável sem estado de sessão compartilhado.
- **Log de auditoria assíncrono** — a persistência do log de auditoria é feita em thread separada e nunca bloqueia a requisição principal.
- **Pool de conexões** — HikariCP (padrão do Spring Boot) está pré-configurado para reuso eficiente de conexões com o banco.
- **Escalabilidade futura** — para volumes muito grandes (milhões de linhas por arquivo), recomenda-se avaliar uma estratégia de bulk-upsert ou processamento via fila de mensagens (Kafka, RabbitMQ).
