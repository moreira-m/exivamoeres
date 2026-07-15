# Configurações e integrações externas

Tudo que depende de contas, painéis e segredos de terceiros — a ser feito
**depois** que o código estiver pronto. Nada aqui deve ser commitado: são
valores que vão em variáveis de ambiente (Railway / Netlify) ou no `.env`
local (que é git-ignored).

Ordem sugerida: **1 → 2 → 3/4 → 6 → 7 → 5** (Stripe por último, junto da
Fase 2 do código).

---

## 1. Segredos internos (gerar você mesmo)

| Item | Como gerar | Vai em |
|---|---|---|
| `JWT_SECRET` | `openssl rand -base64 64` | Backend (Railway) e `.env` local |
| `POSTGRES_PASSWORD` | senha forte qualquer | Banco + backend |

> O `JWT_SECRET` precisa ter no mínimo 64 caracteres (HMAC-SHA512). Se trocar
> o segredo em produção, todos os tokens ativos são invalidados (todo mundo
> precisa logar de novo) — o que é o comportamento esperado.

---

## 2. Banco de dados PostgreSQL

- **Local:** já resolvido pelo `docker-compose.yml` (nada a fazer além do
  `.env`).
- **Produção (Railway):** adicionar o plugin **PostgreSQL** ao projeto. O
  Railway injeta as credenciais automaticamente, mas no **formato dele**
  (`postgres://user:senha@host:porta/db`), enquanto o backend espera o
  formato JDBC. Você precisa montar:
  - `DATABASE_URL` = `jdbc:postgresql://<host>:<porta>/<database>`
  - `POSTGRES_USER` = usuário do Railway
  - `POSTGRES_PASSWORD` = senha do Railway

> As migrations (Flyway V1…V7) rodam sozinhas na primeira subida do backend —
> não precisa criar tabela manualmente.

---

## 3. Login social — Google

Painel: <https://console.cloud.google.com/apis/credentials>

1. Criar um projeto (ou usar um existente).
2. Configurar a **tela de consentimento OAuth** (OAuth consent screen).
3. Criar credencial do tipo **OAuth client ID → Web application**.
4. Em **Authorized redirect URIs**, adicionar:
   - Local: `http://localhost:8080/login/oauth2/code/google`
   - Produção: `https://<seu-backend>/login/oauth2/code/google`
5. Copiar **Client ID** e **Client Secret** para:
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`

Escopos usados pelo backend: `openid`, `profile`, `email`.

---

## 4. Login social — Discord

Painel: <https://discord.com/developers/applications>

1. **New Application**.
2. Aba **OAuth2** → copiar **Client ID** e **Client Secret**.
3. Em **OAuth2 → Redirects**, adicionar:
   - Local: `http://localhost:8080/login/oauth2/code/discord`
   - Produção: `https://<seu-backend>/login/oauth2/code/discord`
4. Preencher:
   - `DISCORD_CLIENT_ID`
   - `DISCORD_CLIENT_SECRET`

Escopos usados pelo backend: `identify`, `email`.

> O Discord pode não retornar email (conta sem email verificado). O sistema
> já lida com isso: cria a conta sem email nesse caso.

---

## 5. Pagamento — Stripe (Fase 2, quando o código do Stripe existir)

> Ainda **não** há código de Stripe. Esta seção é o guia para quando a Fase 2
> for implementada. A tabela `subscriptions` e o campo `users.plan` já existem
> no banco esperando essa integração.

Painel: <https://dashboard.stripe.com/> (usar **modo de teste** primeiro)

1. **Produto e preço:** criar um Produto "Premium" com um **Preço recorrente
   mensal**. Copiar o **Price ID** (`price_...`) → `STRIPE_PREMIUM_PRICE_ID`.
2. **Chave secreta:** Developers → API keys → **Secret key**
   (`sk_test_...` em teste) → `STRIPE_SECRET_KEY`.
   - ⚠️ Nunca usar a chave de produção (`sk_live_...`) em ambiente de teste,
     e nunca commitar nenhuma das duas.
3. **Webhook:** Developers → Webhooks → **Add endpoint**:
   - URL: `https://<seu-backend>/api/billing/webhook`
   - Eventos: `checkout.session.completed`,
     `customer.subscription.updated`, `customer.subscription.deleted`.
   - Copiar o **Signing secret** (`whsec_...`) → `STRIPE_WEBHOOK_SECRET`.
4. **Customer Portal:** Settings → Billing → **Customer portal** → ativar
   (é a tela onde o assinante cancela/troca cartão; não precisamos construí-la).

> Para testar o webhook localmente, usar a Stripe CLI:
> `stripe listen --forward-to localhost:8080/api/billing/webhook`
> (ela imprime um `whsec_` temporário para o `.env` local).

---

## 6. Deploy do backend — Railway

Painel: <https://railway.app/>

1. Novo projeto → **Deploy from GitHub repo** (ou CLI), apontando para a
   pasta `backend/` (o `backend/railway.json` já configura build via
   Dockerfile e healthcheck em `/actuator/health`).
2. Adicionar o plugin **PostgreSQL** (ver seção 2).
3. Preencher **todas** as variáveis de ambiente da tabela abaixo (seção 8).
4. Após o primeiro deploy, anotar a **URL pública do backend** — ela é usada
   em vários lugares (redirects OAuth, `VITE_API_URL`, webhook do Stripe).

---

## 7. Deploy do frontend — Netlify

Painel: <https://app.netlify.com/>

1. Novo site → conectar o repositório. O `frontend/netlify.toml` já define
   base `frontend`, build `npm run build`, publish `dist` e o redirect de SPA.
2. Em **Site settings → Environment variables**, definir:
   - `VITE_API_URL` = URL pública do backend no Railway (sem barra no final).
3. Anotar a **URL pública do Netlify** — ela é o `FRONTEND_URL` do backend.

> Variável do Vite é embutida no build: **rebuild** o site sempre que trocar
> `VITE_API_URL`.

---

## 8. Tabela completa de variáveis de ambiente (backend)

Preencher no Railway (produção) e no `.env` local (dev). Ver `.env.example`
para o template.

### Obrigatórias

| Variável | Descrição | Origem |
|---|---|---|
| `DATABASE_URL` | JDBC do Postgres (`jdbc:postgresql://…`) | Seção 2 |
| `POSTGRES_USER` | usuário do banco | Seção 2 |
| `POSTGRES_PASSWORD` | senha do banco | Seção 1/2 |
| `JWT_SECRET` | segredo HMAC ≥ 64 chars | Seção 1 |
| `FRONTEND_URL` | origem do frontend (CORS **e** origem do WebSocket) | Seção 7 |
| `OAUTH2_REDIRECT_URL` | `<FRONTEND_URL>/oauth/callback` | Seção 7 |

### Login social (necessárias só se usar OAuth)

| Variável | Origem |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Seção 3 |
| `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` | Seção 4 |

### Stripe (Fase 2)

| Variável | Origem |
|---|---|
| `STRIPE_SECRET_KEY` | Seção 5 |
| `STRIPE_WEBHOOK_SECRET` | Seção 5 |
| `STRIPE_PREMIUM_PRICE_ID` | Seção 5 |

### Opcionais (têm default no código; só sobrescrever se quiser mudar a regra)

| Variável | Default | O que controla |
|---|---|---|
| `JWT_ACCESS_TOKEN_MINUTES` | 30 | validade do access token |
| `JWT_REFRESH_TOKEN_DAYS` | 14 | validade do refresh token |
| `TIBIADATA_BASE_URL` | `https://api.tibiadata.com` | API do Tibia (trocar só se usar mirror) |
| `CLAIM_POLL_INTERVAL` | 15m | frequência da verificação de personagem |
| `CHARACTER_ELIGIBILITY_CACHE_TTL` | 1h | cache de world/Free-Premium por personagem |
| `WORLDS_CACHE_TTL` | 24h | cache da lista de worlds |
| `CHAT_MESSAGES_PER_MINUTE` | 20 | rate limit do chat por usuário |
| `TEAM_FREE_ACTIVE_LIMIT` | 3 | máx. de times ativos no plano free |
| `TEAM_FREE_DURATION_DAYS` | 7 | prazo de vida do time (free) |
| `TEAM_PREMIUM_DURATION_DAYS` | 30 | prazo de vida do time (premium) |
| `TEAM_EXPIRATION_CHECK_INTERVAL` | 1h | frequência do job que arquiva times expirados |
| `SPRING_PROFILES_ACTIVE` | dev | perfil do Spring |

### Frontend (Netlify)

| Variável | Descrição |
|---|---|
| `VITE_API_URL` | URL pública do backend (sem barra final) |

---

## 9. Checklist final de produção

- [ ] Postgres provisionado e `DATABASE_URL` no formato JDBC.
- [ ] `JWT_SECRET` forte e único (diferente do de dev).
- [ ] Redirects OAuth de **produção** cadastrados no Google e no Discord.
- [ ] `FRONTEND_URL` = domínio real do Netlify (https) — nunca `*`.
- [ ] `OAUTH2_REDIRECT_URL` = `<FRONTEND_URL>/oauth/callback`.
- [ ] `VITE_API_URL` no Netlify = URL do backend, com **rebuild** após definir.
- [ ] (Fase 2) Webhook do Stripe cadastrado e `STRIPE_WEBHOOK_SECRET` correto.
- [ ] (Fase 2) Testado tudo no **modo de teste** do Stripe antes de virar a
      chave de produção.
- [ ] Confirmar que o backend sobe rodando **1 réplica** (scheduler, rate
      limit e chat são single-instance — ver `docs/proxima-sessao.md`).
