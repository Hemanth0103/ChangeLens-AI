
import { useState, type ChangeEvent } from 'react'

export type AnalyzedFile = {
  fileName: string
  classes: string[]
  packageName?: string
}

export type ProjectAnalysisResult = {
  totalFiles: number
  totalClasses: number
  totalDependencies: number
  files: AnalyzedFile[]
  dependencies: Record<string, string[]>
  classMethods: Record<string, string[]>
  errors: string[]
}

type ProjectUploadProps = {
  selectedFiles: File[]
  onSelectedFilesChange: (files: File[]) => void
  result: ProjectAnalysisResult | null
  onAnalysisComplete: (result: ProjectAnalysisResult) => void
}

function ProjectUpload({
  selectedFiles,
  onSelectedFilesChange,
  result,
  onAnalysisComplete,
}: ProjectUploadProps) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selectionMessage, setSelectionMessage] = useState('')

  const handleFileChange = (
    event: ChangeEvent<HTMLInputElement>
  ) => {
    const files = Array.from(event.target.files ?? [])

    const javaFiles = files.filter((file) =>
      file.name.toLowerCase().endsWith('.java')
    )

    onSelectedFilesChange(javaFiles)
    setError('')

    setSelectionMessage(
      javaFiles.length > 0
        ? `${javaFiles.length} Java file(s) selected.`
        : 'No Java files were found in your selection.'
    )

    event.target.value = ''
  }

  const handleFolderChange = (
    event: ChangeEvent<HTMLInputElement>
  ) => {
    const files = Array.from(event.target.files ?? [])

    const javaFiles = files.filter((file) =>
      file.name.toLowerCase().endsWith('.java')
    )

    onSelectedFilesChange(javaFiles)
    setError('')

    const ignoredFiles = files.length - javaFiles.length

    setSelectionMessage(
      `${javaFiles.length} Java file(s) found. ` +
        `${ignoredFiles} non-Java file(s) ignored.`
    )

    event.target.value = ''
  }

  const analyzeProject = async () => {
    if (selectedFiles.length === 0) {
      setError('Please select at least one Java file.')
      return
    }

    setLoading(true)
    setError('')

    const formData = new FormData()

    selectedFiles.forEach((file) => {
      formData.append('files', file, file.name)
    })

    try {
      const response = await fetch('/api/projects/analyze', {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data: ProjectAnalysisResult =
        await response.json()

      onAnalysisComplete(data)
    } catch (err) {
      setError(
        err instanceof Error
          ? `Project analysis failed: ${err.message}`
          : 'Unable to analyze the selected files.'
      )
    } finally {
      setLoading(false)
    }
  }

  const dependencyEntries = result
    ? Object.entries(result.dependencies).flatMap(
        ([source, targets]) =>
          targets.map((target) => ({
            source,
            target,
          }))
      )
    : []

  return (
    <div className="project-upload">
      <h2>Java Project Analysis</h2>

      <p>
        Upload individual Java source files or select an
        entire project folder to analyze its classes and
        dependencies.
      </p>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns:
            'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '16px',
          marginTop: '24px',
        }}
      >
        <div
          style={{
            padding: '24px',
            border: '1px dashed #475569',
            borderRadius: '12px',
            background: '#0b1120',
          }}
        >
          <label
            htmlFor="java-files"
            style={{
              display: 'block',
              marginBottom: '12px',
              fontWeight: 600,
            }}
          >
            📄 Choose Java Files
          </label>

          <input
            id="java-files"
            type="file"
            accept=".java"
            multiple
            onChange={handleFileChange}
            disabled={loading}
            style={{
              width: '100%',
              color: '#e2e8f0',
            }}
          />

          <p
            style={{
              marginTop: '12px',
              color: '#94a3b8',
              fontSize: '13px',
            }}
          >
            Select one or more individual .java files.
          </p>
        </div>

        <div
          style={{
            padding: '24px',
            border: '1px dashed #38bdf8',
            borderRadius: '12px',
            background: '#0b1120',
          }}
        >
          <label
            htmlFor="java-folder"
            style={{
              display: 'block',
              marginBottom: '12px',
              fontWeight: 600,
            }}
          >
            📁 Choose Project Folder
          </label>

          <input
            id="java-folder"
            type="file"
            {...({
              webkitdirectory: '',
              directory: '',
            } as React.InputHTMLAttributes<HTMLInputElement>)}
            multiple
            onChange={handleFolderChange}
            disabled={loading}
            style={{
              width: '100%',
              color: '#e2e8f0',
            }}
          />

          <p
            style={{
              marginTop: '12px',
              color: '#94a3b8',
              fontSize: '13px',
            }}
          >
            Select a folder containing Java source files.
            Subfolders are included automatically.
          </p>
        </div>
      </div>

      {selectionMessage && (
        <p
          style={{
            marginTop: '16px',
            color: '#38bdf8',
          }}
        >
          {selectionMessage}
        </p>
      )}

      {selectedFiles.length > 0 && (
        <div style={{ marginTop: '24px' }}>
          <h3>
            Selected Java Files ({selectedFiles.length})
          </h3>

          <div
            style={{
              maxHeight: '300px',
              overflowY: 'auto',
              marginTop: '12px',
            }}
          >
            {selectedFiles.map((file, index) => {
              const relativePath =
                file.webkitRelativePath || file.name

              return (
                <div
                  key={`${relativePath}-${index}`}
                  style={{
                    padding: '10px 14px',
                    marginTop: '8px',
                    background: '#1e293b',
                    borderRadius: '8px',
                    overflowWrap: 'anywhere',
                  }}
                >
                  📄 {relativePath}
                </div>
              )
            })}
          </div>
        </div>
      )}

      <button
        onClick={analyzeProject}
        disabled={loading || selectedFiles.length === 0}
        style={{
          marginTop: '24px',
          padding: '12px 24px',
          background: '#2563eb',
          color: '#ffffff',
          border: 'none',
          borderRadius: '8px',
          cursor: loading ? 'wait' : 'pointer',
          opacity:
            loading || selectedFiles.length === 0
              ? 0.6
              : 1,
        }}
      >
        {loading
          ? 'Analyzing Project...'
          : 'Analyze Project'}
      </button>

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
        <div style={{ marginTop: '30px' }}>
          <h3>Project Analysis Results</h3>

          <div
            style={{
              background: '#1e293b',
              border: '1px solid #334155',
              borderRadius: '12px',
              padding: '20px',
              marginTop: '16px',
            }}
          >
            <p>
              Total Files Analyzed:{' '}
              <strong>{result.totalFiles}</strong>
            </p>

            <p>
              Total Classes Found:{' '}
              <strong>{result.totalClasses}</strong>
            </p>

            <p>
              Total Dependencies Found:{' '}
              <strong>{result.totalDependencies}</strong>
            </p>

            <h4>Analyzed Java Files</h4>

            {result.files.map((file, index) => (
              <div
                key={`${file.fileName}-${index}`}
                style={{
                  marginTop: '12px',
                  padding: '14px',
                  background: '#0f172a',
                  borderRadius: '8px',
                  overflowWrap: 'anywhere',
                }}
              >
                <strong>{file.fileName}</strong>

                {file.packageName && (
                  <p
                    style={{
                      color: '#94a3b8',
                      fontSize: '13px',
                    }}
                  >
                    Package: {file.packageName}
                  </p>
                )}

                <p>
                  Classes:{' '}
                  {file.classes.length > 0
                    ? file.classes.join(', ')
                    : 'No classes found'}
                </p>
              </div>
            ))}

            <h4 style={{ marginTop: '24px' }}>
              Detected Dependencies
            </h4>

            {dependencyEntries.length === 0 ? (
              <p>
                No dependencies found between uploaded
                classes.
              </p>
            ) : (
              dependencyEntries.map(
                ({ source, target }, index) => (
                  <div
                    key={`${source}-${target}-${index}`}
                    style={{
                      marginTop: '10px',
                      padding: '14px',
                      background: '#0f172a',
                      border: '1px solid #334155',
                      borderRadius: '8px',
                      overflowWrap: 'anywhere',
                    }}
                  >
                    <strong>{source}</strong>

                    <span
                      style={{
                        color: '#38bdf8',
                        margin: '0 12px',
                      }}
                    >
                      →
                    </span>

                    <strong>{target}</strong>
                  </div>
                )
              )
            )}

            {result.errors.length > 0 && (
              <div style={{ marginTop: '20px' }}>
                <h4 style={{ color: '#f87171' }}>
                  Analysis Warnings
                </h4>

                {result.errors.map((message, index) => (
                  <p
                    key={index}
                    style={{ color: '#fca5a5' }}
                  >
                    {message}
                  </p>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default ProjectUpload
