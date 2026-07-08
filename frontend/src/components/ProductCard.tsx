import type { Product } from '../api/types'
import { formatCurrency } from '../lib/format'

interface ProductCardProps {
  product: Product
  quantity: number
  onQuantityChange: (quantity: number) => void
}

export function ProductCard({ product, quantity, onQuantityChange }: ProductCardProps) {
  const outOfStock = product.stockQuantity === 0

  return (
    <div className={`product-card${outOfStock ? ' product-card--disabled' : ''}`}>
      <div className="product-card__body">
        <h3>{product.name}</h3>
        <p className="product-card__sku">{product.sku}</p>
        <p className="product-card__price num">{formatCurrency(product.price)}</p>
        <p className={`product-card__stock${outOfStock ? ' product-card__stock--out' : ''}`}>
          {outOfStock ? 'Out of stock' : `${product.stockQuantity} in stock`}
        </p>
      </div>
      <div className="product-card__quantity">
        <button
          type="button"
          aria-label={`Decrease quantity of ${product.name}`}
          onClick={() => onQuantityChange(Math.max(0, quantity - 1))}
          disabled={outOfStock || quantity === 0}
        >
          −
        </button>
        <span>{quantity}</span>
        <button
          type="button"
          aria-label={`Increase quantity of ${product.name}`}
          onClick={() => onQuantityChange(Math.min(product.stockQuantity, quantity + 1))}
          disabled={outOfStock || quantity >= product.stockQuantity}
        >
          +
        </button>
      </div>
    </div>
  )
}
