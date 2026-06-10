describe('Settings Page', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/settings', { fixture: 'settings.json' }).as('settings')
    cy.mockAuthAsAdmin()
    cy.visit('/settings')
    cy.wait('@refresh')
    cy.wait('@settings')
  })

  it('renders threshold, retention, and notification sections', () => {
    cy.contains('h2', 'Alert Thresholds').should('be.visible')
    cy.contains('h2', 'Data Retention').should('be.visible')
    cy.contains('h2', 'Notification Channels').should('be.visible')
    cy.contains('h2', 'Platform').should('be.visible')
  })

  it('displays loaded threshold values from the fixture', () => {
    cy.get('input[type="number"]').first().should('have.value', '80')
  })

  it('ADMIN can edit threshold fields and Save Changes button appears', () => {
    cy.get('input[type="number"]').first().clear().type('85')
    cy.contains('button', 'Save Changes').should('be.visible')
  })

  it('Save Changes calls PATCH /api/v1/settings', () => {
    cy.intercept('PATCH', '/api/v1/settings', { statusCode: 200, body: {} }).as('saveSettings')
    cy.get('input[type="number"]').first().clear().type('85')
    cy.contains('button', 'Save Changes').click()
    cy.wait('@saveSettings')
    cy.contains('Settings saved successfully').should('be.visible')
  })

  it('OPERATOR sees read-only fields (all inputs disabled)', () => {
    cy.intercept('GET', '/api/v1/settings', { fixture: 'settings.json' }).as('settingsOp')
    cy.mockAuthAsOperator()
    cy.visit('/settings')
    cy.wait('@refresh')
    cy.wait('@settingsOp')
    cy.get('input[type="number"]').each($el => {
      cy.wrap($el).should('be.disabled')
    })
    cy.contains('button', 'Save Changes').should('not.exist')
  })
})
