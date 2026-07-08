import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { cancelOrder, getOrder } from '../api/orders'
import { getNotificationsByCustomer } from '../api/notifications'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import type { OrderStatus } from '../api/types'
import { useCustomerId } from '../hooks/useCustomerId'
import { formatCurrency, formatTrackingNumber } from '../lib/format'

const TERMINAL_STATUSES: OrderStatus[] = ['CONFIRMED', 'CANCELLED', 'FAILED']
const POLL_INTERVAL_MS = 1500

export function OrderStatusPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const { customerId } = useCustomerId()
  const queryClient = useQueryClient()

  const orderQuery = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => getOrder(orderId!),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && TERMINAL_STATUSES.includes(status) ? false : POLL_INTERVAL_MS
    },
  })

  const isCancelled = orderQuery.data?.status === 'CANCELLED'

  const notificationsQuery = useQuery({
    queryKey: ['notifications', customerId],
    queryFn: () => getNotificationsByCustomer(customerId),
    enabled: isCancelled,
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(orderId!),
    onSuccess: (order) => queryClient.setQueryData(['order', orderId], order),
  })

  if (orderQuery.isLoading) {
    return <p className="muted-text">Loading order…</p>
  }

  if (orderQuery.isError || !orderQuery.data) {
    return <p className="error-text">Could not find that order.</p>
  }

  const order = orderQuery.data
  const cancellationNotification = notificationsQuery.data?.items.find((n) => n.orderId === order.id)
  const isLive = !TERMINAL_STATUSES.includes(order.status)

  return (
    <div className="manifest">
      <div className="manifest__header">
        <div>
          <p className="manifest__eyebrow">Order manifest</p>
          <p className="manifest__tracking">{formatTrackingNumber(order.id)}</p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>

      {isLive && (
        <p className="manifest__live">
          <span className="manifest__live-dot" aria-hidden="true" />
          Watching for updates…
        </p>
      )}

      {cancellationNotification && <p className="manifest__reason">{cancellationNotification.message}</p>}

      <hr className="manifest__perforation" />

      <ul className="manifest__items">
        {order.items.map((item) => (
          <li key={item.id}>
            <span>
              {item.quantity} × product {item.productId.slice(0, 8)}
            </span>
            <span className="num">{formatCurrency(item.unitPrice * item.quantity)}</span>
          </li>
        ))}
      </ul>

      <div className="manifest__total">
        <span>Total</span>
        <span className="num">{formatCurrency(order.totalAmount)}</span>
      </div>

      <div className="manifest__footer">
        <Link to="/orders" className="manifest__back">
          View order history
        </Link>
        {order.status === 'CREATED' && (
          <button
            type="button"
            className="manifest__cancel"
            onClick={() => cancelMutation.mutate()}
            disabled={cancelMutation.isPending}
          >
            {cancelMutation.isPending ? 'Cancelling…' : 'Cancel order'}
          </button>
        )}
      </div>
    </div>
  )
}
