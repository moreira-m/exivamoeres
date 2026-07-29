import { Component, type ErrorInfo, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from './ui/Button'
import { Card } from './ui/Card'

/**
 * Última linha de defesa contra **tela branca**.
 *
 * Um erro lançado durante o render derruba a árvore inteira do React: sem
 * boundary, o `<div id="root">` fica vazio — sem cabeçalho, sem mensagem, sem
 * caminho de volta, e com **HTTP 200** para qualquer monitoramento. Foi assim que
 * um `team.slots.length` num cartão secundário apagou a página do time inteira
 * quando a API respondeu sem o campo.
 *
 * ⚠️ **Não confunda com o [`QueryError`](./ui/QueryError.tsx).** Requisição que
 * falha é *estado* do React Query e **nunca** chega aqui; boundary pega erro de
 * *render*. Os dois são necessários e não se substituem.
 *
 * Só uma classe pode ser boundary (é o único lugar onde `componentDidCatch`
 * existe) — daí o componente de classe num projeto que é todo função.
 *
 * **Dois usos, um componente:**
 *
 * | Onde | Fallback |
 * |---|---|
 * | `App.tsx`, em volta de tudo | tela cheia com "recarregar" e "ir para a home" |
 * | Em volta de um bloco secundário (`section`) | um aviso do tamanho do bloco, e **o resto da página continua** |
 *
 * O segundo existe porque o primeiro, sozinho, troca a **página inteira** pelo aviso
 * quando o que quebrou foi um cartão secundário — melhor que tela branca, pior que
 * possível. Ver NEXT_STEPS T9.
 */
interface Props {
  children: ReactNode
  /**
   * Aviso do tamanho do bloco, para boundary de seção. Sem isto, o fallback é a tela
   * cheia — que é o certo quando o boundary envolve a aplicação.
   */
  section?: string
}

interface State {
  crashed: boolean
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { crashed: false }

  static getDerivedStateFromError(): State {
    return { crashed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Não há serviço de erro no projeto (é o T7 do NEXT_STEPS): o console é o
    // único destino, e `componentStack` é o que diz **qual tela** quebrou —
    // a mensagem sozinha ("undefined is not an object") não localiza nada.
    // O `section` entra na linha para o log dizer *qual bloco* caiu sem precisar
    // decifrar o stack.
    console.error(
      `[ErrorBoundary]${this.props.section ? ` seção=${this.props.section}` : ''} erro de render:`,
      error,
      info.componentStack,
    )
  }

  render() {
    if (!this.state.crashed) {
      return this.props.children
    }
    return this.props.section ? <SectionFailure section={this.props.section} /> : <CrashScreen />
  }
}

/**
 * Falha de um bloco secundário: o aviso ocupa o lugar **do bloco**, e a página segue
 * utilizável. Sem botão de propósito — o boundary de cima já oferece "recarregar", e
 * um botão por seção transformaria a tela num painel de erros.
 */
function SectionFailure({ section }: { section: string }) {
  const { t } = useTranslation()
  return (
    <Card className="mb-6 p-4">
      <p className="font-bold text-accent">{t('errors.sectionFailed', { section })}</p>
      <p className="mt-1 text-sm font-bold text-ink/60">{t('errors.sectionFailedHelp')}</p>
    </Card>
  )
}

/**
 * A tela de falha. Componente de função separado só para poder usar i18n (a
 * classe não tem hooks).
 *
 * As duas ações são **navegação de verdade** (`location`), não do roteador, de
 * propósito: depois de um erro de render a árvore está num estado desconhecido, e
 * remontá-la costuma estourar de novo no mesmo lugar. Recarregar sempre funciona;
 * um `<Link>` daqui deixaria o usuário preso nesta tela.
 */
function CrashScreen() {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="max-w-lg p-6 text-center">
        <h1 className="mb-2 text-2xl text-ink">{t('errors.crashTitle')}</h1>
        <p className="mb-5 font-bold text-ink/70">{t('errors.crashHelp')}</p>
        <div className="flex flex-wrap justify-center gap-2">
          <Button onClick={() => window.location.reload()}>{t('errors.reload')}</Button>
          <a href="/" className="inline-flex">
            <Button variant="neutral">{t('errors.goHome')}</Button>
          </a>
        </div>
      </Card>
    </div>
  )
}
