import type { OrderStatus } from '../api/types'

const LABELS: Record<OrderStatus, string> = {
  CREATED: 'Processing',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`stamp stamp--${status.toLowerCase()}`}>{LABELS[status]}</span>
}
