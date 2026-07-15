export function Spinner({ label = 'Carregando…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 font-extrabold uppercase text-white">
      <span className="h-4 w-4 animate-spin border-[3px] border-white border-t-transparent" />
      {label}
    </div>
  )
}
