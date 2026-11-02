import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import App from './App.jsx'
import { ErrorBoundary } from './components/ErrorBoundary.jsx'
import './styles/global.css'

createRoot(document.getElementById('root')).render(
  <React.StrictMode><BrowserRouter><AuthProvider><ErrorBoundary><App /></ErrorBoundary></AuthProvider></BrowserRouter></React.StrictMode>,
)
