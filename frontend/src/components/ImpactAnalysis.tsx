
import { useEffect, useState } from 'react'

type ImpactAnalysisProps = {
  dependencies?: Record<string, string[]>
  onImpactedClasses: (count: number) => void
}

type ImpactResult = {
  changedClass: string
  impactedClasses: string[]
  totalImpacted: number
  directImpact: number
  indirectImpact: number
  maxDepth: number
  projectReach: number
  riskScore: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
}

type ImpactDetail = {
  className: string
  level: number
  path: string[]
}

const sampleDependencies: Record<string, string[]> = {
  OrderController: ['OrderService'],
  OrderService: ['PaymentService'],
  PaymentService: [],
}

function calculateImpactDetails(
  changedClass: string,
  dependencies: Record<string, string[]>
): ImpactDetail[] {
  const reverseDependencies: Record<string, string[]> = {}

  for (const [source, targets] of Object.entries(dependencies)) {
    for (const target of targets) {
      if (!reverseDependencies[target]) {
        reverseDependencies[target] = []
      }

      if (!reverseDependencies[target].includes(source)) {
        reverseDependencies[target].push(source)
      }
    }
  }

  const visited = new Set<string>([changedClass])
  const queue: ImpactDetail[] = [
    {
      className: changedClass,
      level: 0,
      path: [changedClass],
    },
  ]
  const results: ImpactDetail[] = []
  let index = 0

  while (index < queue.length) {
    const current = queue[index]
    index++

    const dependents =
      reverseDependencies[current.className] ?? []

    for (const dependent of dependents) {
      if (visited.has(dependent)) {
        continue
      }

      visited.add(dependent)

      const detail: ImpactDetail = {
        className: dependent,
        level: current.level + 1,
        path: [...current.path, dependent],
      }

      results.push(detail)
      queue.push(detail)
    }
  }

  return results.sort(
    (a, b) =>
      a.level - b.level ||
      a.className.localeCompare(b.className)
  )
}

const riskColors = {
  LOW: '#22c55e',
  MEDIUM: '#f59e0b',
  HIGH: '#ef4444',
}

function ImpactAnalysis({
  dependencies,
  onImpactedClasses,
}: ImpactAnalysisProps) {
  const [selectedClass, setSelectedClass] = useState('')
  const [result, setResult] = useState<ImpactResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const activeDependencies =
    dependencies ?? sampleDependencies

  const classNames = Array.from(
    new Set([
      ...Object.keys(activeDependencies),
      ...Object.values(activeDependencies).flat(),
    ])
  ).sort()

  useEffect(() => {
    setSelectedClass('')
    setResult(null)
    setError('')
  }, [dependencies])

  const analyzeImpact = async () => {
    if (!selectedClass) {
      setError('Please select a Java class.')
      return
    }

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const response = await fetch('/api/analyze/impact', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          changedClass: selectedClass,
          dependencies: activeDependencies,
        }),
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data: ImpactResult = await response.json()

      if (
        typeof data.riskScore !== 'number' ||
        !['LOW', 'MEDIUM', 'HIGH'].includes(data.riskLevel)
      ) {
        throw new Error(
          'Backend response does not include valid risk scoring.'
        )
      }

      setResult(data)
      onImpactedClasses(data.totalImpacted)
    } catch (err) {
      setError(
        err instanceof Error
          ? `Impact analysis failed: ${err.message}`
          : 'Unable to analyze change impact.'
      )
    } finally {
      setLoading(false)
    }
  }

  const impactDetails = result
    ? calculateImpactDetails(
        result.changedClass,
        activeDependencies
      ).filter((detail) =>
        result.impactedClasses.includes(detail.className)
      )
    : []

  const directImpacts = impactDetails.filter(
    (detail) => detail.level === 1
  )

  const indirectImpacts = impactDetails.filter(
    (detail) => detail.level > 1
  )

  const riskColor = result
    ? riskColors[result.riskLevel]
    : '#94a3b8'

  const metricGridStyle = {
    display: 'grid',
    gridTemplateColumns:
      'repeat(auto-fit, minmax(150px, 1fr))',
    gap: '12px',
    marginTop: '20px',
  }

  const metricCardStyle = {
    padding: '16px',
    background: '#0f172a',
    border: '1px solid #334155',
    borderRadius: '10px',
  }

  return (
    <div>
      <h2>Java Change Impact Analysis</h2>

      <p>
        Discover which Java classes may be affected by
        a code change and understand how the impact
        propagates through dependencies.
      </p>

      <p
        style={{
          color: '#94a3b8',
          marginTop: '12px',
        }}
      >
        {dependencies
          ? 'Using classes from your uploaded Java project.'
          : 'No uploaded project found. Using sample classes.'}
      </p>

      <div
        style={{
          marginTop: '24px',
          padding: '24px',
          background: '#0b1120',
          border: '1px solid #334155',
          borderRadius: '12px',
        }}
      >
        <label
          htmlFor="changed-class"
          style={{
            display: 'block',
            marginBottom: '12px',
            fontWeight: 600,
          }}
        >
          Select the Class You Want to Change
        </label>

        <select
          id="changed-class"
          value={selectedClass}
          onChange={(event) => {
            setSelectedClass(event.target.value)
            setResult(null)
            setError('')
            onImpactedClasses(0)
          }}
          style={{
            width: '100%',
            padding: '12px',
            background: '#1e293b',
            color: '#f8fafc',
            border: '1px solid #475569',
            borderRadius: '8px',
          }}
        >
          <option value="">-- Select Java Class --</option>

          {classNames.map((className) => (
            <option key={className} value={className}>
              {className}
            </option>
          ))}
        </select>

        <button
          onClick={analyzeImpact}
          disabled={loading || !selectedClass}
          style={{
            marginTop: '20px',
            padding: '12px 24px',
            background: '#2563eb',
            color: '#ffffff',
            border: 'none',
            borderRadius: '8px',
            cursor: loading ? 'wait' : 'pointer',
            opacity: loading || !selectedClass ? 0.6 : 1,
          }}
        >
          {loading
            ? 'Analyzing Impact...'
            : 'Generate Impact Analysis'}
        </button>
      </div>

      {error && (
        <p
          style={{
            color: '#f87171',
            marginTop: '20px',
          }}
        >
          {error}
        </p>
      )}

      {result && (
        <div
          style={{
            marginTop: '24px',
            padding: '24px',
            background: '#1e293b',
            border: '1px solid #334155',
            borderRadius: '12px',
          }}
        >
          <h3>Impact Analysis Results</h3>

          <p>
            Changed Class:{' '}
            <strong>{result.changedClass}</strong>
          </p>

          <div style={metricGridStyle}>
            {[
              {
                label: 'Total Impacted',
                value: result.totalImpacted,
              },
              {
                label: 'Direct Impact',
                value: directImpacts.length,
              },
              {
                label: 'Indirect Impact',
                value: indirectImpacts.length,
              },
            ].map((item) => (
              <div key={item.label} style={metricCardStyle}>
                <div
                  style={{
                    color: '#94a3b8',
                    fontSize: '13px',
                  }}
                >
                  {item.label}
                </div>
                <div
                  style={{
                    marginTop: '8px',
                    fontSize: '26px',
                    fontWeight: 700,
                  }}
                >
                  {item.value}
                </div>
              </div>
            ))}
          </div>

          <div
            style={{
              marginTop: '24px',
              padding: '24px',
              background: '#0f172a',
              border: '1px solid #334155',
              borderRadius: '12px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '12px',
              }}
            >
              <h3 style={{ margin: 0 }}>
                Change Risk Assessment
              </h3>

              <span
                style={{
                  color: riskColor,
                  background: `${riskColor}20`,
                  border: `1px solid ${riskColor}`,
                  padding: '6px 12px',
                  borderRadius: '20px',
                  fontSize: '13px',
                  fontWeight: 700,
                }}
              >
                {result.riskLevel} RISK
              </span>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: '10px',
                marginTop: '20px',
              }}
            >
              <strong
                style={{
                  fontSize: '40px',
                  color: riskColor,
                }}
              >
                {result.riskScore}/100
              </strong>

              <span
                style={{
                  color: '#94a3b8',
                  fontSize: '13px',
                }}
              >
                Structural risk score
              </span>
            </div>

            <div
              role="progressbar"
              aria-label="Change risk score"
              aria-valuenow={result.riskScore}
              aria-valuemin={0}
              aria-valuemax={100}
              style={{
                width: '100%',
                height: '12px',
                background: '#334155',
                borderRadius: '8px',
                overflow: 'hidden',
                marginTop: '16px',
              }}
            >
              <div
                style={{
                  width: `${Math.max(
                    0,
                    Math.min(100, result.riskScore)
                  )}%`,
                  height: '100%',
                  background: riskColor,
                  borderRadius: '8px',
                  transition: 'width 0.3s ease',
                }}
              />
            </div>

            <div style={metricGridStyle}>
              {[
                {
                  label: 'Direct Impact',
                  value: result.directImpact,
                },
                {
                  label: 'Indirect Impact',
                  value: result.indirectImpact,
                },
                {
                  label: 'Maximum Depth',
                  value: result.maxDepth,
                },
                {
                  label: 'Project Reach',
                  value: `${result.projectReach.toFixed(2)}%`,
                },
              ].map((item) => (
                <div key={item.label} style={metricCardStyle}>
                  <div
                    style={{
                      color: '#94a3b8',
                      fontSize: '13px',
                    }}
                  >
                    {item.label}
                  </div>

                  <div
                    style={{
                      marginTop: '8px',
                      fontSize: '24px',
                      fontWeight: 700,
                    }}
                  >
                    {item.value}
                  </div>
                </div>
              ))}
            </div>

            <p
              style={{
                marginTop: '20px',
                color: '#94a3b8',
                fontSize: '13px',
                lineHeight: 1.6,
              }}
            >
              This heuristic score measures structural
              dependency exposure. It does not predict
              the probability of a bug or guarantee
              that affected classes will fail.
            </p>
          </div>

          <h4 style={{ marginTop: '24px' }}>
            Potentially Affected Classes
          </h4>

          {result.impactedClasses.length === 0 ? (
            <p>
              No other uploaded classes depend on this class.
            </p>
          ) : (
            impactDetails.map((detail) => (
              <div
                key={detail.className}
                style={{
                  marginTop: '12px',
                  padding: '16px',
                  background: '#0f172a',
                  border: '1px solid #334155',
                  borderRadius: '10px',
                  overflowWrap: 'anywhere',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: '12px',
                    flexWrap: 'wrap',
                  }}
                >
                  <strong>{detail.className}</strong>

                  <span
                    style={{
                      color:
                        detail.level === 1
                          ? '#38bdf8'
                          : '#fbbf24',
                      fontSize: '13px',
                      fontWeight: 600,
                    }}
                  >
                    {detail.level === 1
                      ? 'Direct Impact'
                      : 'Indirect Impact'}
                  </span>
                </div>

                <p
                  style={{
                    color: '#94a3b8',
                    fontSize: '13px',
                    marginTop: '10px',
                  }}
                >
                  Dependency distance: {detail.level}{' '}
                  {detail.level === 1 ? 'step' : 'steps'}
                </p>

                <div
                  style={{
                    marginTop: '12px',
                    padding: '12px',
                    background: '#1e293b',
                    borderRadius: '8px',
                    color: '#cbd5e1',
                    fontSize: '13px',
                    lineHeight: 1.8,
                  }}
                >
                  <strong>Impact path:</strong>
                  <div style={{ marginTop: '6px' }}>
                    {detail.path.join(' → ')}
                  </div>
                </div>

                <p
                  style={{
                    marginTop: '12px',
                    color: '#cbd5e1',
                    fontSize: '13px',
                  }}
                >
                  {detail.level === 1
                    ? `${detail.className} directly depends on the changed class and may require testing.`
                    : `${detail.className} depends on another potentially affected class and may require regression testing.`}
                </p>
              </div>
            ))
          )}

          {impactDetails.length !==
            result.impactedClasses.length && (
            <p
              style={{
                marginTop: '16px',
                color: '#fbbf24',
              }}
            >
              Some backend-reported impacts could not
              be matched to a dependency path.
            </p>
          )}

          <p
            style={{
              marginTop: '20px',
              color: '#94a3b8',
              fontSize: '13px',
            }}
          >
            Note: These results represent potential
            dependency-based impacts, not confirmed bugs.
          </p>
        </div>
      )}
    </div>
  )
}

export default ImpactAnalysis
