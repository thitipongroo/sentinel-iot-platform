'use client'

import { Component } from 'react'

/**
 * React error boundary — catches render-phase errors in the subtree and
 * shows a scoped fallback UI rather than crashing the whole page.
 *
 * Usage:
 *   <ErrorBoundary label="Device list">
 *     <DeviceTable ... />
 *   </ErrorBoundary>
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error }
  }

  componentDidCatch(error, info) {
    // In production wire this to your error tracking service (Sentry, Datadog, etc.)
    console.error(`[ErrorBoundary:${this.props.label ?? 'unknown'}]`, error, info.componentStack)
  }

  reset() {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="rounded-lg border border-sentinel-danger/40 bg-sentinel-danger/10 p-6 text-center space-y-3"
        >
          <p className="text-sentinel-danger font-semibold text-sm">
            {this.props.label ? `${this.props.label} failed to render` : 'Something went wrong'}
          </p>
          <p className="text-gray-400 text-xs font-mono truncate">
            {this.state.error?.message}
          </p>
          <button
            onClick={() => this.reset()}
            className="text-xs text-sentinel-accent hover:underline"
          >
            Try again
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
