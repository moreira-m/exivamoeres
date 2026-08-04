import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router'
import { render, screen, within } from '@testing-library/react'
import { useAuthStore } from '../store/authStore'
import type { CharacterSummaryResponse, ListDetailResponse, UserResponse } from '../types/api'

/**
 * Renderiza um pedaço do app com o mínimo que ele precisa para existir: React
 * Query, rota e i18n (inicializado em `setup.ts`).
 *
 * `retry: false` é o ponto mais importante daqui: com a política padrão, um
 * teste de "a tela mostra o erro" ficaria segundos tentando de novo antes de
 * falhar por timeout — e o motivo verdadeiro (a mensagem não aparece) ficaria
 * escondido atrás de um timeout genérico.
 */
export function renderizar(ui: ReactNode, { rota = '/' }: { rota?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[rota]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

/**
 * Espelha a URL do roteador num elemento, para os testes afirmarem sobre ela.
 *
 * Necessário porque o `renderizar` usa `MemoryRouter`: a navegação acontece na memória e
 * **não** toca em `window.location` — afirmar sobre `window.location.search` daria sempre
 * vazio, que é um teste que nunca falha pelo motivo certo.
 */
export function SondaDaUrl() {
  const location = useLocation()
  return <output data-testid="url">{location.pathname + location.search}</output>
}

/** A URL atual do roteador (precisa da {@link SondaDaUrl} renderizada junto). */
export function urlAtual(): string {
  return screen.getByTestId('url').textContent ?? ''
}

/**
 * O conteúdo da página, sem a barra de navegação.
 *
 * Sem isto, `getByRole('button', { name: /criar time/i })` acha **três** botões:
 * o do formulário e os dois da NavBar (desktop e menu do celular). Consultar
 * dentro do `<main>` é o mesmo recorte que a suíte de `e2e/` usa.
 */
export function conteudo() {
  return within(screen.getByRole('main'))
}

/** Coloca uma sessão no store, como se a pessoa tivesse acabado de entrar. */
export function logarComo(user: Partial<UserResponse> = {}) {
  const sessao: UserResponse = {
    id: 1,
    displayName: 'Jogador de Teste',
    email: 'teste@exemplo.com',
    authProvider: 'LOCAL',
    anonymous: false,
    plan: 'FREE',
    ...user,
  }
  useAuthStore.setState({ accessToken: 'token-de-teste', refreshToken: 'refresh', user: sessao })
  return sessao
}

/** Personagem verificado. O `world` é o que decide elegibilidade nas telas. */
export function personagem(
  campos: Partial<CharacterSummaryResponse> & { name: string; world: string },
): CharacterSummaryResponse {
  return { id: 1, vocation: 'Elder Druid', level: 500, ...campos }
}

/**
 * Detalhe de time no formato que o backend devolve. Só os campos que as telas
 * leem precisam de valor "de verdade"; o resto é preenchimento — e existir é o
 * que importa, porque campo faltando na fábrica esconde `undefined` no teste.
 */
/**
 * Uma página do backend, como o Spring a devolve (item P12).
 *
 * ⚠️ `totalElements` **separado do tamanho da lista**, de propósito: é assim que se testa o
 * contador de uma aba que já tem mais de uma página. Passar só a lista faria todo teste
 * assumir "uma página só", que é justamente o caso que a paginação existe para não assumir.
 */
export function pagina<T>(content: T[], totalElements = content.length, number = 0) {
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(content.length, 1)))
  return { content, totalElements, totalPages, number }
}

export function detalheDeTime(
  campos: Partial<ListDetailResponse['summary']> = {},
  extras: Partial<Omit<ListDetailResponse, 'summary'>> = {},
): ListDetailResponse {
  return {
    summary: {
      id: 7,
      name: 'Time de Teste',
      world: 'Antica',
      shareCode: 'CODIGO7',
      targetCreatureId: 1,
      targetCreatureName: 'Demon',
      targetCreatureImageUrl: null,
      joinPolicy: 'MANUAL_APPROVAL',
      status: 'ACTIVE',
      expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString(),
      minimumLevel: null,
      pricePerSlot: null,
      description: null,
      huntSchedule: null,
      featured: false,
      memberCount: 1,
      maxMembers: 5,
      hasOpenSlots: true,
      slots: [],
      createdAt: new Date().toISOString(),
      ...campos,
    },
    ownerId: 99,
    contact: null,
    members: [],
    ...extras,
  }
}
