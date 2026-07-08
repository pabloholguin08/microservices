import { useState, type ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useCustomerId } from '../hooks/useCustomerId'

export function Layout({ children }: { children: ReactNode }) {
  const { customerId, setCustomerId } = useCustomerId()
  const [draftId, setDraftId] = useState(customerId)

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header__brand">
          Pedidos
          <span className="app-header__brand-mark">MANIFEST</span>
        </div>
        <nav className="app-header__nav">
          <NavLink to="/" end>
            Catalog
          </NavLink>
          <NavLink to="/orders">My Orders</NavLink>
        </nav>
        <form
          className="customer-id-form"
          onSubmit={(e) => {
            e.preventDefault()
            if (draftId.trim()) {
              setCustomerId(draftId.trim())
            }
          }}
        >
          <label htmlFor="customerId">Customer ID</label>
          <input
            id="customerId"
            value={draftId}
            onChange={(e) => setDraftId(e.target.value)}
            spellCheck={false}
          />
          <button type="submit">Set</button>
        </form>
      </header>
      <main className="app-main">{children}</main>
    </div>
  )
}
