describe('Sentinel IoT Dashboard', () => {
  beforeEach(() => {
    cy.intercept('POST', '/api/auth/login', {
      statusCode: 200,
      body: { token: 'fake-jwt-token', role: 'ADMIN', username: 'admin' }
    }).as('login')

    cy.intercept('GET', '/api/devices', {
      statusCode: 200,
      body: [
        { id: 'uuid-1', name: 'sensor-1', status: 'ONLINE', location: 'Factory A', lastSeen: new Date().toISOString() },
        { id: 'uuid-2', name: 'sensor-2', status: 'OFFLINE', location: 'Factory B', lastSeen: null }
      ]
    }).as('devices')

    cy.intercept('GET', '/api/alerts', { statusCode: 200, body: [] }).as('alerts')
    cy.intercept('GET', '/api/telemetry/stats', { statusCode: 200, body: { lastMinute: 42 } }).as('stats')
    cy.intercept('GET', '/api/telemetry/uuid-1/latest*', { statusCode: 200, body: [] }).as('telemetry')
  })

  it('redirects unauthenticated users to /login', () => {
    cy.visit('/')
    cy.url().should('include', '/login')
  })

  it('logs in and navigates to /dashboard', () => {
    cy.visit('/login')
    cy.get('input[placeholder="admin"]').type('admin')
    cy.get('input[placeholder="••••••••"]').type('admin123')
    cy.get('button[type="submit"]').click()
    cy.wait('@login')
    cy.url().should('include', '/dashboard')
    cy.contains('Sentinel').should('be.visible')
    cy.wait('@devices')
    cy.contains('sensor-1').should('be.visible')
  })

  it('shows stats bar with event count', () => {
    localStorage.setItem('sentinel_token', 'fake-jwt-token')
    localStorage.setItem('sentinel_user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    cy.visit('/dashboard')
    cy.wait('@devices')
    cy.contains('Total Devices').should('be.visible')
    cy.contains('42').should('be.visible')
  })

  it('shows all chart tabs', () => {
    localStorage.setItem('sentinel_token', 'fake-jwt-token')
    localStorage.setItem('sentinel_user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    cy.visit('/dashboard')
    cy.wait('@devices')
    cy.contains('Temperature / Humidity').should('be.visible')
    cy.contains('Smoke (ppm)').should('be.visible')
    cy.contains('Motion').should('be.visible')
  })
})
