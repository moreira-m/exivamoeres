/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // Paleta "retro" inspirada visualmente na pasta referencia/ (reimplementada
      // do zero): azul primário, laranja de destaque, tinta preta para bordas.
      colors: {
        ink: '#1a1a1a',
        primary: '#288dfe',
        'primary-dark': '#1f74d0',
        accent: '#fc5c21',
        canvas: '#fdfdfd',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
      },
      boxShadow: {
        // Sombra "dura" deslocada, marca visual da referência.
        retro: '6px 6px 0 #1a1a1a',
        'retro-sm': '4px 4px 0 #1a1a1a',
        'retro-lg': '8px 8px 0 #1a1a1a',
      },
    },
  },
  plugins: [],
}
