import { useTranslation } from 'react-i18next'

export function Spinner({ label }: { label?: string }) {
  const { t } = useTranslation()
  return (
    // `role="status"` é o papel ARIA correto para "estou carregando" (leitor de
    // tela anuncia sem roubar o foco) — e é o que os testes de navegação usam
    // para esperar a tela assentar antes de julgar se ela tem conteúdo.
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-2 font-extrabold uppercase text-white"
    >
      <span className="h-4 w-4 animate-spin border-[3px] border-white border-t-transparent" />
      {label ?? t('common.loading')}
    </div>
  )
}
