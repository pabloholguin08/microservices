const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount)
}

/**
 * Renders a UUID as a manifest-style tracking number, e.g. PED-B70A-3F52.
 *
 * IDs are time-ordered (see UuidGenerator.Style.TIME on the backend), so the
 * leading bytes are near-identical for orders created close together —
 * slicing from the front would make two different orders placed in the same
 * second show the same "tracking number". The trailing bytes carry the
 * actual per-record entropy, so slice from the end instead.
 */
export function formatTrackingNumber(id: string): string {
  const hex = id.replace(/-/g, '').slice(-8).toUpperCase()
  return `PED-${hex.slice(0, 4)}-${hex.slice(4, 8)}`
}

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function formatDateTime(iso: string): string {
  return dateFormatter.format(new Date(iso))
}
