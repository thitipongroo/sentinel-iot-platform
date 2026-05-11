Cypress.Commands.add('mockAuthAsAdmin', () => {
  cy.intercept('POST', '/api/v1/auth/refresh', {
    statusCode: 200,
    body: { accessToken: 'fake-admin-token', username: 'admin', role: 'ADMIN' },
  }).as('refresh')
})

Cypress.Commands.add('mockAuthAsOperator', () => {
  cy.intercept('POST', '/api/v1/auth/refresh', {
    statusCode: 200,
    body: { accessToken: 'fake-op-token', username: 'operator', role: 'OPERATOR' },
  }).as('refresh')
})
