describe('Sentinel IoT Dashboard', () => {
  beforeEach(() => {
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 200,
      body: { accessToken: 'fake-jwt-token', role: 'ADMIN', username: 'admin' },
    }).as('login')
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/uuid-1/latest*', { fixture: 'telemetry.json' }).as('telemetry')
  })

  it('redirects unauthenticated users to /login', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
    cy.visit('/')
    cy.url().should('include', '/login')
  })

  it('logs in and navigates to /dashboard', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
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
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Total Devices').should('be.visible')
    cy.contains('42').should('be.visible')
  })

  it('shows all chart tabs', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Temperature / Humidity').should('be.visible')
    cy.contains('Smoke (ppm)').should('be.visible')
    cy.contains('Motion').should('be.visible')
  })
})
