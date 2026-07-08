import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { CustomerIdProvider } from './components/CustomerIdProvider'
import { Layout } from './components/Layout'
import { CatalogPage } from './pages/CatalogPage'
import { OrderHistoryPage } from './pages/OrderHistoryPage'
import { OrderStatusPage } from './pages/OrderStatusPage'

const queryClient = new QueryClient()

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <CustomerIdProvider>
        <BrowserRouter>
          <Layout>
            <Routes>
              <Route path="/" element={<CatalogPage />} />
              <Route path="/orders" element={<OrderHistoryPage />} />
              <Route path="/orders/:orderId" element={<OrderStatusPage />} />
            </Routes>
          </Layout>
        </BrowserRouter>
      </CustomerIdProvider>
    </QueryClientProvider>
  )
}

export default App
