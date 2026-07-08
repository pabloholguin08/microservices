/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ORDERS_API_URL: string
  readonly VITE_INVENTORY_API_URL: string
  readonly VITE_NOTIFICATIONS_API_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
