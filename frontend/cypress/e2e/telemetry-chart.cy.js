describe('Telemetry Chart', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/**/latest*', { fixture: 'telemetry.json' }).as('telemetry')
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.wait('@telemetry')
  })

  it('shows Temperature/Humidity tab as active by default', () => {
    cy.contains('Temperature / Humidity').should('have.class', 'bg-sentinel-accent')
  })

  it('switching to Smoke tab makes it active', () => {
    cy.contains('Smoke (ppm)').click()
    cy.contains('Smoke (ppm)').should('have.class', 'bg-sentinel-accent')
    cy.contains('Temperature / Humidity').should('not.have.class', 'bg-sentinel-accent')
  })

  it('switching to Motion tab makes it active', () => {
    cy.contains('Motion').click()
    cy.contains('Motion').should('have.class', 'bg-sentinel-accent')
    cy.contains('Temperature / Humidity').should('not.have.class', 'bg-sentinel-accent')
  })

  it('switching time window to 1h calls range API', () => {
    cy.intercept('GET', '/api/v1/telemetry/uuid-1/range*', {
      statusCode: 200,
      body: [],
    }).as('rangeApi')
    cy.contains('1h').click()
    cy.wait('@rangeApi')
  })

  it('switching time window to 24h calls hourly API', () => {
    cy.intercept('GET', '/api/v1/telemetry/uuid-1/hourly*', {
      statusCode: 200,
      body: [],
    }).as('hourlyApi')
    cy.contains('24h').click()
    cy.wait('@hourlyApi')
  })

  it('switching time window to 7d calls hourly API', () => {
    cy.intercept('GET', '/api/v1/telemetry/uuid-1/hourly*', {
      statusCode: 200,
      body: [],
    }).as('hourlyApi')
    cy.contains('7d').click()
    cy.wait('@hourlyApi')
  })
})
