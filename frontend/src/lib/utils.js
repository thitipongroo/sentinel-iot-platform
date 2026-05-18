/**
 * Returns a concise relative time string for a timestamp.
 * e.g. "5s ago", "3m ago", "2h ago", "1d ago"
 */
export function relativeTime(ts) {
  if (!ts) return '—'
  const diff = (Date.now() - new Date(ts)) / 1000
  if (diff < 60)    return `${Math.round(diff)}s ago`
  if (diff < 3600)  return `${Math.round(diff / 60)}m ago`
  if (diff < 86400) return `${Math.round(diff / 3600)}h ago`
  return `${Math.round(diff / 86400)}d ago`
}
