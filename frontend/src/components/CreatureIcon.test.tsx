import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CreatureIcon } from './CreatureIcon'

/**
 * O ícone da criatura e a reserva de iniciais (item P27).
 *
 * O que estes testes prendem é o **invariante do card**: nunca fica um buraco no lugar
 * do ícone. Ou a imagem tem bytes, ou aparecem as iniciais — e a segunda metade disso
 * não existia: imagem que falha não vira erro de console, então a tela ficava com um
 * `<img>` vazio e ninguém era avisado.
 */
describe('CreatureIcon', () => {
  it('com URL, mostra a imagem com o nome no alt', () => {
    render(<CreatureIcon imageUrl="https://static.tibia.com/images/library/demon.gif" name="Demon" />)

    const img = screen.getByRole('img', { name: 'Demon' })
    expect(img).toHaveAttribute('src', 'https://static.tibia.com/images/library/demon.gif')
  })

  it('sem URL, mostra as iniciais', () => {
    render(<CreatureIcon imageUrl={null} name="Rotworm" />)

    expect(screen.getByText('RO')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('imagem que falha cai para as iniciais, e nao deixa buraco', () => {
    render(<CreatureIcon imageUrl="https://static.tibia.com/images/library/troll.gif" name="Troll" />)

    // O evento que o navegador dispara quando a imagem não carrega (403 da
    // Cloudflare, host fora, bloqueador de conteúdo).
    fireEvent.error(screen.getByRole('img', { name: 'Troll' }))

    expect(screen.getByText('TR')).toBeInTheDocument()
    // O `<img>` sai do DOM: é isso que o teste de navegação verifica na página
    // inteira ("nenhuma imagem quebrada em cena").
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('as iniciais dizem qual criatura e, no title', () => {
    render(<CreatureIcon imageUrl={null} name="Cyclops Smith" />)

    // Duas letras sozinhas não identificam a criatura; o title resolve sem ocupar
    // espaço no card.
    expect(screen.getByTitle('Cyclops Smith')).toBeInTheDocument()
  })

  it('trocar de criatura depois de uma falha volta a tentar a imagem', () => {
    const { rerender } = render(
      <CreatureIcon imageUrl="https://static.tibia.com/images/library/troll.gif" name="Troll" />,
    )
    fireEvent.error(screen.getByRole('img', { name: 'Troll' }))
    expect(screen.getByText('TR')).toBeInTheDocument()

    // Mesma posição na lista, criatura outra: o React reaproveita a instância. Com um
    // booleano de "falhou", esta imagem nunca seria tentada — e o card do Demon
    // mostraria "DE" para sempre.
    rerender(<CreatureIcon imageUrl="https://static.tibia.com/images/library/demon.gif" name="Demon" />)

    expect(screen.getByRole('img', { name: 'Demon' })).toBeInTheDocument()
  })

  it('o tamanho pedido vale para a imagem e para as iniciais', () => {
    const { rerender } = render(<CreatureIcon imageUrl="https://x/y.gif" name="Demon" size={72} />)
    expect(screen.getByRole('img')).toHaveAttribute('width', '72')

    rerender(<CreatureIcon imageUrl={null} name="Demon" size={72} />)
    // O buraco e a reserva têm que ocupar o mesmo espaço, senão o card salta quando a
    // imagem falha.
    expect(screen.getByTitle('Demon')).toHaveStyle({ width: '72px', height: '72px' })
  })
})
