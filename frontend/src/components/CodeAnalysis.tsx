
import { useState } from 'react'

type JavaMethod = {
  name: string
  returnType?: string
  parameters?: string[]
}

type JavaClass = {
  name: string
  isInterface?: boolean
  methods: JavaMethod[]
}

type AnalysisResult = {
  packageName: string | null
  imports: string[]
  classes: JavaClass[]
}

type CodeAnalysisProps = {
  onClassesAnalyzed: (count: number) => void
}

const sampleCode = `package com.example;

public class Student {

    private String name;

    public void study() {
        System.out.println("Student is studying");
    }
}`

function CodeAnalysis({ onClassesAnalyzed }: CodeAnalysisProps) {
  const [code, setCode] = useState(sampleCode)
  const [result, setResult] = useState<AnalysisResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const analyzeCode = async () => {
    if (loading) return

    setLoading(true)
    setError('')
    setResult(null)
    onClassesAnalyzed(0)

    try {
      if (!code.trim()) {
        setError('Please enter Java code before analyzing.')
        return
      }

      const response = await fetch('/api/analyze/java', {
        method: 'POST',
        headers: {
          'Content-Type': 'text/plain',
        },
        body: code,
      })

      if (!response.ok) {
        if (response.status === 400) {
          throw new Error(
            'The Java code could not be analyzed. Please check for syntax errors and try again.'
          )
        }

        if (response.status === 404) {
          throw new Error(
            'The analysis service could not be found. Please check the backend configuration.'
          )
        }

        if (response.status >= 500) {
          throw new Error(
            'The analysis server encountered an error. Please try again. If the problem continues, contact the backend developer.'
          )
        }

        throw new Error(
          `The analysis request failed (status ${response.status}). Please try again.`
        )
      }

      const data: unknown = await response.json()

      if (
        typeof data !== 'object' ||
        data === null ||
        !('classes' in data) ||
        !Array.isArray(data.classes) ||
        !('imports' in data) ||
        !Array.isArray(data.imports)
      ) {
        throw new Error(
          'The server returned an unexpected analysis result.'
        )
      }

      const analysisData = data as AnalysisResult

      setResult(analysisData)
      onClassesAnalyzed(analysisData.classes.length)
    } catch (err) {
      if (err instanceof TypeError) {
        setError(
          'Unable to connect to the analysis server. Please check that the backend is running.'
        )
      } else {
        setError(
          err instanceof Error
            ? err.message
            : 'Unable to analyze Java code. Please try again.'
        )
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="code-analysis">
      <h2>Java Code Analysis</h2>

      <p>
        Enter Java source code to analyze classes, imports,
        and methods.
      </p>

      <textarea
        value={code}
        onChange={(event) => {
          setCode(event.target.value)
          setError('')
          setResult(null)
        }}
        spellCheck={false}
        aria-label="Java source code"
        style={{
          width: '100%',
          minHeight: '280px',
          marginTop: '20px',
          padding: '16px',
          background: '#0b1120',
          color: '#e2e8f0',
          border: '1px solid #334155',
          borderRadius: '10px',
          fontFamily: 'monospace',
          fontSize: '14px',
          boxSizing: 'border-box',
        }}
      />

      <div style={{ marginTop: '20px' }}>
        <button
          onClick={analyzeCode}
          disabled={loading || !code.trim()}
          style={{
            padding: '12px 24px',
            background: '#2563eb',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            cursor: loading ? 'wait' : 'pointer',
            opacity: loading || !code.trim() ? 0.65 : 1,
          }}
        >
          {loading ? 'Analyzing...' : 'Analyze Java Code'}
        </button>
      </div>

      {error && (
        <div
          role="alert"
          style={{
            marginTop: '20px',
            padding: '16px',
            background: 'rgba(239, 68, 68, 0.08)',
            border: '1px solid #ef4444',
            borderRadius: '10px',
            color: '#fca5a5',
            textAlign: 'left',
          }}
        >
          <strong>Unable to analyze Java code</strong>

          <p style={{ margin: '8px 0 0' }}>
            {error}
          </p>
        </div>
      )}

      {result && (
        <div style={{ marginTop: '30px' }}>
          <h3>Analysis Results</h3>

          <div
            style={{
              marginTop: '16px',
              padding: '20px',
              background: '#1e293b',
              border: '1px solid #334155',
              borderRadius: '12px',
            }}
          >
            <p>
              <strong>Package:</strong>{' '}
              {result.packageName || 'Default Package'}
            </p>

            <h4>Imports ({result.imports?.length ?? 0})</h4>

            {result.imports?.length ? (
              <ul>
                {result.imports.map((importName, index) => (
                  <li key={index}>{importName}</li>
                ))}
              </ul>
            ) : (
              <p>No imports found.</p>
            )}

            <h4>Classes ({result.classes?.length ?? 0})</h4>

            {result.classes?.length ? (
              result.classes.map((javaClass, index) => (
                <div
                  key={`${javaClass.name}-${index}`}
                  style={{
                    marginTop: '16px',
                    padding: '16px',
                    background: '#0f172a',
                    border: '1px solid #334155',
                    borderRadius: '8px',
                  }}
                >
                  <h4>
                    {javaClass.isInterface ? 'Interface' : 'Class'}:{' '}
                    {javaClass.name}
                  </h4>

                  <p>
                    <strong>Methods:</strong>{' '}
                    {javaClass.methods?.length ?? 0}
                  </p>

                  {javaClass.methods?.length ? (
                    <ul>
                      {javaClass.methods.map((method, methodIndex) => (
                        <li key={methodIndex}>
                          {method.name}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p>No methods found.</p>
                  )}
                </div>
              ))
            ) : (
              <p>No classes found.</p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default CodeAnalysis
