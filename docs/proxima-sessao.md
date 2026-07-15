# Handoff de desenvolvimento

Histórico das sessões e o que ainda falta. Leia junto com o `README.md`.

## Sessão 1 (fundação) — PRONTO

Monorepo, modelo de dados base (V1–V4), autenticação completa (JWT + refresh
token rotacionado + OAuth2 Google/Discord + contas anônimas) e o fluxo de
CharacterClaim (polling 15min, verify-now, expiração 24h, aprovação atômica).

## Sessão 2 (times, soul cores, chat, frontend) — PRONTO

### Backend
- **Migrations V5–V6** (aditivas, não editam V1–V4): V5 —
  `hunting_lists.target_creature_id` + `join_policy`; `list_memberships.status`
  + índice parcial de pendentes; `chat_messages.character_id`;
  `characters.vocation`; `creatures.race` + `image_url`. V6 — `difficulty`
  opcional (o catálogo completo é importado no boot).
- **Catálogo de criaturas**: `CreatureCatalogService` importa o Bestiary
  inteiro da TibiaData (`/v4/creatures`, ~719 criaturas com ícone) no boot,
  casando por `race` (best-effort; não derruba o boot se a API falhar).
- **Times** (`HuntingListService`): criar, buscar (público), entrar por
  share_code, aprovar/recusar pedidos, sair, listar/detalhar. Regras sempre no
  backend:
  - limite de 5 membros — lock pessimista (`findByIdForUpdate`) durante
    join/aprovação evita corrida de vagas;
  - mesmo world + bloqueio de Free Account — `TeamEligibilityService`, que
    consulta a TibiaData via `CachedCharacterLookup` (cache Caffeine, TTL 1h);
  - política de entrada `MANUAL_APPROVAL` / `AUTO_ACCEPT`.
- **Soul cores** (`SoulcoreService`): obtido/desbloqueado; unlock grava o
  Animus Mastery em `character_soulcores` na mesma transação e dispara
  `SuggestionService.generateSuggestions`.
- **Sugestões** (`SuggestionService`): criaturas que nenhum membro ativo tem,
  por dificuldade, exceto o alvo do próprio time; dismiss.
- **Chat** (`ChatService` + `WebSocketConfig` + `StompAuthChannelInterceptor`):
  STOMP em `/ws`, broadcast em `/topic/lists/{id}/chat`, JWT validado no frame
  CONNECT, rate limit por usuário (`ChatRateLimiter`), mensagem vinculada a
  personagem. Histórico paginado por REST.
- **Auxiliares**: `WorldService` (cache), `CreatureService`,
  `CreatureCatalogService` (sincroniza ícones no boot, best-effort),
  `CharacterService`, `TeamMembershipGuard` (autorização "membro ativo").
- **`CharacterSyncService`** extraído (sync name/world/vocation) e reusado por
  claim e por elegibilidade — antes era um método privado no claim service.

### Frontend
- Área pública (busca `/`, detalhe `/teams/:id`) e área logada
  (`/account/*` com `ProtectedRoute`): personagens/claim, meus times, criar
  time. Callback OAuth em `/oauth/callback` (lê tokens do fragmento).
- Clients HTTP por domínio (`listsApi`, `worldsApi`, `creaturesApi`,
  `charactersApi`, `chatApi`), socket STOMP (`chatSocket`), hooks React Query,
  store Zustand persistido, `apiClient` com **refresh automático** no 401.
- Design system Tailwind "retro" (`components/ui`), inspirado visualmente na
  pasta `referencia/` (reimplementado do zero).

### Deploy
- `backend/railway.json`, `frontend/netlify.toml`, `.env.example` atualizado.

### Testes (35, todos verdes — `mvn test`, exige Docker)
Claim (sessão 1) + times (política auto/manual, world, Free Account, limite de
5, aprovar/recusar), soul core + geração de sugestão, chat (serviço +
handshake STOMP com/sem JWT).

## O que FALTA (próximas sessões)

1. **Dificuldade das criaturas**: a TibiaData não expõe as estrelas do
   Bestiary, então só os 12 seeds têm `difficulty`; as ~707 importadas ficam
   nulas. Se quiser priorizar sugestões por dificuldade em todo o catálogo,
   buscar essa info de outra fonte (ex.: scraping do Tibia.com) — hoje as
   sugestões só ordenam bem entre os seeds.
2. **Seletor de criatura com busca**: o dropdown tem ~719 opções (`<select>`
   nativo com type-ahead do browser). Trocar por um combobox com filtro
   melhora a UX.
3. **Promoção de conta anônima** para registrada/OAuth mantendo o `user_id`.
3. **Transferir/excluir time**: hoje o dono não pode sair; falta transferir a
   posse ou arquivar o time.
4. **Paginação/scroll infinito** no chat e na busca (o backend já pagina).
5. **Notificações** (pedido aceito, novo membro, core obtido).
6. **Perfil público de personagem** com os cores desblobqueados (endpoint
   `/api/characters/{id}/soulcores` já existe; falta a tela).
7. **Testes de frontend** (nenhum ainda) e E2E.

## Armadilhas / dívidas conscientes

- **Single-instance**: scheduler de claims (`@Scheduled`), rate limits
  (`RateLimitFilter`, `ChatRateLimiter`) e o broker STOMP simples são em
  memória. Para escalar horizontalmente: ShedLock/Quartz, Bucket4j
  distribuído e um broker externo (RabbitMQ). Rodar **1 réplica** por ora.
- **Cache de elegibilidade**: TTL de 1h (`CHARACTER_ELIGIBILITY_CACHE_TTL`).
  Um char que virou Premium pode ficar até 1h bloqueado — trade-off conhecido.
- **Busca com `hasOpenSlots`**: o filtro de vaga é aplicado em memória sobre a
  página (contar membros por time exigiria subquery). Como o time tem no
  máximo 5 membros e a página é pequena, o custo é desprezível; se a busca
  crescer muito, mover a contagem para a query.
- **`race` da criatura**: o ícone só resolve se o slug bater com o da
  TibiaData. Os 12 seeds foram mapeados à mão na V5; ao importar o Bestiary,
  garantir o slug correto.
- **JPA `ddl-auto: validate`**: qualquer mudança de entidade exige migration
  correspondente (proposital).
- **Chat sem SockJS**: o cliente usa WebSocket puro (`ws://`/`wss://`). Se
  precisar de fallback para redes que bloqueiam WS, habilitar SockJS nos dois
  lados.

## Validar que nada quebrou

```bash
cd backend && mvn test        # 35 testes; integração exige Docker
cd frontend && npm run build  # typecheck + build
```
