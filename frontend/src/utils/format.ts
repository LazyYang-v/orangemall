/** 分 → 元字符串，例如 19900 → "199" / 19950 → "199.50" */
export function yuan(cents: number, keepZero = false): string {
  const v = cents / 100
  if (!keepZero && Number.isInteger(v)) return String(v)
  return v.toFixed(2)
}

export function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

export function formatTime(iso: string | number | Date | undefined, withSeconds = false): string {
  if (!iso) return '-'
  const d = new Date(iso)
  const base = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  return withSeconds ? `${base}:${pad(d.getSeconds())}` : base
}

export function shortDate(iso: string): string {
  const d = new Date(iso)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function maskPhone(phone: string): string {
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

export function splitDuration(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000))
  return { d: Math.floor(s / 86400), h: Math.floor((s % 86400) / 3600), m: Math.floor((s % 3600) / 60), s: s % 60 }
}

export function percent(part: number, total: number): number {
  if (!total) return 0
  return Math.min(100, Math.round((part / total) * 100))
}
