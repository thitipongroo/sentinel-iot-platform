/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        sentinel: {
          900: '#0a0f1e',
          800: '#0d1530',
          700: '#112044',
          600: '#1a3a6e',
          accent: '#00d4ff',
          warning: '#f59e0b',
          danger: '#ef4444',
          success: '#10b981'
        }
      }
    }
  },
  plugins: []
}
