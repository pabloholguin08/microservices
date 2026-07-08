export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'CANCELLED' | 'FAILED'

export interface Product {
  id: string
  name: string
  sku: string
  price: number
  stockQuantity: number
}

export interface OrderItem {
  id: string
  productId: string
  quantity: number
  unitPrice: number
}

export interface Order {
  id: string
  customerId: string
  status: OrderStatus
  totalAmount: number
  createdAt: string
  updatedAt: string
  items: OrderItem[]
}

export interface CreateOrderItemRequest {
  productId: string
  quantity: number
  unitPrice: number
}

export interface CreateOrderRequest {
  customerId: string
  items: CreateOrderItemRequest[]
}

export type NotificationType = 'ORDER_CONFIRMED' | 'ORDER_CANCELLED'

export interface Notification {
  id: string
  orderId: string
  customerId: string
  type: NotificationType
  message: string
  sentAt: string
}

export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}
