// Fuzzy matching leve, sem dependência externa.
// Pontua cada candidato contra a query; quanto maior, melhor. Retorna null
// quando não há match (nem substring nem subsequência na ordem digitada).

function normalize(text: string): string {
  // \p{Diacritic} remove os acentos após decompor (NFD) — sem depender de
  // literais de marcas combinantes no código-fonte.
  return text.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '')
}

export function fuzzyScore(query: string, text: string): number | null {
  const q = normalize(query.trim())
  const t = normalize(text)
  if (q.length === 0) return 0

  // Substring contígua: melhor match, priorizado por posição inicial.
  const index = t.indexOf(q)
  if (index !== -1) {
    return 1000 - index
  }

  // Subsequência: os caracteres da query aparecem em ordem, não contíguos.
  let ti = 0
  let firstMatch = -1
  let lastMatch = -1
  for (let qi = 0; qi < q.length; qi++) {
    const found = t.indexOf(q[qi], ti)
    if (found === -1) return null
    if (firstMatch === -1) firstMatch = found
    lastMatch = found
    ti = found + 1
  }
  // Quanto mais "espalhada" a subsequência, menor a pontuação.
  const spread = lastMatch - firstMatch
  return 400 - spread - firstMatch
}

export interface FuzzyOption {
  value: string
  label: string
}

/** Filtra e ordena opções pelo score fuzzy (melhores primeiro). */
export function fuzzyFilter(query: string, options: FuzzyOption[]): FuzzyOption[] {
  if (query.trim().length === 0) return options
  return options
    .map((option) => ({ option, score: fuzzyScore(query, option.label) }))
    .filter((entry): entry is { option: FuzzyOption; score: number } => entry.score !== null)
    .sort((a, b) => b.score - a.score || a.option.label.localeCompare(b.option.label))
    .map((entry) => entry.option)
}
