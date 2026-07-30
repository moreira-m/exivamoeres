import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import i18n from '../i18n'
import { useAuthStore } from '../store/authStore'

/**
 * Idioma fixo em **pt** para todos os testes de componente.
 *
 * O app escolhe o idioma pelo `localStorage` ou pelo `navigator.language`, e o
 * runner herda o locale da máquina: sem fixar aqui, a mesma asserção passaria
 * no meu terminal e falharia no CI. A suíte de `e2e/` roda em **en** — juntas,
 * as duas cobrem os dois idiomas.
 */
beforeEach(async () => {
  await i18n.changeLanguage('pt')
})

/**
 * `scrollIntoView` não existe no jsdom (é da camada de layout, que ele não implementa).
 * Componentes que rolam a tela — o `ChatPanel` ao chegar mensagem — chamariam `undefined`.
 * Stub no lugar de mudar o produto: rolar não é comportamento que este nível testa.
 */
Element.prototype.scrollIntoView = () => {}

afterEach(() => {
  cleanup()
  // O authStore é persistido em localStorage: sessão de um teste sobreviveria
  // ao próximo e o teste de "visitante não vê o formulário" passaria por sorte.
  useAuthStore.setState({ accessToken: null, refreshToken: null, user: null })
  localStorage.clear()
})
