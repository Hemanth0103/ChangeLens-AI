
import { useEffect, useState } from 'react'

type ImpactAnalysisProps = {
  dependencies?: Record<string, string[]>
  onImpactedClasses: (count: number) => void
}

type ImpactResult = {
  changedClass: string
  impactedClasses: string[]
  totalImpacted: number
}

const sampleDependencies: Record<string, string[]> = {
  OrderController: ['OrderService'],
  OrderService: ['PaymentService'],
  PaymentService: [],
}

function ImpactAnalysis({
  dependencies,
  onImpactedClasses,
}: ImpactAnalysisProps) {
  const [selectedClass, setSelectedClass] = useState('')
  const [result, setResult] = useState<ImpactResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const activeDependencies = dependencies ?? sampleDependencies

  const classNames = Object.keys(activeDependencies).sort()

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

  return (
    <div>
      <h2>Java Change Impact Analysis</h2>

      <p>
        Identify which Java classes may be affected when
        another class changes.
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
            Changed Class: <strong>{result.changedClass}</strong>
          </p>

          <p>
            Total Impacted Classes:{' '}
            <strong>{result.totalImpacted}</strong>
          </p>

          <h4 style={{ marginTop: '20px' }}>
            Potentially Affected Classes
          </h4>

          {result.impactedClasses.length === 0 ? (
            <p>
              No other uploaded classes depend on this class.
            </p>
          ) : (
            result.impactedClasses.map((className) => (
              <div
                key={className}
                style={{
                  marginTop: '10px',
                  padding: '14px',
                  background: '#0f172a',
                  border: '1px solid #334155',
                  borderRadius: '8px',
                }}
              >
                ⚠️ {className}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}

export default ImpactAnalysis
