import { apiFetch } from './client'
import type { CreateOrderRequest, Order, PageResponse } from './types'

const BASE_URL = import.meta.env.VITE_ORDERS_API_URL

export function createOrder(request: CreateOrderRequest): Promise<Order> {
  return apiFetch<Order>(BASE_URL, '/api/v1/orders', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getOrder(orderId: string): Promise<Order> {
  return apiFetch<Order>(BASE_URL, `/api/v1/orders/${orderId}`)
}

export function getOrdersByCustomer(customerId: string): Promise<PageResponse<Order>> {
  return apiFetch<PageResponse<Order>>(BASE_URL, `/api/v1/orders?customerId=${encodeURIComponent(customerId)}`)
}

export function cancelOrder(orderId: string): Promise<Order> {
  return apiFetch<Order>(BASE_URL, `/api/v1/orders/${orderId}/cancel`, { method: 'PUT' })
}
