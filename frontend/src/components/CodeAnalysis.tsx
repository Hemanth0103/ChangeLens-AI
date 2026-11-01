
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
    setLoading(true)
    setError('')
    setResult(null)

    try {
      const response = await fetch('/api/analyze/java', {
        method: 'POST',
        headers: {
          'Content-Type': 'text/plain',
        },
        body: code,
      })

      if (!response.ok) {
        throw new Error(`HTTP error: ${response.status}`)
      }

      const data: AnalysisResult = await response.json()

      setResult(data)

      // Update the Dashboard with the latest analysis count
      onClassesAnalyzed(data.classes?.length ?? 0)
    } catch (err) {
      setError(
        err instanceof Error
          ? `Code analysis failed: ${err.message}`
          : 'Unable to analyze Java code.'
      )
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
        onChange={(event) => setCode(event.target.value)}
        spellCheck={false}
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
          }}
        >
          {loading ? 'Analyzing...' : 'Analyze Java Code'}
        </button>
      </div>

      {error && (
        <p style={{ color: '#f87171', marginTop: '20px' }}>
          {error}
        </p>
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
