import type { InputHTMLAttributes, SelectHTMLAttributes, ReactNode } from 'react'

const fieldClass =
  'w-full border-[3px] border-ink bg-white px-3 py-2.5 font-mono text-ink outline-none ' +
  'focus:shadow-retro-sm focus:-translate-x-0.5 focus:-translate-y-0.5 transition-transform'

function Field({ label, children }: { label?: string; children: ReactNode }) {
  return (
    <label className="block">
      {label && (
        <span className="mb-1.5 block text-sm font-extrabold uppercase text-white">{label}</span>
      )}
      {children}
    </label>
  )
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
}

export function Input({ label, className = '', ...props }: InputProps) {
  return (
    <Field label={label}>
      <input className={`${fieldClass} ${className}`} {...props} />
    </Field>
  )
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
}

export function Select({ label, className = '', children, ...props }: SelectProps) {
  return (
    <Field label={label}>
      <select className={`${fieldClass} ${className}`} {...props}>
        {children}
      </select>
    </Field>
  )
}
