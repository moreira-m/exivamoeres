import { useState } from 'react'

interface Props {
  imageUrl: string | null
  name: string
  size?: number
}

/**
 * Ícone da criatura vindo da TibiaData, com as **iniciais do nome** como reserva.
 *
 * A reserva vale para dois casos, e o segundo é o que faltava: quando não há URL
 * (criatura importada sem imagem) **e quando a URL existe e não carrega**. Antes, a
 * imagem que falhava deixava um `<img>` vazio na tela — um buraco do tamanho do ícone,
 * no lugar mais visível do card. Nada no sistema notava: imagem que não carrega não
 * vira erro de console nem derruba a página.
 *
 * O host dos sprites (`static.tibia.com`) fica atrás da Cloudflare, que serve navegador
 * e **bloqueia cliente que não é navegador** (403 com página de desafio). Para o usuário
 * a imagem carrega; num IP de datacenter, atrás de um bloqueador ou se a Cloudflare
 * apertar a régua, pode não carregar — e é por não dar para garantir que a reserva
 * existe.
 */
export function CreatureIcon({ imageUrl, name, size = 40 }: Props) {
  // Guarda a **URL** que falhou, não um booleano: assim o estado se reseta sozinho
  // quando o componente é reaproveitado para outra criatura (o React reusa a instância
  // por posição na lista, e um `true` preso mostraria as iniciais para uma imagem que
  // carrega perfeitamente).
  const [urlQueFalhou, setUrlQueFalhou] = useState<string | null>(null)

  if (imageUrl && imageUrl !== urlQueFalhou) {
    return (
      <img
        src={imageUrl}
        alt={name}
        width={size}
        height={size}
        onError={() => setUrlQueFalhou(imageUrl)}
        /* Fundo branco fixo: sprites do Tibia foram feitos para fundo claro. */
        className="border-2 border-ink bg-white object-contain"
      />
    )
  }
  return (
    <div
      style={{ width: size, height: size }}
      /* O nome no `title`: duas iniciais sozinhas não dizem qual criatura é. */
      title={name}
      className="flex items-center justify-center border-2 border-ink bg-surface text-xs font-black text-ink"
    >
      {name.slice(0, 2).toUpperCase()}
    </div>
  )
}
