const nextJest = require('next/jest')

const createJestConfig = nextJest({ dir: './' })

const customConfig = {
  testEnvironment: 'jest-environment-jsdom',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  testMatch: ['**/__tests__/**/*.{js,jsx}', '**/*.test.{js,jsx}'],
}

// Export async to allow overriding transformIgnorePatterns after next/jest builds its defaults
module.exports = async () => {
  const nextConfig = await createJestConfig(customConfig)()
  return {
    ...nextConfig,
    // Allow Jest to transform MSW and its ESM dependencies
    transformIgnorePatterns: [
      '/node_modules/(?!(msw|@mswjs|rettime|@bundled-es-modules)/)',
    ],
  }
}
