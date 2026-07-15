/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  // Dark mode por classe: a classe `dark` no <html> alterna as variáveis CSS
  // definidas em styles/index.css.
  darkMode: 'class',
  theme: {
    extend: {
      // Paleta "retro" inspirada visualmente na pasta referencia/. As cores de
      // marca (primary/accent) são fixas nos dois temas; as demais são tokens
      // ligados a variáveis CSS que mudam entre claro e escuro.
      colors: {
        primary: '#288dfe',
        'primary-dark': '#1f74d0',
        accent: '#fc5c21',
        // Tokens temáveis (ver :root e .dark em styles/index.css):
        ink: 'var(--color-content)', // texto e bordas sobre superfícies
        surface: 'var(--color-surface)', // cards, inputs
        canvas: 'var(--color-base)', // fundos sutis dentro de cards
        page: 'var(--color-page)', // fundo da página
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
      },
      boxShadow: {
        // Sombra "dura" deslocada, marca visual da referência.
        retro: '6px 6px 0 var(--color-shadow)',
        'retro-sm': '4px 4px 0 var(--color-shadow)',
        'retro-lg': '8px 8px 0 var(--color-shadow)',
      },
    },
  },
  plugins: [],
}
