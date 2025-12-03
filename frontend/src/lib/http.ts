async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`HTTP ${res.status} ${res.statusText}${text ? ` — ${text}` : ''}`)
  }
  // 204 No Content
  if (res.status === 204) return undefined as T
  const ct = res.headers.get('content-type') || ''
  if (ct.includes('application/json')) return (await res.json()) as T
  // fallback
  return (await res.text()) as unknown as T
}

export async function GET<T>(path: string, init?: RequestInit) {
  const res = await fetch(path, { method: 'GET', ...init })
  return handle<T>(res)
}
export async function POST<T>(path: string, body: unknown, init?: RequestInit) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    body: JSON.stringify(body),
    ...init,
  })
  return handle<T>(res)
}

