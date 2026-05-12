describe('Alerts', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/**/latest*', { fixture: 'telemetry.json' }).as('telemetry')
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.wait('@alerts')
  })

  it('shows all alerts by default', () => {
    cy.contains('Temperature exceeded 80°C').should('be.visible')
    cy.contains('Smoke level exceeded 200 ppm').should('be.visible')
    cy.contains('Temperature above 70°C').should('be.visible')
  })

  it('unacknowledged badge shows correct count', () => {
    // 2 unacked alerts (a1 and a2)
    cy.contains('h2', 'Alerts').within(() => {
      cy.contains('2').should('be.visible')
    })
  })

  it('clicking Unacknowledged tab filters to unacked alerts only', () => {
    cy.contains('button', 'Unacknowledged').click()
    cy.contains('Temperature exceeded 80°C').should('be.visible')
    cy.contains('Smoke level exceeded 200 ppm').should('be.visible')
    cy.contains('Temperature above 70°C').should('not.exist')
  })

  it('ADMIN sees Acknowledge button on unacked alerts', () => {
    const ackButtons = cy.get('button').filter(':contains("Ack")')
    ackButtons.should('have.length', 2)
  })

  it('clicking Acknowledge calls the acknowledge API', () => {
    cy.intercept('PUT', '/api/v1/alerts/a1/acknowledge', {
      statusCode: 200,
      body: {},
    }).as('ackAlert')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alertsRefetch')
    cy.get('button').filter(':contains("Ack")').first().click()
    cy.wait('@ackAlert')
  })
})
