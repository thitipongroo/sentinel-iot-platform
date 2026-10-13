describe('E2E Full-Screen Capture — Structured by Menu', () => {
  const alertsPage = {
    content: [
      { id: 'a1', deviceId: 'uuid-1', level: 'CRITICAL', message: 'Temperature exceeded 80°C', acknowledged: false, createdAt: '2026-05-12T09:00:00Z' },
      { id: 'a2', deviceId: 'uuid-2', level: 'CRITICAL', message: 'Smoke level exceeded 200 ppm', acknowledged: false, createdAt: '2026-05-12T08:30:00Z' },
      { id: 'a3', deviceId: 'uuid-3', level: 'WARNING', message: 'Temperature above 70°C', acknowledged: true, createdAt: '2026-05-12T08:00:00Z' },
    ],
    totalPages: 1,
    totalElements: 3,
  }

  beforeEach(() => {
    cy.intercept('GET', '/api/v1/devices', { fixture: 'devices.json' }).as('devices')
    cy.intercept('GET', '/api/v1/devices/*', { fixture: 'devices.json' }).as('deviceDetail')
    cy.intercept('GET', '/api/v1/alerts*', { fixture: 'alerts.json' }).as('alerts')
    cy.intercept('GET', '/api/v1/alerts/unacknowledged*', { fixture: 'alerts.json' }).as('unacked')
    cy.intercept('GET', '/api/v1/telemetry/stats', { fixture: 'stats.json' }).as('stats')
    cy.intercept('GET', '/api/v1/telemetry/**/latest*', { fixture: 'telemetry.json' }).as('telemetry')
    cy.intercept('GET', '/api/v1/users', { fixture: 'users.json' }).as('users')
    cy.intercept('GET', '/api/v1/settings', { fixture: 'settings.json' }).as('settings')
  })

  // 01-login
  it('captures 01-login / login-page', () => {
    cy.intercept('POST', '/api/v1/auth/refresh', { statusCode: 401 }).as('refresh')
    cy.visit('/login')
    cy.contains('Sentinel IoT').should('be.visible')
    cy.wait(400)
    cy.screenshot('01-login/login-page', { capture: 'fullPage' })
  })

  // 02-dashboard
  it('captures 02-dashboard / dashboard-overview', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Total Devices').should('be.visible')
    cy.contains('sensor-alpha').should('be.visible')
    cy.wait(600)
    cy.screenshot('02-dashboard/dashboard-overview', { capture: 'fullPage' })
  })

  it('captures 02-dashboard / telemetry-smoke-tab', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('button', 'Smoke (ppm)').click()
    cy.wait(600)
    cy.screenshot('02-dashboard/telemetry-smoke-tab', { capture: 'fullPage' })
  })

  it('captures 02-dashboard / admin-device-management', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Device Management').scrollIntoView().should('be.visible')
    cy.wait(600)
    cy.screenshot('02-dashboard/admin-device-management', { capture: 'fullPage' })
  })

  // 03-devices
  it('captures 03-devices / devices-list', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/devices')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('h1', 'Devices').should('be.visible')
    cy.contains('sensor-alpha').should('be.visible')
    cy.wait(600)
    cy.screenshot('03-devices/devices-list', { capture: 'fullPage' })
  })

  it('captures 03-devices / device-detail', () => {
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
    cy.intercept('GET', '/api/v1/devices/uuid-1', { body: device }).as('device')
    cy.intercept('GET', '/api/v1/alerts?deviceId=uuid-1', { body: [] }).as('deviceAlerts')
    cy.mockAuthAsAdmin()
    cy.visit('/devices/uuid-1')
    cy.wait('@refresh')
    cy.wait('@device')
    cy.contains('sensor-alpha').should('be.visible')
    cy.wait(600)
    cy.screenshot('03-devices/device-detail', { capture: 'fullPage' })
  })

  // 04-digital-twin
  it('captures 04-digital-twin / digital-twin-hub', () => {
    const shadow = {
      deviceId: 'uuid-1',
      reported: { temperature: 24.5, humidity: 45.2, operatingMode: 'OPTIMAL', telemetryIntervalSec: 5 },
      desired: { targetTemperature: 22.5, operatingMode: 'OPTIMAL', telemetryIntervalSec: 5 },
      delta: { targetTemperature: 22.5 },
      version: 42,
      lastReportedAt: '2026-05-12T10:00:00Z',
    }
    cy.intercept('GET', '/api/v1/devices/*/shadow', { body: shadow }).as('shadow')
    cy.mockAuthAsAdmin()
    cy.visit('/digital-twin')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Digital Twin & Device Shadow Hub').should('be.visible')
    cy.wait(600)
    cy.screenshot('04-digital-twin/digital-twin-hub', { capture: 'fullPage' })
  })

  // 05-predictive-ai
  it('captures 05-predictive-ai / predictive-ai-analytics', () => {
    const baselines = {
      deviceId: 'uuid-1',
      baselines: {
        TEMPERATURE: { mean: 24.2, stdDev: 1.5, min: 20.0, max: 28.0, p95: 26.5 },
        HUMIDITY: { mean: 45.0, stdDev: 3.2, min: 38.0, max: 52.0, p95: 49.8 },
        SMOKE: { mean: 12.0, stdDev: 2.1, min: 5.0, max: 25.0, p95: 18.0 },
      },
    }
    cy.intercept('GET', '/api/v1/anomalies/baselines/*', { body: baselines }).as('baselines')
    cy.mockAuthAsAdmin()
    cy.visit('/predictive-ai')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('Predictive AI').should('be.visible')
    cy.wait(600)
    cy.screenshot('05-predictive-ai/predictive-ai-analytics', { capture: 'fullPage' })
  })

  // 06-lakehouse
  it('captures 06-lakehouse / lakehouse-explorer', () => {
    const lakehouseData = {
      meta: { totalRowsScanned: 1500000, executionTimeMs: 14.2, engine: 'DuckDB 1.1 Embedded OLAP', parquetFilesRead: 12 },
      data: [
        { bucketTime: '2026-02-01', avg: 22.4, min: 18.2, max: 26.1, count: 50000 },
        { bucketTime: '2026-03-01', avg: 23.1, min: 19.0, max: 27.4, count: 52000 },
        { bucketTime: '2026-04-01', avg: 24.5, min: 20.1, max: 28.9, count: 48000 },
        { bucketTime: '2026-05-01', avg: 24.8, min: 20.5, max: 29.2, count: 51000 },
      ],
    }
    cy.intercept('GET', '/api/v1/lakehouse/query*', { body: lakehouseData }).as('lakehouseQuery')
    cy.mockAuthAsAdmin()
    cy.visit('/lakehouse')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.contains('DuckDB Lakehouse').should('be.visible')
    cy.wait(600)
    cy.screenshot('06-lakehouse/lakehouse-explorer', { capture: 'fullPage' })
  })

  // 07-alerts
  it('captures 07-alerts / alerts-management', () => {
    cy.intercept('GET', '/api/v1/alerts*', { body: alertsPage }).as('alertsPage')
    cy.mockAuthAsAdmin()
    cy.visit('/alerts')
    cy.wait('@refresh')
    cy.wait('@alertsPage')
    cy.contains('h1', 'Alerts').should('be.visible')
    cy.wait(600)
    cy.screenshot('07-alerts/alerts-management', { capture: 'fullPage' })
  })

  // 08-users
  it('captures 08-users / user-management', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/users')
    cy.wait('@refresh')
    cy.wait('@users')
    cy.contains('h1', 'Users').should('be.visible')
    cy.contains('admin').should('be.visible')
    cy.wait(600)
    cy.screenshot('08-users/user-management', { capture: 'fullPage' })
  })

  // 09-settings
  it('captures 09-settings / platform-settings', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/settings')
    cy.wait('@refresh')
    cy.wait('@settings')
    cy.contains('h1', 'Settings').should('be.visible')
    cy.wait(600)
    cy.screenshot('09-settings/platform-settings', { capture: 'fullPage' })
  })

  // 10-system
  it('captures 10-system / system-offline-banner', () => {
    cy.mockAuthAsAdmin()
    cy.visit('/dashboard')
    cy.wait('@refresh')
    cy.wait('@devices')
    cy.window().then((win) => {
      win.dispatchEvent(new win.Event('offline'))
    })
    cy.contains('You are offline').should('be.visible')
    cy.wait(600)
    cy.screenshot('10-system/system-offline-banner', { capture: 'fullPage' })
  })
})
