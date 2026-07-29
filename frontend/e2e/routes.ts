/**
 * Inventário das páginas do site — **fonte única** para os testes de navegação.
 *
 * Rota nova entra aqui e ganha teste automaticamente. É de propósito: o custo de
 * esquecer é justamente o bug que estes testes existem para pegar (página que
 * ninguém abre desde que foi escrita).
 *
 * Mantenha em pé de igualdade com `src/App.tsx` — o teste
 * `routes.spec.ts` compara os dois e reprova se divergirem.
 */

export interface AppRoute {
  /** URL a visitar (já com parâmetros resolvidos, quando houver). */
  path: string
  /** Nome legível, usado no título do teste. */
  name: string
  /** `logged` visita com sessão; `public` visita sem nenhuma. */
  access: 'public' | 'logged'
  /**
   * Um pedaço de texto que **só esta página** mostra, em inglês (o idioma é
   * fixado nos testes). É o que separa "a página renderizou" de "a página
   * renderizou o cabeçalho e mais nada".
   */
  mustShow: RegExp
}

export const PUBLIC_ROUTES: AppRoute[] = [
  { path: '/', name: 'home (busca)', access: 'public', mustShow: /Find your Soul Core team/i },
  { path: '/login', name: 'login', access: 'public', mustShow: /Continue as guest/i },
]

export const LOGGED_ROUTES: AppRoute[] = [
  { path: '/account/teams', name: 'meus times', access: 'logged', mustShow: /My teams/i },
  {
    // `?tab=requests` é o destino do aviso de "pedido em risco" (P18) e depende do
    // `useSearchParams` do roteador — que é API de roteador, logo assunto de major.
    path: '/account/teams?tab=requests',
    name: 'meus times · aba de pedidos',
    access: 'logged',
    mustShow: /You have no join requests|Requested on/i,
  },
  { path: '/account/teams/new', name: 'criar time', access: 'logged', mustShow: /Create team/i },
  { path: '/account/characters', name: 'personagens', access: 'logged', mustShow: /My characters/i },
  { path: '/account/notifications', name: 'notificações', access: 'logged', mustShow: /Notifications/i },
  { path: '/account/billing', name: 'assinatura', access: 'logged', mustShow: /Your plan/i },
]

export const ALL_ROUTES = [...PUBLIC_ROUTES, ...LOGGED_ROUTES]

/**
 * Rotas que existem no `App.tsx` mas não entram na varredura, com o motivo.
 * Ficam listadas para o teste de paridade não acusar falta — e para ninguém
 * "esquecer" uma rota só omitindo-a.
 */
export const ROUTES_OUT_OF_SCOPE: Record<string, string> = {
  '/oauth/callback': 'não é página, é redirecionador — coberto em oauth-callback.spec.ts',
  '/teams/:id': 'depende de um time existente — coberto em detalhe-do-time.spec.ts',
}
