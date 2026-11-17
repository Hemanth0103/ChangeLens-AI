
import { useEffect, useRef, useState, type ChangeEvent } from 'react'

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

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) &&
    value.every((item) => typeof item === 'string')
}

function isStringArrayRecord(
  value: unknown
): value is Record<string, string[]> {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    Object.values(value).every(isStringArray)
  )
}

function isProjectAnalysisResult(
  value: unknown
): value is ProjectAnalysisResult {
  if (
    typeof value !== 'object' ||
    value === null ||
    Array.isArray(value)
  ) {
    return false
  }

  const data = value as Record<string, unknown>

  return (
    ['totalFiles', 'totalClasses', 'totalDependencies'].every(
      (key) =>
        typeof data[key] === 'number' &&
        Number.isInteger(data[key]) &&
        (data[key] as number) >= 0
    ) &&
    Array.isArray(data.files) &&
    data.files.every((file: unknown) => {
      if (
        typeof file !== 'object' ||
        file === null ||
        Array.isArray(file)
      ) {
        return false
      }

      const item = file as Record<string, unknown>

      return (
        typeof item.fileName === 'string' &&
        isStringArray(item.classes) &&
        (item.packageName === undefined ||
          typeof item.packageName === 'string')
      )
    }) &&
    isStringArrayRecord(data.dependencies) &&
    isStringArrayRecord(data.classMethods) &&
    isStringArray(data.errors)
  )
}

async function getResponseError(
  response: Response
): Promise<string> {
  if (response.status === 400) {
    return 'The selected Java files could not be analyzed. Check the files and try again.'
  }

  if (response.status === 413) {
    return 'The selected files are too large for the server.'
  }

  if (response.status === 429) {
    return 'Too many requests. Please wait a moment and try again.'
  }

  if (response.status >= 500) {
    return 'The analysis server encountered an error. Please try again later.'
  }

  return `Project analysis failed (HTTP ${response.status}).`
}

function getFileKey(file: File): string {
  return [
    file.webkitRelativePath || file.name,
    file.size,
    file.lastModified,
  ].join('::')
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

  const requestRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  useEffect(() => {
    return () => {
      requestIdRef.current += 1
      requestRef.current?.abort()
      requestRef.current = null
    }
  }, [])

  const cancelPendingRequest = () => {
    requestIdRef.current += 1
    requestRef.current?.abort()
    requestRef.current = null
    setLoading(false)
    setError('')
  }

  const selectFiles = (files: File[], fromFolder: boolean) => {
    const javaFiles = files.filter((file) =>
      file.name.toLowerCase().endsWith('.java')
    )

    const ignoredFiles = files.length - javaFiles.length

    if (javaFiles.length === 0) {
      setSelectionMessage(
        'No Java files were found in this selection. Please choose .java files.'
      )
      return
    }

    cancelPendingRequest()

    // Keep previously selected files instead of replacing them.
    const mergedFiles = [...selectedFiles]
    const existingKeys = new Set(
      mergedFiles.map(getFileKey)
    )

    let addedCount = 0

    for (const file of javaFiles) {
      const key = getFileKey(file)

      if (!existingKeys.has(key)) {
        mergedFiles.push(file)
        existingKeys.add(key)
        addedCount += 1
      }
    }

    onSelectedFilesChange(mergedFiles)

    setSelectionMessage(
      `${addedCount} new Java file(s) added. ` +
      `${mergedFiles.length} total selected. ` +
      (fromFolder
        ? `${ignoredFiles} non-Java file(s) ignored.`
        : '')
    )
  }

  const handleFileChange = (
    event: ChangeEvent<HTMLInputElement>
  ) => {
    const files = Array.from(event.target.files ?? [])

    if (files.length > 0) {
      selectFiles(files, false)
    }

    event.target.value = ''
  }

  const handleFolderChange = (
    event: ChangeEvent<HTMLInputElement>
  ) => {
    const files = Array.from(event.target.files ?? [])

    if (files.length > 0) {
      selectFiles(files, true)
    }

    event.target.value = ''
  }

  const removeFile = (indexToRemove: number) => {
    cancelPendingRequest()

    const updatedFiles = selectedFiles.filter(
      (_, index) => index !== indexToRemove
    )

    onSelectedFilesChange(updatedFiles)

    setSelectionMessage(
      `${updatedFiles.length} Java file(s) selected.`
    )
  }

  const clearSelection = () => {
    cancelPendingRequest()
    onSelectedFilesChange([])
    setSelectionMessage('File selection cleared.')
  }

  const analyzeProject = async () => {
    if (selectedFiles.length === 0) {
      setError('Please select at least one Java file.')
      return
    }

    requestRef.current?.abort()

    const controller = new AbortController()
    requestRef.current = controller

    const requestId = ++requestIdRef.current

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
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new Error(await getResponseError(response))
      }

      const data: unknown = await response.json()

      if (!isProjectAnalysisResult(data)) {
        throw new Error(
          'The server returned an unexpected analysis result. Please try again.'
        )
      }

      if (
        controller.signal.aborted ||
        requestId !== requestIdRef.current
      ) {
        return
      }

      onAnalysisComplete(data)
    } catch (err) {
      if (
        controller.signal.aborted ||
        requestId !== requestIdRef.current
      ) {
        return
      }

      if (err instanceof TypeError) {
        setError(
          'Cannot connect to the analysis server. Check that the backend is running and try again.'
        )
      } else {
        setError(
          err instanceof Error
            ? err.message
            : 'Unable to analyze the selected Java files.'
        )
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false)
        requestRef.current = null
      }
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

  const panelStyle = {
    padding: '24px',
    border: '1px dashed #475569',
    borderRadius: '12px',
    background: '#0b1120',
  }

  const fileStyle = {
    padding: '10px 14px',
    marginTop: '8px',
    background: '#1e293b',
    borderRadius: '8px',
    overflowWrap: 'anywhere' as const,
  }

  return (
    <div className="project-upload">
      <h2>Java Project Analysis</h2>

      <p>
        Upload Java source files from multiple folders or
        select an entire project folder to analyze its
        classes and dependencies.
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
        <div style={panelStyle}>
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
            Choose one or more Java files. You can click
            Choose Files again to add files from another
            folder without losing your selection.
          </p>
        </div>

        <div
          style={{
            ...panelStyle,
            border: '1px dashed #38bdf8',
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
          role="status"
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
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '12px',
              flexWrap: 'wrap',
            }}
          >
            <h3>
              Selected Java Files ({selectedFiles.length})
            </h3>

            <button
              type="button"
              onClick={clearSelection}
              disabled={loading}
              style={{
                padding: '8px 14px',
                background: '#475569',
                color: '#ffffff',
                border: 'none',
                borderRadius: '8px',
                cursor: 'pointer',
              }}
            >
              Clear Selection
            </button>
          </div>

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
                  key={`${getFileKey(file)}-${index}`}
                  style={{
                    ...fileStyle,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '12px',
                  }}
                >
                  <span>
                    📄 {relativePath}
                  </span>

                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    disabled={loading}
                    aria-label={`Remove ${file.name}`}
                    style={{
                      padding: '6px 10px',
                      background: '#7f1d1d',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '6px',
                      cursor: 'pointer',
                    }}
                  >
                    Remove
                  </button>
                </div>
              )
            })}
          </div>
        </div>
      )}

      <button
        type="button"
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
          role="alert"
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
