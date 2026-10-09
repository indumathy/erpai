/**
 * Display formatting only. The frontend never calculates money: all amounts it shows
 * (line nets, tax, totals) come from the backend, which is the single source of truth.
 */

export function formatMoney(amount: number, currency: string, maxFractionDigits = 2): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: maxFractionDigits,
  }).format(amount)
}

/** Unit prices may carry sub-cent precision (backend scale 4). */
export function formatUnitPrice(amount: number, currency: string): string {
  return formatMoney(amount, currency, 4)
}

export function formatQuantity(quantity: number): string {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 3 }).format(quantity)
}

export function formatPercent(percent: number): string {
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(percent)} %`
}

/** "2026-10-06" -> localised date, without time-zone shifts (parsed as a calendar date). */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year!, month! - 1, day!).toLocaleDateString()
}

export function todayIsoDate(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
