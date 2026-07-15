import type { HTMLAttributes } from 'react'

/** Caixa branca com borda e sombra duras — bloco visual base da referência. */
export function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`border-[3px] border-ink bg-white text-ink shadow-retro ${className}`}
      {...props}
    />
  )
}
