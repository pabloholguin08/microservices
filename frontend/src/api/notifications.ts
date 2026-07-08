import { apiFetch } from './client'
import type { Notification, PageResponse } from './types'

const BASE_URL = import.meta.env.VITE_NOTIFICATIONS_API_URL

export function getNotificationsByCustomer(customerId: string): Promise<PageResponse<Notification>> {
  return apiFetch<PageResponse<Notification>>(
    BASE_URL,
    `/api/v1/notifications?customerId=${encodeURIComponent(customerId)}`,
  )
}
