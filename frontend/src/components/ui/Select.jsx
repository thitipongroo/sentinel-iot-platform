import { clsx } from 'clsx'

/**
 * Design-system Select primitive with accessible label + options.
 */
export default function Select({ id, label, value, onChange, options, className }) {
  return (
    <div className={clsx('flex flex-col gap-1', className)}>
      {label && (
        <label htmlFor={id} className="text-xs text-gray-500 font-medium">
          {label}
        </label>
      )}
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="px-2 py-1.5 text-sm bg-sentinel-900 border border-sentinel-700 rounded-md
                   text-gray-300 focus:outline-none focus:border-sentinel-accent cursor-pointer"
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  )
}
