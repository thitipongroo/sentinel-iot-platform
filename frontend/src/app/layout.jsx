import './globals.css'
import Providers from './providers'
import VersionBanner from '@/components/VersionBanner'

export const metadata = {
  title: 'Sentinel IoT Platform',
  description: 'Production-grade Industrial IoT Monitoring Platform'
}

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <VersionBanner />
          {children}
        </Providers>
      </body>
    </html>
  )
}
