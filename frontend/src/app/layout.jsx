import './globals.css'
import Providers from './providers'

export const metadata = {
  title: 'Sentinel IoT Platform',
  description: 'Production-grade Industrial IoT Monitoring Platform'
}

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          {children}
        </Providers>
      </body>
    </html>
  )
}
