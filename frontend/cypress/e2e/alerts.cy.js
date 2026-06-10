// Paginated response format expected by the /alerts page
const alertsPage = {
  content: [
    { id: 'a1', deviceId: 'uuid-1', level: 'CRITICAL', message: 'Temperature exceeded 80°C',   acknowledged: false, createdAt: '2026-05-12T09:00:00Z' },
    { id: 'a2', deviceId: 'uuid-2', level: 'CRITICAL', message: 'Smoke level exceeded 200 ppm', acknowledged: false, createdAt: '2026-05-12T08:30:00Z' },
    { id: 'a3', deviceId: 'uuid-3', level: 'WARNING',  message: 'Temperature above 70°C',        acknowledged: true,  createdAt: '2026-05-12T08:00:00Z' },
  ],
  totalPages: 1,
  totalElements: 3,
}

const unackedAlerts = [
  { id: 'a1', deviceId: 'uuid-1', level: 'CRITICAL', message: 'Temperature exceeded 80°C',   acknowledged: false, createdAt: '2026-05-12T09:00:00Z' },
  { id: 'a2', deviceId: 'uuid-2', level: 'CRITICAL', message: 'Smoke level exceeded 200 ppm', acknowledged: false, createdAt: '2026-05-12T08:30:00Z' },
]

describe('Alerts Page (/alerts)', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/alerts*', { body: alertsPage }).as('alertsPage')
    cy.intercept('GET', '/api/v1/alerts/unacknowledged', { body: unackedAlerts }).as('unacked')
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.mockAuthAsAdmin()
    cy.visit('/alerts')
    cy.wait('@refresh')
    cy.wait('@alertsPage')
    cy.wait('@unacked')
  })

  it('filter by CRITICAL level hides WARNING alerts', () => {
    // The WARNING alert (a3) should disappear when CRITICAL level filter is selected
    cy.contains('Temperature above 70°C').should('be.visible')
    cy.get('select').first().select('CRITICAL')
    cy.contains('Temperature above 70°C').should('not.exist')
    cy.contains('Temperature exceeded 80°C').should('be.visible')
  })

  it('Clear button resets both level and status filters', () => {
    cy.get('select').first().select('CRITICAL')
    cy.contains('Temperature above 70°C').should('not.exist')
    cy.contains('button', 'Clear').click()
    cy.contains('Temperature above 70°C').should('be.visible')
  })

  it('ADMIN sees Acknowledge All button when there are unacknowledged alerts', () => {
    cy.contains('button', 'Acknowledge All').should('be.visible')
    cy.contains('p', '2 unacknowledged').should('be.visible')
  })
})

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
