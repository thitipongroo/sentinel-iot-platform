describe('Users Page', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/users', { fixture: 'users.json' }).as('users')
    cy.mockAuthAsAdmin()
    cy.visit('/users')
    cy.wait('@refresh')
    cy.wait('@users')
  })

  it('renders user list with member count', () => {
    cy.contains('h1', 'Users').should('be.visible')
    cy.contains('3 members').should('be.visible')
    cy.contains('admin').should('be.visible')
    cy.contains('operator1').should('be.visible')
  })

  it('shows ADMIN and OPERATOR role labels for each user', () => {
    cy.contains('tr', 'admin').contains('ADMIN').should('be.visible')
    cy.contains('tr', 'operator1').contains('OPERATOR').should('be.visible')
  })

  it('marks the currently logged-in user with "(you)"', () => {
    // The mock auth user is "admin"
    cy.contains('tr', 'admin').contains('(you)').should('be.visible')
  })

  it('Delete and Reset Password buttons are disabled for own account', () => {
    cy.contains('tr', 'admin').within(() => {
      cy.contains('button', 'Delete').should('be.disabled')
      cy.contains('button', 'Reset Password').should('be.disabled')
    })
  })

  it('Delete button opens a confirmation dialog for other users', () => {
    cy.contains('tr', 'operator1').within(() => {
      cy.contains('button', 'Delete').click()
    })
    cy.contains('Delete User').should('be.visible')
    cy.contains('operator1').should('be.visible')
    cy.contains('button', 'Cancel').click()
    cy.contains('Delete User').should('not.exist')
  })

  it('confirmed Delete calls DELETE /api/v1/users/:username', () => {
    cy.intercept('DELETE', '/api/v1/users/operator1', { statusCode: 204, body: {} }).as('deleteUser')
    cy.intercept('GET', '/api/v1/users', { fixture: 'users.json' }).as('usersRefetch')
    cy.contains('tr', 'operator1').within(() => {
      cy.contains('button', 'Delete').click()
    })
    cy.contains('button', 'Delete').last().click()
    cy.wait('@deleteUser')
  })

  it('Add User button opens the modal', () => {
    cy.contains('button', '+ Add User').click()
    cy.contains('h2', 'Add User').should('be.visible')
    cy.contains('button', 'Cancel').click()
    cy.contains('h2', 'Add User').should('not.exist')
  })

  it('Add User form submits POST /api/v1/users', () => {
    cy.intercept('POST', '/api/v1/users', { statusCode: 201, body: { id: 'user-4', username: 'newuser', role: 'OPERATOR' } }).as('createUser')
    cy.intercept('GET', '/api/v1/users', { fixture: 'users.json' }).as('usersRefetch')
    cy.contains('button', '+ Add User').click()
    cy.get('input[placeholder="john.doe"]').type('newuser')
    cy.get('input[placeholder="Min 8 characters"]').first().type('password123')
    cy.contains('button', 'Create User').click()
    cy.wait('@createUser')
  })
})
