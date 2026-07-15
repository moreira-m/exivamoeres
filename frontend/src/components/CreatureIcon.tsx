interface Props {
  imageUrl: string | null
  name: string
  size?: number
}

/** Ícone da criatura vindo da TibiaData; cai para as iniciais se faltar imagem. */
export function CreatureIcon({ imageUrl, name, size = 40 }: Props) {
  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt={name}
        width={size}
        height={size}
        /* Fundo branco fixo: sprites do Tibia foram feitos para fundo claro. */
        className="border-2 border-ink bg-white object-contain"
      />
    )
  }
  return (
    <div
      style={{ width: size, height: size }}
      className="flex items-center justify-center border-2 border-ink bg-surface text-xs font-black text-ink"
    >
      {name.slice(0, 2).toUpperCase()}
    </div>
  )
}
