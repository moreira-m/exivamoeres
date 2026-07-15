import type { HTMLAttributes } from 'react'

type Tone = 'primary' | 'accent' | 'neutral' | 'muted'

const tones: Record<Tone, string> = {
  primary: 'bg-primary text-white',
  accent: 'bg-accent text-white',
  neutral: 'bg-ink text-white',
  muted: 'bg-surface text-ink',
}

interface Props extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone
}

export function Badge({ tone = 'neutral', className = '', ...props }: Props) {
  return (
    <span
      className={`inline-block border-2 border-ink px-2 py-0.5 text-xs font-extrabold uppercase ${tones[tone]} ${className}`}
      {...props}
    />
  )
}
