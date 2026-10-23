import { useCallback, useEffect, useState } from 'react'
import { api, money } from '../api.js'

const ACTIONS = {
  deposit: { label: 'Add money', path: '/api/wallet/deposit', cta: 'Add money' },
  withdraw: { label: 'Withdraw', path: '/api/wallet/withdraw', cta: 'Withdraw' },
  transfer: { label: 'Send money', path: '/api/wallet/transfer', cta: 'Send money' },
}

export default function Dashboard({ session, onLogout }) {
  const { token } = session
  const [wallet, setWallet] = useState(null)
  const [history, setHistory] = useState({ content: [], page: 0, totalPages: 0 })
  const [page, setPage] = useState(0)
  const [tab, setTab] = useState('transfer')
  const [form, setForm] = useState({ amount: '', recipientEmail: '', description: '' })
  const [msg, setMsg] = useState(null) // { type: 'ok' | 'error', text }
  const [busy, setBusy] = useState(false)

  const guard = useCallback((err) => {
    if (err.status === 401) onLogout()
    else setMsg({ type: 'error', text: err.status === 429 ? 'Too many requests. Wait a minute and try again.' : err.message })
  }, [onLogout])

  const load = useCallback(async () => {
    try {
      const [w, h] = await Promise.all([
        api('/api/wallet', { token }),
        api(`/api/wallet/transactions?page=${page}&size=8`, { token }),
      ])
      setWallet(w); setHistory(h)
    } catch (err) { guard(err) }
  }, [token, page, guard])

  useEffect(() => { load() }, [load])

  async function submit(e) {
    e.preventDefault()
    setMsg(null); setBusy(true)
    try {
      const amount = Number(form.amount)
      const body = tab === 'transfer'
        ? { recipientEmail: form.recipientEmail, amount, description: form.description || undefined }
        : { amount }
      await api(ACTIONS[tab].path, { method: 'POST', body, token, idempotent: true })
      setMsg({ type: 'ok', text: tab === 'transfer' ? `Sent ${money(amount)} to ${form.recipientEmail}.` : `${ACTIONS[tab].label} complete: ${money(amount)}.` })
      setForm({ amount: '', recipientEmail: '', description: '' })
      if (page === 0) await load(); else setPage(0)
    } catch (err) { guard(err) }
    finally { setBusy(false) }
  }

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  return (
    <div className="shell">
      <header className="top">
        <strong>Wallet</strong>
        <span>{session.fullName}<button className="link" onClick={onLogout}>Log out</button></span>
      </header>

      <section className="balance">
        <p>Available balance</p>
        <h1>{wallet ? money(wallet.balance, wallet.currency) : '…'}</h1>
        <small>Wallet #{wallet?.walletId} · {wallet?.ownerEmail}</small>
      </section>

      <div className="grid">
        <form className="panel" onSubmit={submit}>
          <div className="tabs" role="tablist">
            {Object.entries(ACTIONS).map(([k, a]) => (
              <button type="button" key={k} role="tab" aria-selected={tab === k}
                      className={tab === k ? 'active' : ''} onClick={() => { setTab(k); setMsg(null) }}>{a.label}</button>
            ))}
          </div>
          {tab === 'transfer' && (
            <label>Recipient email<input type="email" required value={form.recipientEmail} onChange={set('recipientEmail')} /></label>
          )}
          <label>Amount (₹)<input type="number" required min="0.01" step="0.01" inputMode="decimal" value={form.amount} onChange={set('amount')} /></label>
          {tab === 'transfer' && (
            <label>Note (optional)<input maxLength={140} value={form.description} onChange={set('description')} /></label>
          )}
          {msg && <p className={msg.type === 'ok' ? 'ok' : 'error'} role="status">{msg.text}</p>}
          <button className="primary" disabled={busy}>{busy ? 'Processing…' : ACTIONS[tab].cta}</button>
        </form>

        <section className="panel">
          <h2>Transactions</h2>
          {history.content.length === 0 && <p className="empty">No transactions yet. Add money to get started.</p>}
          <ul className="tx">
            {history.content.map((t) => {
              const credit = t.toWalletId === wallet?.walletId
              const title = t.type === 'TRANSFER' ? (credit ? 'Received' : 'Sent') : t.type === 'DEPOSIT' ? 'Added money' : 'Withdrawal'
              return (
                <li key={t.id}>
                  <div>
                    <b>{title}</b>
                    <small>{new Date(t.createdAt).toLocaleString('en-IN')}{t.description ? ` · ${t.description}` : ''}</small>
                  </div>
                  <span className={credit ? 'credit' : 'debit'}>{credit ? '+' : '−'}{money(t.amount)}</span>
                </li>
              )
            })}
          </ul>
          {history.totalPages > 1 && (
            <div className="pager">
              <button className="link" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
              <small>Page {history.page + 1} of {history.totalPages}</small>
              <button className="link" disabled={page + 1 >= history.totalPages} onClick={() => setPage(page + 1)}>Older</button>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
