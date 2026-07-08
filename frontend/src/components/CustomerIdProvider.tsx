import { useMemo, useState, type ReactNode } from 'react'
import { CustomerIdContext } from '../hooks/useCustomerId'

const STORAGE_KEY = 'pedidos.customerId'

function loadOrCreateCustomerId(): string {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) {
    return stored
  }
  const generated = crypto.randomUUID()
  localStorage.setItem(STORAGE_KEY, generated)
  return generated
}

export function CustomerIdProvider({ children }: { children: ReactNode }) {
  const [customerId, setCustomerIdState] = useState(loadOrCreateCustomerId)

  const value = useMemo(
    () => ({
      customerId,
      setCustomerId: (next: string) => {
        localStorage.setItem(STORAGE_KEY, next)
        setCustomerIdState(next)
      },
    }),
    [customerId],
  )

  return <CustomerIdContext.Provider value={value}>{children}</CustomerIdContext.Provider>
}
