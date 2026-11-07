
import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import './ImpactAnalysis.css'

type Dependencies = Record<string, string[]>

type ImpactAnalysisProps = {
  dependencies?: Dependencies
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

type AiImpactResponse = {
  analysis?: ImpactResult
  aiExplanation?: string
  error?: string
}

const sampleDependencies: Dependencies = {
  OrderController: ['OrderService'],
  OrderService: ['PaymentService'],
  PaymentService: [],
}

function calculateImpactDetails(
  changedClass: string,
  dependencies: Dependencies
): ImpactDetail[] {
  const reverse: Dependencies = {}

  for (const [source, targets] of Object.entries(dependencies)) {
    for (const target of targets) {
      if (!reverse[target]) reverse[target] = []
      if (!reverse[target].includes(source)) {
        reverse[target].push(source)
      }
    }
  }

  const visited = new Set([changedClass])
  const queue: ImpactDetail[] = [
    {
      className: changedClass,
      level: 0,
      path: [changedClass],
    },
  ]

  const results: ImpactDetail[] = []

  for (let index = 0; index < queue.length; index++) {
    const current = queue[index]

    for (const dependent of reverse[current.className] ?? []) {
      if (visited.has(dependent)) continue

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

function MetricCard({
  label,
  value,
  description,
}: {
  label: string
  value: string | number
  description?: string
}) {
  return (
    <div className="impact-metric">
      <span className="impact-metric-label">{label}</span>
      <strong className="impact-metric-value">{value}</strong>

      {description && (
        <span className="impact-metric-description">
          {description}
        </span>
      )}
    </div>
  )
}

function ImpactAnalysis({
  dependencies,
  onImpactedClasses,
}: ImpactAnalysisProps) {
  const [selectedClass, setSelectedClass] = useState('')
  const [result, setResult] = useState<ImpactResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const [aiExplanation, setAiExplanation] = useState('')
  const [aiLoading, setAiLoading] = useState(false)
  const [aiError, setAiError] = useState('')

  const activeDependencies = dependencies ?? sampleDependencies

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
    setAiExplanation('')
    setAiError('')
    onImpactedClasses(0)
  }, [dependencies, onImpactedClasses])

  const resetAnalysis = () => {
    setResult(null)
    setError('')
    setAiExplanation('')
    setAiError('')
    onImpactedClasses(0)
  }

  const analyzeImpact = async () => {
    if (!selectedClass) {
      setError('Please select a Java class.')
      return
    }

    setLoading(true)
    resetAnalysis()

    try {
      const response = await fetch('/api/analyze/impact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
        !Array.isArray(data.impactedClasses) ||
        typeof data.totalImpacted !== 'number' ||
        typeof data.riskScore !== 'number' ||
        !Number.isFinite(data.riskScore) ||
        !['LOW', 'MEDIUM', 'HIGH'].includes(data.riskLevel)
      ) {
        throw new Error('Invalid impact analysis response.')
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

  const generateAiExplanation = async () => {
    if (!result) return

    setAiLoading(true)
    setAiError('')
    setAiExplanation('')

    try {
      const response = await fetch('/api/ai/impact-explanation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          changedClass: result.changedClass,
          dependencies: activeDependencies,
        }),
      })

      const data: AiImpactResponse = await response.json()

      if (!response.ok) {
        throw new Error(data.error || `HTTP ${response.status}`)
      }

      if (
        !data.aiExplanation ||
        typeof data.aiExplanation !== 'string'
      ) {
        throw new Error('AI returned an empty explanation.')
      }

      setAiExplanation(data.aiExplanation)
    } catch (err) {
      setAiError(
        err instanceof Error
          ? err.message
          : 'Unable to generate AI explanation.'
      )
    } finally {
      setAiLoading(false)
    }
  }

  const impactDetails = result
    ? calculateImpactDetails(
        result.changedClass,
        activeDependencies
      ).filter(detail =>
        result.impactedClasses.includes(detail.className)
      )
    : []

  const riskLevel = result?.riskLevel ?? 'LOW'

  const riskScore = result
    ? Math.max(0, Math.min(100, result.riskScore))
    : 0

  return (
    <section className="impact-dashboard">
      <div className="impact-heading">
        <div>
          <span className="impact-eyebrow">
            CHANGE INTELLIGENCE
          </span>

          <h2>Change Impact Analysis</h2>

          <p>
            Understand how changes to a Java class may affect
            other classes across your project.
          </p>
        </div>

        <span className="impact-source">
          {dependencies ? 'Uploaded Project' : 'Sample Project'}
        </span>
      </div>

      <div className="impact-panel impact-selector-panel">
        <div className="impact-section-heading">
          <div>
            <h3>Select a Java Class</h3>
            <p>Choose the class you plan to modify.</p>
          </div>

          <span className="impact-class-count">
            {classNames.length} classes available
          </span>
        </div>

        <div className="impact-selector-row">
          <select
            id="changed-class"
            aria-label="Select Java class to analyze"
            value={selectedClass}
            disabled={loading || aiLoading}
            onChange={event => {
              setSelectedClass(event.target.value)
              resetAnalysis()
            }}
          >
            <option value="">Select a Java class...</option>

            {classNames.map(className => (
              <option key={className} value={className}>
                {className}
              </option>
            ))}
          </select>

          <button
            type="button"
            className="impact-button impact-button-primary"
            onClick={analyzeImpact}
            disabled={!selectedClass || loading || aiLoading}
          >
            {loading ? 'Analyzing...' : 'Analyze Impact'}
          </button>
        </div>

        <p className="impact-hint">
          Analysis identifies potential downstream effects
          using class dependency relationships.
        </p>
      </div>

      {error && (
        <div className="impact-alert" role="alert">
          {error}
        </div>
      )}

      {!result && !loading && (
        <div className="impact-empty">
          <div className="impact-empty-icon">◎</div>

          <h3>Ready to analyze your code</h3>

          <p>
            Select a class and run an impact analysis to see
            affected classes, dependency paths, and risk metrics.
          </p>
        </div>
      )}

      {loading && (
        <div className="impact-empty" role="status">
          <h3>Analyzing dependencies...</h3>
          <p>Calculating affected classes and structural risk.</p>
        </div>
      )}

      {result && (
        <>
          <div className="impact-result-header">
            <div>
              <span className="impact-eyebrow">
                ANALYSIS RESULTS
              </span>

              <h3>{result.changedClass}</h3>

              <p>
                Selected class for change-impact assessment
              </p>
            </div>

            <button
              type="button"
              className="impact-button impact-button-secondary"
              onClick={() => {
                setSelectedClass('')
                resetAnalysis()
              }}
            >
              Reset Analysis
            </button>
          </div>

          <div className="impact-metrics-grid">
            <MetricCard
              label="Total Impacted"
              value={result.totalImpacted}
              description="Potentially affected classes"
            />

            <MetricCard
              label="Direct Impact"
              value={result.directImpact}
              description="Immediate dependents"
            />

            <MetricCard
              label="Indirect Impact"
              value={result.indirectImpact}
              description="Transitive dependents"
            />

            <MetricCard
              label="Maximum Depth"
              value={result.maxDepth}
              description="Dependency propagation steps"
            />
          </div>

          <div className="impact-risk-grid">
            <div className="impact-panel">
              <div className="impact-section-heading">
                <div>
                  <h3>Structural Risk Assessment</h3>
                  <p>Dependency exposure heuristic</p>
                </div>

                <span
                  className={`impact-risk-badge ${riskLevel.toLowerCase()}`}
                >
                  {riskLevel} RISK
                </span>
              </div>

              <div className="impact-risk-score">
                <strong>{result.riskScore}</strong>
                <span>/ 100</span>
              </div>

              <div
                className="impact-risk-track"
                role="progressbar"
                aria-label="Structural risk score"
                aria-valuenow={riskScore}
                aria-valuemin={0}
                aria-valuemax={100}
              >
                <div
                  className={`impact-risk-fill ${riskLevel.toLowerCase()}`}
                  style={{ width: `${riskScore}%` }}
                />
              </div>

              <p className="impact-risk-note">
                This score measures structural dependency exposure.
                It does not predict the probability of a bug.
              </p>
            </div>

            <div className="impact-panel impact-reach-panel">
              <h3>Project Reach</h3>

              <p>
                Share of project classes potentially affected
              </p>

              <div className="impact-reach-value">
                {result.projectReach.toFixed(2)}%
              </div>

              <div className="impact-reach-track">
                <div
                  style={{
                    width: `${Math.max(
                      0,
                      Math.min(100, result.projectReach)
                    )}%`,
                  }}
                />
              </div>

              <p className="impact-risk-note">
                Based on the class dependency graph provided
                to the analysis engine.
              </p>
            </div>
          </div>

          <div className="impact-panel">
            <div className="impact-section-heading">
              <div>
                <h3>Potentially Affected Classes</h3>

                <p>
                  Explore the dependency paths that propagate
                  changes through your project.
                </p>
              </div>

              <span className="impact-class-count">
                {result.impactedClasses.length} classes
              </span>
            </div>

            {result.impactedClasses.length === 0 ? (
              <div className="impact-no-results">
                No other classes were reported as depending
                on this class.
              </div>
            ) : (
              <div className="impact-path-list">
                {impactDetails.map(detail => (
                  <div
                    className="impact-path-card"
                    key={detail.className}
                  >
                    <div className="impact-path-top">
                      <strong>{detail.className}</strong>

                      <span
                        className={
                          detail.level === 1
                            ? 'impact-type direct'
                            : 'impact-type indirect'
                        }
                      >
                        {detail.level === 1
                          ? 'Direct Impact'
                          : 'Indirect Impact'}
                      </span>
                    </div>

                    <p>
                      Dependency distance: {detail.level}{' '}
                      {detail.level === 1 ? 'step' : 'steps'}
                    </p>

                    <div className="impact-path">
                      <span>Impact path</span>

                      <div>
                        {detail.path.join(' → ')}
                      </div>
                    </div>

                    <p className="impact-path-note">
                      {detail.level === 1
                        ? 'This class directly depends on the changed class and may require testing.'
                        : 'This class depends on another potentially affected class and may require regression testing.'}
                    </p>
                  </div>
                ))}
              </div>
            )}

            {impactDetails.length !==
              result.impactedClasses.length && (
              <p className="impact-warning">
                Some backend-reported impacts could not be
                matched to a dependency path.
              </p>
            )}

            <p className="impact-disclaimer">
              These are potential dependency-based impacts,
              not confirmed software defects.
            </p>
          </div>

          <div className="impact-panel impact-ai-panel">
            <div className="impact-section-heading">
              <div>
                <span className="impact-eyebrow">
                  AI ASSISTANT
                </span>

                <h3>AI-Powered Impact Explanation</h3>

                <p>
                  Generate an explanation of dependency impact,
                  structural risk, and recommended regression tests.
                </p>
              </div>
            </div>

            <button
              type="button"
              className="impact-button impact-button-ai"
              onClick={generateAiExplanation}
              disabled={aiLoading || loading}
            >
              {aiLoading
                ? 'Generating Explanation...'
                : 'Generate AI Explanation'}
            </button>

            {aiLoading && (
              <p className="impact-ai-status" role="status">
                Ollama is analyzing the dependency information.
                This may take a few seconds.
              </p>
            )}

            {aiError && (
              <div className="impact-alert" role="alert">
                AI request failed: {aiError}
              </div>
            )}

            {aiExplanation && (
              <div className="impact-ai-response">
                <h4>
                  AI Analysis and Testing Recommendations
                </h4>

                <div className="impact-markdown">
                  <ReactMarkdown>
                    {aiExplanation}
                  </ReactMarkdown>
                </div>

                <p className="impact-disclaimer">
                  AI-generated suggestions may be inaccurate.
                  Verify recommendations against the source
                  code and existing tests.
                </p>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}

export default ImpactAnalysis
