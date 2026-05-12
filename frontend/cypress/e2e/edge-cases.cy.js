describe('Edge Cases — Banners', () => {
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

  it('shows OfflineBanner when network goes offline', () => {
    cy.window().then(win => win.dispatchEvent(new win.Event('offline')))
    cy.contains('You are offline').should('be.visible')
  })

  it('OfflineBanner disappears when network comes back online', () => {
    cy.window().then(win => win.dispatchEvent(new win.Event('offline')))
    cy.contains('You are offline').should('be.visible')
    cy.window().then(win => win.dispatchEvent(new win.Event('online')))
    cy.contains('You are offline').should('not.exist')
  })

  it('shows VersionBanner on api-version mismatch event', () => {
    cy.window().then(win => {
      win.dispatchEvent(new win.CustomEvent('sentinel:api-version-mismatch'))
    })
    cy.contains('A new version is available.').should('be.visible')
  })

  it('shows VersionBanner on api-version-rejected event', () => {
    cy.window().then(win => {
      win.dispatchEvent(new win.CustomEvent('sentinel:api-version-rejected'))
    })
    cy.contains('This client version is no longer supported by the server.').should('be.visible')
  })
})
