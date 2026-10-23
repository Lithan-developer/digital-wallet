// Every money-moving call gets a fresh Idempotency-Key, so a double click or network retry can never charge twice.
export async function api(path, { method = 'GET', body, token, idempotent = false } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (idempotent) headers['Idempotency-Key'] = crypto.randomUUID()

  const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    const err = new Error(data?.message || `Request failed (${res.status})`)
    err.status = res.status
    throw err
  }
  return data
}

export const money = (n, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(n)
