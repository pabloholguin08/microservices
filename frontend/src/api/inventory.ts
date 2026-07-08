import { apiFetch } from './client'
import type { PageResponse, Product } from './types'

const BASE_URL = import.meta.env.VITE_INVENTORY_API_URL

export function getProducts(): Promise<PageResponse<Product>> {
  return apiFetch<PageResponse<Product>>(BASE_URL, '/api/v1/products?size=100')
}
