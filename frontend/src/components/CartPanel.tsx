import type { Product } from '../api/types'
import { formatCurrency } from '../lib/format'

export interface CartLine {
  product: Product
  quantity: number
}

interface CartPanelProps {
  lines: CartLine[]
  onPlaceOrder: () => void
  isPlacing: boolean
  error: string | null
}

export function CartPanel({ lines, onPlaceOrder, isPlacing, error }: CartPanelProps) {
  const total = lines.reduce((sum, line) => sum + line.product.price * line.quantity, 0)

  return (
    <aside className="cart-panel">
      <p className="section-label">Manifest</p>
      {lines.length === 0 ? (
        <p className="cart-panel__empty">Select products to build an order.</p>
      ) : (
        <ul className="cart-panel__lines">
          {lines.map((line) => (
            <li key={line.product.id}>
              <span>
                {line.quantity} × {line.product.name}
              </span>
              <span className="num">{formatCurrency(line.product.price * line.quantity)}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="cart-panel__total">
        <span>Total</span>
        <span className="num">{formatCurrency(total)}</span>
      </div>
      {error && <p className="cart-panel__error">{error}</p>}
      <button
        type="button"
        className="cart-panel__submit"
        onClick={onPlaceOrder}
        disabled={lines.length === 0 || isPlacing}
      >
        {isPlacing ? 'Placing order…' : 'Place order'}
      </button>
    </aside>
  )
}
