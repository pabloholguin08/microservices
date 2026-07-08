import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getOrdersByCustomer } from '../api/orders'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { useCustomerId } from '../hooks/useCustomerId'
import { formatCurrency, formatDateTime, formatTrackingNumber } from '../lib/format'

export function OrderHistoryPage() {
  const { customerId } = useCustomerId()

  const ordersQuery = useQuery({
    queryKey: ['orders', customerId],
    queryFn: () => getOrdersByCustomer(customerId),
  })
  const orders = ordersQuery.data?.items

  if (ordersQuery.isLoading) {
    return <p className="muted-text">Loading order history…</p>
  }

  if (ordersQuery.isError || !orders) {
    return <p className="error-text">Could not load order history.</p>
  }

  if (orders.length === 0) {
    return <p className="muted-text">No orders yet for this customer. Head to the catalog to place one.</p>
  }

  return (
    <>
      <p className="section-label">Order history</p>
      <table className="order-history">
        <thead>
          <tr>
            <th>Tracking no.</th>
            <th>Placed</th>
            <th>Status</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.id}>
              <td>
                <Link to={`/orders/${order.id}`}>{formatTrackingNumber(order.id)}</Link>
              </td>
              <td>{formatDateTime(order.createdAt)}</td>
              <td>
                <OrderStatusBadge status={order.status} />
              </td>
              <td className="num">{formatCurrency(order.totalAmount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
