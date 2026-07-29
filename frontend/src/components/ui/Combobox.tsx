import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fuzzyFilter, type FuzzyOption } from '../../lib/fuzzy'

interface Props {
  label: string
  /** Valor selecionado ('' = a opção "allLabel"). */
  value: string
  onChange: (value: string) => void
  options: FuzzyOption[]
  placeholder?: string
  /** Rótulo da opção neutra no topo (ex.: "Todos"). */
  allLabel?: string
  /**
   * true (padrão): campo de texto com busca fuzzy — ideal para catálogos
   * grandes (mundos, criaturas). false: dropdown estilizado sem digitação —
   * para filtros de poucas opções (ex.: Vagas). Ambos compartilham a mesma
   * lista e visual.
   */
  searchable?: boolean
}

/**
 * Seletor com dropdown estilizado (borda/sombra "retro"). Em modo pesquisável,
 * filtra por busca fuzzy conforme o usuário digita; caso contrário, funciona
 * como um select comum. Componente único para manter todos os filtros iguais.
 */
export function Combobox({
  label,
  value,
  onChange,
  options,
  placeholder,
  allLabel = 'Todos',
  searchable = true,
}: Props) {
  const { t } = useTranslation()
  // Liga o <label> ao controle e o controle à lista. É acessibilidade (leitor de
  // tela anuncia "My vocation, combobox") e é o que dá aos testes de navegação um
  // seletor estável: `getByLabel(rótulo)` + `getByRole('option', ...)`.
  const controlId = useId()
  const listId = `${controlId}-list`
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [highlight, setHighlight] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)

  const selectedLabel = useMemo(
    () => options.find((o) => o.value === value)?.label ?? '',
    [options, value],
  )

  // Opção neutra ("Todos") sempre no topo. Em modo pesquisável, aplica o fuzzy.
  const items = useMemo(() => {
    const matches = searchable ? fuzzyFilter(query, options) : options
    return [{ value: '', label: allLabel }, ...matches]
  }, [searchable, query, options, allLabel])

  // Fecha ao clicar fora.
  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        close()
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  const close = () => {
    setOpen(false)
    setQuery('')
  }

  const select = (optionValue: string) => {
    onChange(optionValue)
    close()
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setHighlight((h) => Math.min(h + 1, items.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((h) => Math.max(h - 1, 0))
    } else if (e.key === 'Enter' || (!searchable && e.key === ' ')) {
      e.preventDefault()
      if (open && items[highlight]) select(items[highlight].value)
      else setOpen(true)
    } else if (e.key === 'Escape') {
      close()
    }
  }

  const triggerClass =
    'w-full border-[3px] border-ink bg-surface px-3 py-2.5 font-mono text-ink outline-none ' +
    'focus:shadow-retro-sm focus:-translate-x-0.5 focus:-translate-y-0.5 transition-transform'

  return (
    <div ref={containerRef} className="relative">
      <label htmlFor={controlId} className="mb-1.5 block text-sm font-extrabold uppercase text-ink">
        {label}
      </label>

      {searchable ? (
        <input
          id={controlId}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          className={triggerClass}
          value={open ? query : selectedLabel}
          placeholder={placeholder ?? allLabel}
          onFocus={() => {
            setOpen(true)
            setHighlight(0)
          }}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
            setHighlight(0)
          }}
          onKeyDown={onKeyDown}
        />
      ) : (
        <button
          id={controlId}
          type="button"
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={listId}
          className={`${triggerClass} flex items-center justify-between text-left`}
          onClick={() => setOpen((o) => !o)}
          onKeyDown={onKeyDown}
        >
          <span>{selectedLabel || allLabel}</span>
          <span aria-hidden className="ml-2 text-ink/60">
            ▾
          </span>
        </button>
      )}

      {open && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto border-[3px] border-ink bg-surface shadow-retro-sm"
        >
          {items.map((option, i) => (
            /* O <li> só existe pela semântica de lista; quem é a opção é o botão. */
            <li key={option.value || '__all__'} role="presentation">
              <button
                type="button"
                role="option"
                aria-selected={option.value === value}
                // Seleciona no mousedown para o clique não perder o foco do input
                // antes de a lista fechar.
                onMouseDown={(e) => {
                  e.preventDefault()
                  select(option.value)
                }}
                onMouseEnter={() => setHighlight(i)}
                className={`block w-full px-3 py-2 text-left font-mono text-sm text-ink
                  ${i === highlight ? 'bg-primary text-white' : ''}
                  ${option.value === value ? 'font-bold' : ''}`}
              >
                {option.label}
              </button>
            </li>
          ))}
          {searchable && items.length === 1 && (
            <li className="px-3 py-2 text-sm font-bold text-ink/50">{t('common.noResults')}</li>
          )}
        </ul>
      )}
    </div>
  )
}
