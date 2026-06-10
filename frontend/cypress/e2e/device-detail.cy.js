describe('Device Detail Page', () => {
  const device = {
    id: 'uuid-1',
    name: 'sensor-alpha',
    status: 'ONLINE',
    lifecycleStatus: 'ACTIVE',
    location: 'Factory A',
    firmwareVersion: '1.2.0',
    lastSeen: '2026-05-12T10:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    description: 'Primary assembly line sensor',
  }

  beforeEach(() => {
    cy.intercept('GET', `/api/v1/devices/${device.id}`, { body: device }).as('device')
    cy.intercept('GET', `/api/v1/telemetry/${device.id}/latest*`, { fixture: 'telemetry.json' }).as('telemetry')
    cy.intercept('GET', `/api/v1/alerts?deviceId=${device.id}`, { body: [] }).as('deviceAlerts')
    cy.intercept('GET', '/api/v1/alerts', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.mockAuthAsAdmin()
    cy.visit(`/devices/${device.id}`)
    cy.wait('@refresh')
    cy.wait('@device')
  })

  it('shows device name in the breadcrumb and heading', () => {
    cy.contains('sensor-alpha').should('be.visible')
    cy.contains('a', 'Devices').should('have.attr', 'href', '/devices')
  })

  it('displays device info fields', () => {
    cy.contains('Factory A').should('be.visible')
    cy.contains('1.2.0').should('be.visible')
    cy.contains('ACTIVE').should('be.visible')
  })

  it('shows ONLINE status indicator', () => {
    cy.contains('ONLINE').should('be.visible')
  })

  it('ADMIN sees device management panel', () => {
    cy.get('[data-testid="device-management"], [class*="DeviceManagement"], .card')
      .contains(/lifecycle|firmware|decommission/i)
      .should('exist')
  })

  it('shows "Device not found" for an unknown device ID', () => {
    cy.intercept('GET', '/api/v1/devices/unknown-id', {
      statusCode: 404,
      body: { message: 'Device not found' },
    }).as('notFound')
    cy.visit('/devices/unknown-id')
    cy.wait('@notFound')
    cy.contains('Device not found').should('be.visible')
    cy.contains('← Back to Devices').should('be.visible')
  })

  it('OPERATOR does not see device management panel', () => {
    cy.intercept('GET', `/api/v1/devices/${device.id}`, { body: device }).as('deviceOp')
    cy.intercept('GET', `/api/v1/telemetry/${device.id}/latest*`, { fixture: 'telemetry.json' }).as('telemetryOp')
    cy.intercept('GET', `/api/v1/alerts?deviceId=${device.id}`, { body: [] }).as('deviceAlertsOp')
    cy.mockAuthAsOperator()
    cy.visit(`/devices/${device.id}`)
    cy.wait('@refresh')
    cy.wait('@deviceOp')
    // DeviceManagement panel is only rendered when isAdmin is true
    cy.contains(/decommission|change lifecycle/i).should('not.exist')
  })
})
