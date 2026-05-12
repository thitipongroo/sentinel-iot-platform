describe('Admin — Device Management', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/**/latest*', { fixture: 'telemetry.json' }).as('telemetry')
  })

  // sensor-alpha (uuid-1) is auto-selected on load — ACTIVE lifecycle
  it('ADMIN sees lifecycle controls', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Device Management').should('be.visible')
    cy.contains('→ INACTIVE').should('be.visible')
    cy.contains('→ DECOMMISSIONED').should('be.visible')
  })

  it('OPERATOR does not see lifecycle controls', () => {
    cy.mockAuthAsOperator()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Device Management').should('not.exist')
  })

  it('lifecycle transition calls PATCH API', () => {
    cy.intercept('PATCH', '/api/v1/devices/uuid-1/lifecycle', {
      statusCode: 200,
      body: {},
    }).as('patchLifecycle')
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('→ INACTIVE').click()
    cy.wait('@patchLifecycle').its('request.body').should('deep.equal', {
      lifecycleStatus: 'INACTIVE',
    })
  })

  it('firmware input validates semver format', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.get('input[placeholder="e.g. 1.2.3"]').type('not-semver')
    cy.get('button').contains('Update').click()
    cy.contains('Version must follow semver').should('be.visible')
  })

  it('firmware update calls PATCH API with correct body', () => {
    cy.intercept('PATCH', '/api/v1/devices/uuid-1/firmware', {
      statusCode: 200,
      body: {},
    }).as('patchFirmware')
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.get('input[placeholder="e.g. 1.2.3"]').type('2.1.0')
    cy.get('button').contains('Update').click()
    cy.wait('@patchFirmware').its('request.body').should('deep.equal', {
      firmwareVersion: '2.1.0',
    })
  })

  it('DECOMMISSIONED device disables all controls', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    // Click sensor-omega (DECOMMISSIONED lifecycle)
    cy.contains('sensor-omega').click()
    cy.contains('Device is decommissioned — no further transitions allowed.').should('be.visible')
    cy.get('input[placeholder="e.g. 1.2.3"]').should('be.disabled')
    cy.get('button').contains('Update').should('be.disabled')
  })
})
