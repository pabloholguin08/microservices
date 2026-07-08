import { useMutation, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getProducts } from '../api/inventory'
import { createOrder } from '../api/orders'
import { ApiError } from '../api/client'
import { CartPanel, type CartLine } from '../components/CartPanel'
import { ProductCard } from '../components/ProductCard'
import { useCustomerId } from '../hooks/useCustomerId'

export function CatalogPage() {
  const { customerId } = useCustomerId()
  const navigate = useNavigate()
  const [quantities, setQuantities] = useState<Record<string, number>>({})

  const productsQuery = useQuery({ queryKey: ['products'], queryFn: getProducts })
  const products = productsQuery.data?.items

  const placeOrder = useMutation({
    mutationFn: createOrder,
    onSuccess: (order) => {
      setQuantities({})
      navigate(`/orders/${order.id}`)
    },
  })

  const lines: CartLine[] = useMemo(() => {
    if (!products) return []
    return products
      .filter((product) => (quantities[product.id] ?? 0) > 0)
      .map((product) => ({ product, quantity: quantities[product.id] }))
  }, [products, quantities])

  if (productsQuery.isLoading) {
    return <p className="muted-text">Loading catalog…</p>
  }

  if (productsQuery.isError || !products) {
    return <p className="error-text">Could not load the product catalog. Is inventory-service running?</p>
  }

  return (
    <div className="catalog-layout">
      <section>
        <p className="section-label">Warehouse stock</p>
        <div className="product-grid">
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              quantity={quantities[product.id] ?? 0}
              onQuantityChange={(quantity) => setQuantities((prev) => ({ ...prev, [product.id]: quantity }))}
            />
          ))}
        </div>
      </section>
      <CartPanel
        lines={lines}
        isPlacing={placeOrder.isPending}
        error={placeOrder.isError ? errorMessage(placeOrder.error) : null}
        onPlaceOrder={() =>
          placeOrder.mutate({
            customerId,
            items: lines.map((line) => ({
              productId: line.product.id,
              quantity: line.quantity,
              unitPrice: line.product.price,
            })),
          })
        }
      />
    </div>
  )
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Something went wrong placing the order.'
}
