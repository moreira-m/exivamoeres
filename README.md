# Exivamoeres

Organizador de times para caçar e trocar **Soul Cores** do Tibia. O nome vem
da magia `exiva moe res`, usada no jogo para procurar soul cores.

**Contexto do domínio:** Soul Cores são itens (um por criatura do Bestiary)
com drop rate baixo. Jogadores gastam cores no **Soulpit** para ganhar
**Animus Mastery** (bônus permanente contra a criatura). Como o drop é raro,
grupos se organizam para caçar juntos e repassar cores entre si — é esse
problema que o site resolve.

## Estado atual do projeto

| Área | Estado |
|---|---|
| Monorepo, Docker Compose, envs | ✅ Completo |
| Modelo de dados (entidades + migrations Flyway V1–V5) | ✅ Completo |
| Autenticação (JWT + refresh token + OAuth2 Discord/Google + anônimo) | ✅ Completo |
| Fluxo de verificação de personagem (CharacterClaim) | ✅ Completo, com testes de integração |
| Times de soul core (criar, entrar, aprovar/recusar, limite de 5, world, Free/Premium) | ✅ Completo |
| Soul cores (obtido/desbloqueado) + sugestões automáticas | ✅ Completo |
| Chat por time (WebSocket/STOMP autenticado por JWT) | ✅ Completo |
| Frontend (área pública + área logada) | ✅ Completo |
| Deploy (Railway/Netlify) | ⚙️ Configurado (railway.json, netlify.toml, Dockerfile), falta provisionar |

Sessão 1 entregou a fundação (auth + claim). Sessão 2 entregou os times,
soul cores, chat e o frontend. Histórico de decisões em `docs/proxima-sessao.md`.

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
- `creatures` — catálogo do Bestiary importado da TibiaData no boot
  (`CreatureCatalogService` chama `/v4/creatures`: ~719 criaturas com nome,
  `race` e `image_url`). A TibiaData não expõe as estrelas do Bestiary, então
  `difficulty` é opcional (preenchida só nos 12 seeds da V3, que priorizam as
  sugestões).
- `hunting_lists` — time com `target_creature_id` (criatura-alvo) e
  `join_policy` (MANUAL_APPROVAL/AUTO_ACCEPT).
- `list_memberships` — participação por personagem, com `status`
  (PENDING/APPROVED/REJECTED) e `active`; índice **parcial** para pedidos
  pendentes. `chat_messages` é vinculada a personagem (`character_id`).

O schema evoluiu por migrations aditivas: V1–V4 (fundação) e **V5** (times,
join requests, chat por personagem, world/ícones). Nenhuma migration aplicada
é editada — mudanças futuras entram em V6+.

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

A verificação é **exclusivamente automática** (job de polling): não há
endpoint de verificação sob demanda — a UI avisa que a checagem pode levar
até ~15 min após colar o código no Comment.

Resiliência: retry com backoff exponencial + circuit breaker (Resilience4j)
nas chamadas à TibiaData; falha de rede **não** atualiza `last_checked_at`
nem expira o claim. Todo polling gera log estruturado
(`claim.poll.*`, `tibiadata.fetch*`, `claim.approved`, ...).

## Estrutura de navegação (frontend)

Duas áreas distintas:

- **Área pública (sem login):** página inicial (`/`) com filtros no topo para
  **buscar** times por mundo, criatura-alvo e vaga disponível; e o detalhe
  público de cada time (`/teams/:id`). É a experiência de quem só quer achar
  um time para entrar.
- **Área logada (`/account/*`, protegida):** criar times
  (`/account/teams/new`), gerenciar os próprios times e pedidos de entrada
  (`/account/teams`), e a aba de configuração de personagem
  (`/account/characters`) com o fluxo de claim/verificação e o botão
  "verificar agora". O callback do login social é `/oauth/callback`.

## Regras de negócio dos times

- **Máximo de 5 jogadores** por time (validado no backend com lock pessimista
  na linha do time durante join/aprovação; refletido na UI).
- **Mesmo world**: todos os personagens de um time são do mesmo mundo; ao
  entrar, o backend revalida o world do personagem via TibiaData.
- **Sem Free Account**: personagens Free Account não participam; o status de
  conta é consultado na TibiaData e **cacheado** (TTL configurável, default 1h).
- **Política de entrada** escolhida por quem cria: `MANUAL_APPROVAL` (pedidos
  ficam PENDING até o dono aceitar/recusar) ou `AUTO_ACCEPT` (entra direto,
  ainda respeitando as três regras acima).
- Sair de um time nunca deleta histórico (`active = false`); recusar um pedido
  marca `REJECTED`. Ao trocar o dono de um personagem (claim aprovado), as
  memberships do dono anterior são desativadas.
- Ao um membro **desbloquear** um core, geram-se **sugestões** de próximos
  bosses para o time (criaturas que nenhum membro ativo tem, por dificuldade).
- **Level mínimo** (opcional): ao entrar, o backend consulta o level do
  personagem na TibiaData e recusa quem estiver abaixo do exigido. A busca
  pública filtra por `characterLevel` (times que aceitam aquele level).
- **Preço por vaga** (opcional): valor **informativo** em gold do jogo — não é
  transação processada pelo sistema (não confundir com o Stripe do plano).
- **Expulsar** (`kickMember`) e **encerrar** (`deleteTeam`) são ações **só do
  dono** (403 caso contrário). Encerrar é exclusão lógica (status `CLOSED`):
  some da busca, vira só leitura, mas membros continuam vendo o histórico.
- **Notificações**: pedido recebido (dono), pedido aceito/recusado
  (solicitante), expulso, time encerrado. O frontend faz **polling leve**
  (~30s) do contador de não-lidas para o badge do sino — não usa WebSocket
  (o volume não justifica tempo real; decisão consciente).

> **Pendente (item 3 do CLAUDE.md — não implementado nesta sessão):** vocação
> obrigatória por vaga (Modo B). É a mudança mais complexa (nova entidade
> `TeamSlot` com vocações permitidas, `ListMembership` referenciando a vaga, e
> refatoração do fluxo de join para escolher uma vaga compatível). Foi deixada
> por último conforme a prioridade do prompt; ver o relatório da sessão.

## Endpoints atuais

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | Registro email+senha (rate limited) |
| POST | `/api/auth/login` | Login (rate limited) |
| POST | `/api/auth/anonymous` | Cria conta anônima + tokens |
| POST | `/api/auth/refresh` | Rotaciona refresh token |
| POST | `/api/auth/logout` | Revoga refresh token |
| GET | `/oauth2/authorization/{google\|discord}` | Início do login social |
| POST | `/api/claims` · GET `/api/claims` · GET `/api/claims/{id}` | Claims do usuário (verificação só automática) |
| GET | `/api/lists/search` | **Público** — busca (world, creatureId, hasOpenSlots, characterLevel) |
| GET | `/api/lists/{id}` | **Público** — detalhe do time |
| POST | `/api/lists` · GET `/api/lists/mine` | Criar / meus times |
| POST | `/api/lists/{shareCode}/join` | Pedir entrada com um personagem |
| POST | `/api/lists/{id}/leave` · `/api/lists/{id}/renew` | Sair / renovar (arquivado) |
| DELETE | `/api/lists/{id}` | Encerrar o time (só dono → 403) |
| DELETE | `/api/lists/{id}/members/{mid}` | Expulsar membro (só dono → 403) |
| GET | `/api/lists/{id}/requests` | Pedidos pendentes (só dono) |
| POST | `/api/lists/{id}/requests/{mid}/approve\|reject` | Aceitar/recusar pedido (só dono) |
| GET/POST | `/api/lists/{id}/soulcores[...]/obtain\|unlock` | Board e ações de soul core |
| GET | `/api/lists/{id}/suggestions` · POST `/api/suggestions/{id}/dismiss` | Sugestões |
| GET/POST | `/api/lists/{id}/chat` | Histórico e envio de mensagem |
| GET | `/api/notifications` · `/unread-count` | Notificações + contador de não-lidas |
| POST | `/api/notifications/{id}/read` · `/read-all` | Marcar lida(s) |
| WS | `/ws` → `/topic/lists/{id}/chat` | Chat em tempo real (STOMP, JWT no CONNECT) |
| GET | `/api/worlds` · `/api/creatures` | **Público** — catálogos para filtros |
| GET | `/api/characters/mine` · `/api/characters/{id}/soulcores` | Personagens e cores |
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

## Deploy

- **Backend + Postgres → Railway** (`backend/railway.json`): build via
  `backend/Dockerfile`, healthcheck em `/actuator/health`. Configurar as envs
  do `.env.example`; `DATABASE_URL` vem do plugin Postgres do Railway
  (atenção: Railway fornece `postgres://...` — converter para
  `jdbc:postgresql://...`).
- **Frontend → Netlify** (`frontend/netlify.toml`): build `npm run build`,
  publica `dist/`, redirect SPA já configurado. Definir `VITE_API_URL`
  (URL do backend no Railway) nas variáveis do site.
- **CORS e WebSocket** em produção: `FRONTEND_URL` = domínio do Netlify
  (https) — vale tanto para o CORS REST quanto para a origem do handshake
  `/ws`. `OAUTH2_REDIRECT_URL` = `<FRONTEND_URL>/oauth/callback`.
- Callbacks OAuth em produção: registrar
  `https://<backend>/login/oauth2/code/{google|discord}` nos portais dos
  providers.
- **Escala**: o scheduler de claims, o rate limit e o broker STOMP são
  single-instance (documentado em `docs/proxima-sessao.md`). Rodar 1 réplica
  do backend, ou adotar ShedLock/Bucket4j distribuído/broker externo.
