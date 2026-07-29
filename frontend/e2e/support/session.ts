import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'

export const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8080'

/** O que o frontend guarda em `localStorage` para ficar logado. */
export interface Session {
  accessToken: string
  refreshToken: string
  user: unknown
}

// Sessão reaproveitada entre execuções. Existe por um motivo concreto: criar
// conta anônima é limitado a 3/hora por IP (item S2), e um suíte que só roda
// três vezes por hora é um suíte que ninguém roda.
const CACHE = join(dirname(new URL(import.meta.url).pathname), '..', '.auth', 'session.json')

/**
 * Garante uma sessão utilizável, na ordem do mais barato para o mais caro:
 * reaproveitar a do cache → renovar com o refresh token → criar conta anônima.
 */
export async function ensureSession(): Promise<Session> {
  const cached = readCache()

  if (cached && (await stillValid(cached.accessToken))) return cached

  if (cached) {
    const renovada = await refresh(cached.refreshToken)
    if (renovada) return writeCache(renovada)
  }

  return writeCache(await createAnonymous())
}

/** Cabeçalhos de uma requisição autenticada — para os testes falarem com a API. */
export function authHeaders(session: Session): Record<string, string> {
  return { Authorization: `Bearer ${session.accessToken}`, 'Content-Type': 'application/json' }
}

function readCache(): Session | null {
  try {
    return JSON.parse(readFileSync(CACHE, 'utf8')) as Session
  } catch {
    return null // primeira execução, ou cache inválido: dá no mesmo
  }
}

function writeCache(session: Session): Session {
  mkdirSync(dirname(CACHE), { recursive: true })
  writeFileSync(CACHE, JSON.stringify(session, null, 2))
  return session
}

/** Qualquer endpoint autenticado serve de teste de vida do access token. */
async function stillValid(accessToken: string): Promise<boolean> {
  const response = await fetch(`${API_BASE}/api/characters/mine`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  return response.ok
}

async function refresh(refreshToken: string): Promise<Session | null> {
  const response = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  return response.ok ? ((await response.json()) as Session) : null
}

async function createAnonymous(): Promise<Session> {
  const response = await fetch(`${API_BASE}/api/auth/anonymous`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName: 'E2E' }),
  })
  if (!response.ok) {
    throw new Error(
      `Não consegui criar a conta anônima do teste (HTTP ${response.status}).\n` +
        (response.status === 429
          ? 'É o rate limit de 3 contas anônimas por hora. Suba o backend com ' +
            'RATE_LIMIT_ANONYMOUS_PER_HOUR mais alto, ou espere a janela virar — ' +
            'a sessão fica em cache em e2e/.auth/session.json e vale 14 dias.'
          : `O backend em ${API_BASE} respondeu, mas recusou. Confira o log dele.`),
    )
  }
  return (await response.json()) as Session
}
