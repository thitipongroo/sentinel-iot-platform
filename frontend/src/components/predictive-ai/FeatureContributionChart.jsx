'use client'

import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Cell
} from 'recharts'

const COLOR_MAP = {
  temperature: '#ef4444',
  smokePpm: '#f59e0b',
  humidity: '#00d4ff',
  tempRateOfChange: '#ec4899',
  humidityRateOfChange: '#8b5cf6',
  thermalRunawayIndex: '#dc2626'
}

export default function FeatureContributionChart({ contributions = {}, anomalyScore = 0.15 }) {
  const data = Object.entries(contributions || {}).map(([key, val]) => {
    const formattedName = key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, str => str.toUpperCase())

    return {
      featureKey: key,
      name: formattedName,
      contribution: Number(val.toFixed(1))
    }
  }).sort((a, b) => b.contribution - a.contribution)

  const isAnomalous = anomalyScore >= 0.65

  return (
    <div className="card flex flex-col h-full">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-white font-semibold text-base flex items-center gap-2">
            <span>Explainable AI (XAI)</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-sentinel-700 text-gray-300 font-mono">
              Feature Attribution
            </span>
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">
            Marginal ablation breakdown indicating sensor impact on the anomaly score
          </p>
        </div>

        <div className="text-right">
          <span className="text-[10px] text-gray-400 block uppercase tracking-wider font-mono">Anomaly Score</span>
          <span className={`text-base font-bold font-mono ${
            anomalyScore >= 0.8
              ? 'text-sentinel-danger animate-pulse'
              : anomalyScore >= 0.65
                ? 'text-sentinel-warning'
                : 'text-sentinel-success'
          }`}>
            {(anomalyScore * 100).toFixed(1)}%
          </span>
        </div>
      </div>

      {data.length === 0 || !isAnomalous ? (
        <div className="flex-1 flex flex-col items-center justify-center p-6 text-center border border-dashed border-sentinel-700/60 rounded-lg">
          <div className="w-10 h-10 rounded-full bg-sentinel-success/10 text-sentinel-success flex items-center justify-center text-lg mb-2">
            ✓
          </div>
          <p className="text-sm font-medium text-gray-300">All Features Operating Nominally</p>
          <p className="text-xs text-gray-500 mt-1 max-w-xs">
            Multivariate sensor covariance is balanced. Feature contributions will highlight anomalies when sensor divergence is detected.
          </p>
        </div>
      ) : (
        <div className="flex-1 min-h-[200px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={data}
              layout="vertical"
              margin={{ top: 5, right: 30, left: 40, bottom: 5 }}
            >
              <XAxis
                type="number"
                unit="%"
                stroke="#4b5563"
                tick={{ fontSize: 10, fill: '#9ca3af' }}
                domain={[0, 100]}
              />
              <YAxis
                type="category"
                dataKey="name"
                stroke="#4b5563"
                tick={{ fontSize: 11, fill: '#e2e8f0' }}
                width={120}
              />
              <Tooltip
                formatter={(val) => [`${val}%`, 'Contribution']}
                contentStyle={{
                  backgroundColor: '#0d1530',
                  borderColor: '#1e293b',
                  borderRadius: '0.5rem',
                  fontSize: '0.75rem',
                  color: '#e2e8f0'
                }}
              />
              <Bar dataKey="contribution" radius={[0, 4, 4, 0]}>
                {data.map((entry) => (
                  <Cell
                    key={`cell-${entry.featureKey}`}
                    fill={COLOR_MAP[entry.featureKey] || '#00d4ff'}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
