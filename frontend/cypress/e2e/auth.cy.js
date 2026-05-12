describe('Authentication', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/**/latest*', { fixture: 'telemetry.json' }).as('telemetry')
  })

  it('redirects unauthenticated user from / to /login', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
    cy.visit('/')
    cy.url().should('include', '/login')
  })

  it('redirects unauthenticated user from /dashboard to /login', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
    cy.visit('/dashboard')
    cy.url().should('include', '/login')
  })

  it('login with valid credentials navigates to /dashboard', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 200,
      body: { accessToken: 'fake-jwt-token', username: 'admin', role: 'ADMIN' },
    }).as('login')
    cy.visit('/login')
    cy.get('input[placeholder="admin"]').type('admin')
    cy.get('input[type="password"]').type('admin123')
    cy.get('button[type="submit"]').click()
    cy.wait('@login')
    cy.url().should('include', '/dashboard')
  })

  it('login with invalid credentials shows error message', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 401,
      body: { message: 'Invalid username or password' },
    }).as('login')
    cy.visit('/login')
    cy.get('input[placeholder="admin"]').type('admin')
    cy.get('input[type="password"]').type('wrongpassword')
    cy.get('button[type="submit"]').click()
    cy.wait('@login')
    cy.contains('Invalid username or password').should('be.visible')
  })

  it('logout clears session and redirects to /login', () => {
    cy.intercept('POST', '/api/v1/auth/logout', { statusCode: 200, body: {} }).as('logout')
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.get('[aria-label="Log out"]').click()
    cy.wait('@logout')
    cy.url().should('include', '/login')
  })
})
