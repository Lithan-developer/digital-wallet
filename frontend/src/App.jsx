import { useState } from 'react'
import AuthPage from './components/AuthPage.jsx'
import Dashboard from './components/Dashboard.jsx'

export default function App() {
  // sessionStorage clears when the tab closes; avoids a long-lived token sitting in localStorage
  const [session, setSession] = useState(() => {
    try { return JSON.parse(sessionStorage.getItem('session')) } catch { return null }
  })

  const login = (s) => { sessionStorage.setItem('session', JSON.stringify(s)); setSession(s) }
  const logout = () => { sessionStorage.removeItem('session'); setSession(null) }

  return session
    ? <Dashboard session={session} onLogout={logout} />
    : <AuthPage onLogin={login} />
}
