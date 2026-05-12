describe('Device Filters', () => {
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

  it('search filters device list by name', () => {
    cy.get('input[type="search"]').type('alpha')
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('sensor-beta').should('not.exist')
  })

  it('status filter shows only ONLINE devices', () => {
    cy.get('#filter-status').select('ONLINE')
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('sensor-beta').should('not.exist')
    cy.contains('sensor-omega').should('not.exist')
  })

  it('lifecycle filter shows only ACTIVE devices', () => {
    cy.get('#filter-lifecycle').select('ACTIVE')
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('sensor-beta').should('be.visible')
    cy.contains('sensor-gamma').should('not.exist')
    cy.contains('sensor-delta').should('not.exist')
  })

  it('device count label reflects filtered result', () => {
    cy.contains('5 of 5 devices').should('be.visible')
    cy.get('#filter-lifecycle').select('ACTIVE')
    cy.contains('2 of 5 devices').should('be.visible')
  })

  it('clear button resets all filters', () => {
    cy.get('input[type="search"]').type('alpha')
    cy.contains('sensor-beta').should('not.exist')
    cy.get('[aria-label="Clear all filters"]').click()
    cy.contains('sensor-beta').should('be.visible')
  })

  it('clicking a device row selects it', () => {
    cy.contains('sensor-beta').click()
    cy.contains('sensor-beta')
      .closest('[role="row"]')
      .should('have.attr', 'aria-selected', 'true')
  })

  it('selected device triggers telemetry fetch', () => {
    cy.intercept('GET', '/api/v1/telemetry/uuid-2/latest*', { fixture: 'telemetry.json' }).as('telemetry2')
    cy.contains('sensor-beta').click()
    cy.wait('@telemetry2')
  })
})
