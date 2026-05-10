import { clsx } from 'clsx'

const VARIANTS = {
  default:  'bg-sentinel-700 text-gray-300',
  success:  'bg-sentinel-success/20 text-sentinel-success',
  warning:  'bg-sentinel-warning/20 text-sentinel-warning',
  danger:   'bg-sentinel-danger/20 text-sentinel-danger',
  accent:   'bg-sentinel-accent/20 text-sentinel-accent',
  critical: 'bg-sentinel-danger text-white',
  online:   'bg-sentinel-success/20 text-sentinel-success',
  offline:  'bg-gray-700 text-gray-400',
}

/**
 * Design-system Badge primitive.
 *
 * @param {string} variant - default | success | warning | danger | accent | critical | online | offline
 * @param {string} className - additional Tailwind classes
 */
export default function Badge({ variant = 'default', className, children }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium leading-tight',
        VARIANTS[variant] ?? VARIANTS.default,
        className
      )}
    >
      {children}
    </span>
  )
}
