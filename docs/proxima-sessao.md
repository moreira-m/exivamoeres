# Guia para a próxima sessão de desenvolvimento

Este documento é o handoff da sessão 1 (fundação) para a sessão 2
(funcionalidades). Leia junto com o `README.md`.

## O que está PRONTO (não refazer)

1. **Monorepo**: `backend/` (Spring Boot 3.5 / Java 21 / Maven),
   `frontend/` (Vite + React 18 + TS), `docker-compose.yml`,
   `.env.example` na raiz e no frontend.
2. **Modelo de dados completo**: todas as 10 entidades do domínio + Flyway
   V1–V4 + repositórios. As tabelas das features futuras (listas, soulcores,
   sugestões, chat) já existem com índices e constraints — **não criar
   migrations que refaçam essas tabelas**; alterações vão em V5+.
3. **Autenticação completa**:
   - email/senha (BCrypt) com registro/login rate-limited (Bucket4j);
   - JWT de acesso (30 min) + refresh token opaco rotacionado em banco;
   - OAuth2 Google e Discord (`security/oauth/`) — para adicionar provider,
     implemente `OAuthUserProfileExtractor` e registre no `application.yml`;
   - contas anônimas (`POST /api/auth/anonymous`).
4. **CharacterClaim de ponta a ponta** (a parte mais sensível do sistema):
   criação, polling de 15 min, verify-now, expiração em 24h, aprovação
   atômica com transferência de posse e desativação de memberships antigas.
   Coberto por testes de integração reais (Testcontainers + WireMock).

## Contratos a RESPEITAR

- **Interfaces esqueleto prontas** (implemente-as, não as substitua):
  `HuntingListService`, `SoulcoreService`, `SuggestionService`,
  `ChatService`. Os métodos retornam `Object` como placeholder — troque por
  DTOs reais (siga o padrão de `dto/claim/ClaimResponse`, records com
  factory `from(...)`).
- **Regra de matching do comment** vive SÓ em `CommentCodeMatcher`
  (trim + lowercase + contains). Nunca duplicar nem "endurecer" para
  igualdade exata.
- **Transições de estado do claim** só via `ClaimTransitionService`
  (transacional). Falha de rede nunca atualiza `last_checked_at` nem expira
  claim — expiração é sobre `created_at`.
- **`TibiaDataClient` é a única porta para a TibiaData.** Novos usos da API
  (ex.: importar Bestiary, validar world) entram como novos métodos na
  interface + impl com `@Retry`/`@CircuitBreaker` name `tibiadata`.
- Entidades JPA nunca saem na API; DTOs sempre com Bean Validation.
- Segredos só via env var; CORS restrito a `FRONTEND_URL`.

## O que FALTA implementar (sessão 2)

### Backend
1. **HuntingListService**: criar lista (share_code no estilo do
   `VerificationCodeGenerator`), entrar por share_code (personagem precisa
   ter dono = usuário e world igual ao da lista; reativar membership inativa
   em vez de duplicar), sair (active=false), listar/detalhar.
2. **SoulcoreService**: marcar OBTAINED (só membro ativo), OBTAINED →
   UNLOCKED gravando `character_soulcores` na mesma transação, board da
   lista, cores do personagem.
3. **SuggestionService**: sugerir criaturas que nenhum membro ativo
   desbloqueou (cruzar `list_memberships` × `character_soulcores`),
   priorizando menor difficulty; dismiss.
4. **Chat**: WebSocket/STOMP — plano detalhado em `config/WebSocketConfig`
   (hoje é placeholder). Adicionar `spring-boot-starter-websocket`.
   Histórico paginado já tem índice (`ix_chat_messages_list_sent`).
5. **Catálogo completo de criaturas**: importar o Bestiary inteiro
   (TibiaData `/v4/creatures` + boss/difficulty) numa migration V5 ou job de
   sincronização. O seed atual (V3) tem só 12 criaturas de exemplo.
6. **Promoção de conta anônima** para registrada/OAuth mantendo o mesmo
   `user_id` (assinatura sugerida: novo método no `AuthService`).
7. **Refresh automático no frontend** (TODO marcado em
   `frontend/src/services/apiClient.ts`).
8. Controllers + DTOs das features acima; endpoints REST seguem o padrão
   `/api/lists`, `/api/lists/{id}/soulcores`, etc.

### Frontend (nenhuma tela existe ainda)
- Páginas: login/registro/anônimo, callback OAuth (`/oauth/callback` — os
  tokens chegam no **fragmento** da URL: `#access_token=...&refresh_token=...`),
  meus personagens/claims (exibir o código de verificação + botão
  verify-now), listas (board de soulcores), chat.
- A base já existe: `apiClient` com Bearer automático, `authStore`
  (Zustand persist), hooks React Query (`useClaims`), tipos em
  `types/api.ts` (manter espelhados com os DTOs Java).

### Deploy
- Railway (backend+Postgres) e Netlify (frontend) — checklist no fim do
  `README.md`. Nada foi provisionado.

## Armadilhas conhecidas / dívidas conscientes

- **Scheduler single-instance**: `@Scheduled` sem lock distribuído. Com mais
  de uma réplica do backend, o job rodaria duplicado (inofensivo para
  aprovação — idempotente — mas desperdiça chamadas à TibiaData). Se
  escalar: ShedLock ou Quartz clusterizado.
- **Rate limiting em memória** (`RateLimitFilter`): por instância. Multi
  instância exige backend distribuído do Bucket4j.
- **`verify-now` sem rate limit próprio**: um usuário pode martelar o
  endpoint e gastar chamadas à TibiaData. Considerar cooldown (ex.: 1/min
  por claim) na sessão 2.
- **Claims concorrentes**: dois usuários podem ter claims PENDING para o
  mesmo personagem; quem tiver o código no comment primeiro leva. É o
  comportamento desejado (o dono real controla o comment), mas o claim
  "perdedor" continua PENDING até expirar — aceitável.
- **Lombok/JDK**: `lombok.version` fixado em 1.18.42 no pom para compilar
  com JDKs > 24. `testcontainers.version` fixado em 1.21.4 por causa do
  Docker Engine 28.4+/29.
- **JPA `ddl-auto: validate`**: qualquer mudança de entidade exige migration
  correspondente, senão o boot falha (proposital).
- O pacote `dto/` usa records; `domain/` usa Lombok `@Getter/@Setter` — não
  usar `@Data` em entidade (equals/hashCode de entidade JPA é armadilha).

## Como validar que nada quebrou

```bash
cd backend && mvn test   # 23 testes; integração exige Docker rodando
cd frontend && npm run build
```

Os testes de integração do claim (`ClaimVerificationIntegrationTest`) são a
rede de segurança do fluxo crítico: espaços extras, quebras de linha, case
diferente, comment vazio, aprovação atômica, expiração e retry em falha de
rede. Se mexer no fluxo de claim, rode-os sempre.
