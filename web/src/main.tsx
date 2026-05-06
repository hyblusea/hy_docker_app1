import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { loader } from '@monaco-editor/react'
import App from './App'
import './styles/theme.css'

loader.config({ paths: { vs: 'https://registry.npmmirror.com/monaco-editor/0.45.0/files/min/vs' } })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
