# Exivamoeres

Organizador de times para caçar e trocar **Soul Cores** do Tibia. O nome vem
da magia `exiva moe res`, usada no jogo para procurar soul cores.

**Contexto do domínio:** Soul Cores são itens (um por criatura do Bestiary)
com drop rate baixo. Jogadores gastam cores no **Soulpit** para ganhar
**Animus Mastery** (bônus permanente contra a criatura). Como o drop é raro,
grupos se organizam para caçar juntos e repassar cores entre si — é esse
problema que o site resolve.

## Estado atual do projeto

Este repositório contém a **fundação** do sistema (sessão 1 de 2):

| Área | Estado |
|---|---|
| Monorepo, Docker Compose, envs | ✅ Completo |
| Modelo de dados (10 entidades + migrations Flyway) | ✅ Completo |
| Autenticação (JWT + refresh token + OAuth2 Discord/Google + anônimo) | ✅ Completo |
| Fluxo de verificação de personagem (CharacterClaim) | ✅ Completo, com testes de integração |
| Listas de caça, soulcores, sugestões, chat | 🔲 Só entidades/migrations/interfaces — ver `docs/proxima-sessao.md` |
| Frontend | 🔲 Só setup (Vite/Tailwind/React Query/Zustand/Router + client HTTP) |
| Deploy real (Railway/Netlify) | 🔲 Preparado (Dockerfile, envs), não configurado |

**Leia `docs/proxima-sessao.md` antes de continuar o desenvolvimento.**

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Web/Data JPA/Security,
  OAuth2 Client, Flyway, Resilience4j, Bucket4j, Actuator + Micrometer, Maven
- **Frontend:** React 18 + TypeScript (Vite), TailwindCSS, React Query,
  Zustand, React Router, Axios
- **Banco:** PostgreSQL 16
- **Integração externa:** [TibiaData API v4](https://api.tibiadata.com)

## Como rodar localmente

Pré-requisitos: JDK 21+, Maven 3.9+, Node 20+, Docker.

```bash
# 1. Variáveis de ambiente
cp .env.example .env        # preencha ao menos POSTGRES_PASSWORD e JWT_SECRET
# gere um segredo JWT: openssl rand -base64 64

# 2. Banco
docker compose up -d postgres

# 3. Backend (porta 8080)
cd backend
export $(grep -v '^#' ../.env | xargs)   # ou configure as envs na IDE
mvn spring-boot:run

# 4. Frontend (porta 5173)
cd ../frontend
cp .env.example .env
npm install
npm run dev
```

Alternativa: `docker compose up -d` sobe banco + backend juntos.

### Testes

```bash
cd backend && mvn test
```

Os testes de integração usam **Testcontainers** (Postgres real, migrations
Flyway reais) e **WireMock** (TibiaData simulada) — o Docker precisa estar
rodando.

## Arquitetura do backend

```
com.exivamoeres
├── domain/       entidades JPA, enums e exceções de domínio
├── repository/   interfaces Spring Data (queries derivadas, sem lógica)
├── service/      contratos (interfaces) + impl/ (regras de negócio)
├── controller/   REST controllers finos + handler global de erros
├── dto/          records de request/response (entidades nunca saem na API)
├── security/     JWT, filtros, OAuth2 (subpacote oauth/), rate limiting
├── scheduler/    job de polling de verificação de claims
├── client/       cliente TibiaData (WebClient + Resilience4j)
└── config/       properties tipadas, WebClient, CORS (no SecurityConfig)
```

Princípios seguidos (manter nas próximas sessões):

- Controllers **não** contêm lógica; services dependem de **interfaces**
  (ex.: `TibiaDataClient`, `ClaimVerificationService`).
- Entidades JPA nunca são expostas na API — sempre DTOs com Bean Validation.
- Schema 100% via Flyway (`ddl-auto: validate`); nunca `update` em produção.
- Nenhum segredo em arquivo versionado: tudo via variável de ambiente
  (ver `.env.example`).
- Código em inglês; comentários e mensagens de commit em português;
  comentários explicam o *porquê*.

## Modelo de dados

```
users ─┬─< characters >── (claims) character_claims
       ├─< refresh_tokens
       ├─< hunting_lists ─┬─< list_memberships >── characters
       │                  ├─< list_soulcores >── creatures
       │                  ├─< soulcore_suggestions >── creatures
       │                  └─< chat_messages
       └─< list_memberships
creatures ──< character_soulcores >── characters
```

- `users` — LOCAL (email+senha BCrypt), GOOGLE/DISCORD (OAuth) ou ANONYMOUS.
- `characters` — nome único global case-insensitive (índice em `lower(name)`);
  `user_id` nulo até um claim ser aprovado.
- `character_claims` — código de verificação, status
  PENDING/APPROVED/REJECTED, índice **parcial** em `status = 'PENDING'`
  (o job só varre pendentes).
- `creatures` — catálogo do Bestiary (seed parcial; importar completo na
  sessão 2).
- Demais tabelas (listas/soulcores/sugestões/chat) já existem com índices e
  constraints, aguardando a lógica da sessão 2.

## Fluxo de verificação de personagem (parte crítica)

1. `POST /api/claims` com o nome do personagem. O backend valida na
   TibiaData que o personagem existe e devolve um `verificationCode`
   (formato `EXIVA-XXXXXXXX`).
2. O usuário cola o código no campo **Comment** do personagem em Tibia.com.
3. Um job (`ClaimVerificationScheduler`, a cada 15 min) consulta a TibiaData
   e procura o código no comment. Matching **tolerante** (nunca igualdade
   exata): `trim + lowercase + contains` — implementado num único lugar,
   `CommentCodeMatcher`.
4. Encontrou: transação atômica (`ClaimTransitionService.approve`) aprova o
   claim, transfere a posse do personagem e **desativa as memberships do
   dono anterior**.
5. 24h sem verificação: claim vira REJECTED (expiração conta a partir de
   `created_at`, nunca de falhas de rede).
6. `POST /api/claims/{id}/verify-now` força a checagem imediata (mesma regra
   do job).

Resiliência: retry com backoff exponencial + circuit breaker (Resilience4j)
nas chamadas à TibiaData; falha de rede **não** atualiza `last_checked_at`
nem expira o claim. Todo polling gera log estruturado
(`claim.poll.*`, `tibiadata.fetch*`, `claim.approved`, ...).

## Endpoints atuais

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | Registro email+senha (rate limited) |
| POST | `/api/auth/login` | Login (rate limited) |
| POST | `/api/auth/anonymous` | Cria conta anônima + tokens |
| POST | `/api/auth/refresh` | Rotaciona refresh token |
| POST | `/api/auth/logout` | Revoga refresh token |
| GET | `/oauth2/authorization/{google\|discord}` | Início do login social |
| POST | `/api/claims` | Inicia claim de personagem |
| GET | `/api/claims` | Meus claims |
| GET | `/api/claims/{id}` | Detalhe do claim |
| POST | `/api/claims/{id}/verify-now` | Verificação sob demanda |
| GET | `/actuator/health` | Health check |

## Segurança

- BCrypt para senhas; JWT HMAC (segredo via `JWT_SECRET`, mínimo 64 chars).
- Refresh tokens opacos persistidos e rotacionados (revogáveis).
- Rate limiting por IP em login/registro (Bucket4j, 10/min).
- CORS restrito à origem `FRONTEND_URL` — nunca `*`.
- Validação de entrada em todos os DTOs (`@Valid` + Bean Validation).
- Mensagem de erro de login idêntica para email inexistente e senha errada
  (evita enumeração de contas).
- OAuth não vincula contas automaticamente por email (evita account takeover
  por provider que não verifica email).

## Decisões de arquitetura (e porquês)

- **TibiaData v4**: versão estável atual da API pública.
- **Refresh token em tabela** (não JWT): permite revogação imediata.
- **`@Scheduled` simples** (não Quartz): deploy single-instance; se escalar
  horizontalmente, adotar ShedLock/Quartz (documentado no scheduler).
- **WebClient reativo com `.block()` na fronteira**: o HTTP é não bloqueante
  (retry/circuit breaker reativos), mas transação JPA é presa à thread — o
  bloqueio acontece só na fronteira entre os dois mundos.
- **`ClaimTransitionService` separado**: transações via proxy do Spring não
  funcionam em auto-invocação; além de isolar cada transição de estado.
- **Prefixo `EXIVA-` no código**: reconhecível pelo usuário e elimina falso
  positivo do `contains` em comments longos.

## Deploy (preparado, não configurado)

- **Backend + Postgres → Railway**: usar `backend/Dockerfile`; configurar as
  envs do `.env.example`; `DATABASE_URL` vem do plugin Postgres do Railway
  (atenção: Railway fornece URL no formato `postgres://...` — converter para
  `jdbc:postgresql://...`).
- **Frontend → Netlify**: build `npm run build`, publicar `dist/`; definir
  `VITE_API_URL` apontando pro Railway; adicionar redirect SPA
  (`/* /index.html 200`).
- Callbacks OAuth em produção: registrar
  `https://<backend>/login/oauth2/code/{google|discord}` nos portais dos
  providers e ajustar `OAUTH2_REDIRECT_URL`/`FRONTEND_URL`.
