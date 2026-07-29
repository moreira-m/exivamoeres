-- Dados mínimos para os testes de navegação (frontend/e2e) terem o que abrir.
--
-- Por que existe: cinco testes precisam de um time que exista de verdade — a
-- página de detalhe (`/teams/:id`) e os dois do ErrorBoundary, que corrompem a
-- resposta **real** da API. Num banco recém-criado (é o caso do CI, e de quem
-- acabou de rodar `docker compose down -v`) eles simplesmente **pulam**, e um
-- suíte que pula cinco testes sem avisar é pior que um que falha.
--
-- Por que INSERT direto e não pela API: criar time exige personagem verificado, e
-- verificação exige que o código apareça no comment do personagem no Tibia.com —
-- não existe caminho de API para isso num runner. Estes são dados de fixture, e o
-- que está sob teste aqui é a navegação, não o fluxo de claim (esse tem 7 testes
-- de integração no backend).
--
-- Rodar DEPOIS de o backend subir (o Flyway precisa ter criado as tabelas):
--   psql "$DATABASE_URL" -f ops/ci/seed-e2e.sql
--
-- É idempotente: rodar duas vezes não duplica nada.

BEGIN;

-- Dono do time. Sem senha e sem provider externo: nenhum teste faz login com ele
-- (o suíte usa uma conta anônima criada pela API), ele só precisa existir para o
-- time ter dono.
INSERT INTO users (email, display_name, auth_provider, plan)
VALUES ('e2e-owner@exemplo.local', 'Dono E2E', 'LOCAL', 'FREE')
ON CONFLICT DO NOTHING;

-- Personagem do dono. O world é um dos que o stub da TibiaData devolve
-- (ops/ci/tibiadata-stub.py), então ele aparece no filtro da home.
INSERT INTO characters (name, world, user_id, vocation, level)
SELECT 'E2E Sir Exiva', 'Antica', u.id, 'Elite Knight', 200
FROM users u
WHERE u.email = 'e2e-owner@exemplo.local'
ON CONFLICT DO NOTHING;

-- O time. ACTIVE e com prazo no futuro para aparecer na busca pública.
-- A criatura-alvo é a primeira do catálogo semeado — não importa qual, importa
-- que exista (o V3 semeia 12; o boot importa o resto quando há TibiaData).
INSERT INTO hunting_lists (
    name, world, share_code, owner_id, target_creature_id,
    join_policy, status, expires_at, minimum_level, hunt_schedule, description
)
SELECT
    'Time do E2E',
    'Antica',
    'e2e-share-code',
    u.id,
    (SELECT id FROM creatures ORDER BY id LIMIT 1),
    'AUTO_ACCEPT',
    'ACTIVE',
    now() + interval '30 days',
    50,
    'Seg-Sex 20h BRT',
    'Time de fixture do suíte de navegação. Se você está vendo isto em produção, algo deu muito errado.'
FROM users u
WHERE u.email = 'e2e-owner@exemplo.local'
  AND NOT EXISTS (SELECT 1 FROM hunting_lists WHERE share_code = 'e2e-share-code');

-- Composição por vocação: duas vagas, uma exigindo Knight e uma livre. Faz o
-- cartão de composição aparecer na tela do time — que é justamente o cartão que
-- o teste do ErrorBoundary derruba de propósito.
INSERT INTO team_slots (list_id, position, vocation)
SELECT l.id, 1, 'KNIGHT' FROM hunting_lists l WHERE l.share_code = 'e2e-share-code'
ON CONFLICT DO NOTHING;

INSERT INTO team_slots (list_id, position, vocation)
SELECT l.id, 2, NULL FROM hunting_lists l WHERE l.share_code = 'e2e-share-code'
ON CONFLICT DO NOTHING;

COMMIT;

-- Confirmação: o suíte precisa de pelo menos um time ACTIVE visível na busca.
SELECT count(*) AS times_ativos FROM hunting_lists WHERE status = 'ACTIVE';
