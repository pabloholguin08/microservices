import { createContext, useContext } from 'react'

export interface CustomerIdContextValue {
  customerId: string
  setCustomerId: (customerId: string) => void
}

export const CustomerIdContext = createContext<CustomerIdContextValue | null>(null)

export function useCustomerId(): CustomerIdContextValue {
  const context = useContext(CustomerIdContext)
  if (!context) {
    throw new Error('useCustomerId must be used within a CustomerIdProvider')
  }
  return context
}
