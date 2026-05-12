describe('Dashboard Overview', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/**/latest*', { fixture: 'telemetry.json' }).as('telemetry')
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
  })

  it('shows StatsBar with correct total device count', () => {
    cy.contains('Total Devices').siblings('p').should('have.text', '5')
  })

  it('shows online and offline counts', () => {
    cy.contains('Online').siblings('p').should('have.text', '3')
    cy.contains('Offline').siblings('p').should('have.text', '2')
  })

  it('shows critical alert count', () => {
    cy.contains('Critical Alerts').siblings('p').should('have.text', '2')
  })

  it('shows events-per-minute from stats API', () => {
    cy.contains('Events / min').siblings('p').should('have.text', '42')
  })

  it('shows warning color on buffered count greater than zero', () => {
    cy.contains('Buffered').siblings('p').should('have.class', 'text-sentinel-warning')
  })

  it('device list renders after data loads', () => {
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('sensor-beta').should('be.visible')
  })
})
