describe('Devices Page', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.mockAuthAsAdmin()
    cy.visit('/devices')
    cy.wait('@refresh')
    cy.wait('@devices')
  })

  it('renders the device list with all registered devices', () => {
    cy.contains('h1', 'Devices').should('be.visible')
    cy.contains('5 registered').should('be.visible')
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('sensor-beta').should('be.visible')
    cy.contains('sensor-gamma').should('be.visible')
  })

  it('shows ONLINE badge for online devices and OFFLINE for offline devices', () => {
    cy.contains('tr', 'sensor-alpha').contains('ONLINE').should('be.visible')
    cy.contains('tr', 'sensor-beta').contains('OFFLINE').should('be.visible')
  })

  it('filter by OFFLINE status hides ONLINE devices', () => {
    cy.get('select').first().next('select').select('OFFLINE')
    cy.contains('sensor-alpha').should('not.exist')
    cy.contains('sensor-beta').should('be.visible')
    cy.contains('sensor-omega').should('be.visible')
  })

  it('search by name filters the table', () => {
    cy.get('input[type="search"]').type('alpha')
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('sensor-beta').should('not.exist')
  })

  it('Clear button resets all filters', () => {
    cy.get('input[type="search"]').type('alpha')
    cy.contains('sensor-beta').should('not.exist')
    cy.contains('button', 'Clear').click()
    cy.contains('sensor-beta').should('be.visible')
  })

  it('ADMIN sees Register Device button', () => {
    cy.contains('button', 'Register Device').should('be.visible')
  })

  it('OPERATOR does not see Register Device button', () => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devicesOp')
    cy.mockAuthAsOperator()
    cy.visit('/devices')
    cy.wait('@refresh')
    cy.wait('@devicesOp')
    cy.contains('button', 'Register Device').should('not.exist')
  })
})
