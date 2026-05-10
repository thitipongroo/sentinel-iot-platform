'use client'

import { QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/hooks/useAuth'
import ErrorBoundary from '@/components/ui/ErrorBoundary'
import { queryClient } from '@/lib/queryClient'

export default function Providers({ children }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ErrorBoundary label="Application">
          {children}
        </ErrorBoundary>
      </AuthProvider>
    </QueryClientProvider>
  )
}
