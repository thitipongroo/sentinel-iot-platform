const { defineConfig } = require('cypress')
const fs = require('fs')
const path = require('path')

module.exports = defineConfig({
  screenshotsFolder: '../docs/screenshots',
  e2e: {
    baseUrl: 'http://localhost:3000',
    viewportWidth: 1920,
    viewportHeight: 1080,
    defaultCommandTimeout: 8000,
    video: false,
    screenshotOnRunFailure: false,
    specPattern: 'cypress/e2e/**/*.cy.{js,jsx}',
    supportFile: 'cypress/support/e2e.js',
    setupNodeEvents(on, config) {
      on('before:browser:launch', (browser = {}, launchOptions) => {
        if (browser.family === 'chromium' && browser.name !== 'electron') {
          launchOptions.args.push('--window-size=1920,1080')
        }
        if (browser.name === 'electron') {
          launchOptions.preferences.width = 1920
          launchOptions.preferences.height = 1080
          launchOptions.preferences.resizable = false
        }
        return launchOptions
      })

      on('after:screenshot', (details) => {
        const targetDir = path.resolve(__dirname, config.screenshotsFolder)
        const targetPath = path.join(targetDir, `${details.name}.png`)
        if (details.path !== targetPath) {
          fs.mkdirSync(path.dirname(targetPath), { recursive: true })
          fs.renameSync(details.path, targetPath)
          // Recursively clean up empty directories left by Cypress under screenshotsFolder
          let curr = path.dirname(details.path)
          while (curr && curr !== targetDir && curr.startsWith(targetDir)) {
            try {
              if (fs.readdirSync(curr).length === 0) {
                fs.rmdirSync(curr)
                curr = path.dirname(curr)
              } else {
                break
              }
            } catch (e) {
              break
            }
          }
          return { path: targetPath }
        }
      })
    },
  },
})
