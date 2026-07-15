import type { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'accent' | 'neutral'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
}

const variants: Record<Variant, string> = {
  primary: 'bg-primary text-white',
  accent: 'bg-accent text-white',
  neutral: 'bg-white text-ink',
}

/** Botão "retro": borda preta grossa e sombra dura que afunda ao clicar. */
export function Button({ variant = 'primary', className = '', disabled, ...props }: Props) {
  return (
    <button
      disabled={disabled}
      className={`border-[3px] border-ink px-5 py-2.5 font-extrabold uppercase tracking-wide
        shadow-retro-sm transition-transform
        enabled:hover:-translate-x-0.5 enabled:hover:-translate-y-0.5
        enabled:active:translate-x-1 enabled:active:translate-y-1 enabled:active:shadow-none
        disabled:cursor-not-allowed disabled:opacity-50
        ${variants[variant]} ${className}`}
      {...props}
    />
  )
}
