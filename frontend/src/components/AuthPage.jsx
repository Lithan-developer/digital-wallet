import { useState } from 'react'
import { api } from '../api.js'

export default function AuthPage({ onLogin }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError(''); setBusy(true)
    try {
      const body = mode === 'login' ? { email: form.email, password: form.password } : form
      const res = await api(`/api/auth/${mode}`, { method: 'POST', body })
      onLogin({ token: res.token, email: res.email, fullName: res.fullName })
    } catch (err) {
      setError(err.status === 429 ? 'Too many attempts. Wait a minute and try again.' : err.message)
    } finally { setBusy(false) }
  }

  return (
    <main className="auth">
      <section className="auth-intro">
        <h1>Send, receive and hold money in one wallet.</h1>
        <p>Every transfer is all-or-nothing: your balance and the ledger always agree.</p>
      </section>
      <form className="panel auth-form" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Log in' : 'Create your wallet'}</h2>
        {mode === 'register' && (
          <label>Full name<input value={form.fullName} onChange={set('fullName')} required maxLength={100} /></label>
        )}
        <label>Email<input type="email" value={form.email} onChange={set('email')} required autoComplete="email" /></label>
        <label>Password
          <input type="password" value={form.password} onChange={set('password')} required minLength={8}
                 autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
        </label>
        {mode === 'register' && <small className="hint">At least 8 characters.</small>}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create wallet'}</button>
        <button type="button" className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>
          {mode === 'login' ? 'New here? Create a wallet' : 'Already have a wallet? Log in'}
        </button>
      </form>
    </main>
  )
}
