/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'sans-serif'],
      },
      colors: {
        // 界面灰不带蓝，靠近 Figma 的中性灰
        slate: {
          50: '#fafafa',
          100: '#f5f5f5',
          200: '#e5e5e5',
          300: '#d4d4d4',
          400: '#333333',
          500: '#1a1a1a',
          600: '#111111',
          700: '#000000',
          800: '#000000',
          900: '#000000',
          950: '#000000',
        },
        // 原先的翠绿强调色改成 Figma 蓝 #0D99FF
        emerald: {
          50: '#f2f9ff',
          100: '#e5f4ff',
          200: '#c5e6ff',
          300: '#8fd0ff',
          400: '#4db5ff',
          500: '#0d99ff',
          600: '#0d99ff',
          700: '#0b7ed4',
          800: '#0963a8',
          900: '#064a7d',
          950: '#043056',
        },
        brand: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
          950: '#1e1b4b',
        },
      },
      boxShadow: {
        'subtle': '0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 3px 0 rgba(0, 0, 0, 0.05)',
        'elevated': '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
        'floating': '0 10px 25px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -4px rgba(0, 0, 0, 0.04)',
        'glow-brand': '0 0 0 1px rgba(99, 102, 241, 0.1), 0 4px 14px 0 rgba(99, 102, 241, 0.25)',
      },
    },
  },
  plugins: [],
}
