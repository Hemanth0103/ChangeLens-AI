
import { useState } from 'react'

import AiChat from './components/AiChat'
import CodeAnalysis from './components/CodeAnalysis'
import DependencyGraph from './components/DependencyGraph'
import ImpactAnalysis from './components/ImpactAnalysis'
import ProjectUpload, {
  type ProjectAnalysisResult,
} from './components/ProjectUpload'

import './App.css'

function App() {
  const [activePage, setActivePage] = useState('Dashboard')

  const [selectedFiles, setSelectedFiles] = useState<File[]>([])

  const [projectResult, setProjectResult] =
    useState<ProjectAnalysisResult | null>(null)

  const [classesAnalyzed, setClassesAnalyzed] = useState(0)
  const [dependenciesFound, setDependenciesFound] = useState(0)
  const [impactedClasses, setImpactedClasses] = useState(0)

  const menuItems = [
    'Dashboard',
    'Project Upload',
    'Code Analysis',
    'Dependency Graph',
    'Impact Analysis',
    'AI Chat',
    'Settings',
  ]

  const handleFilesChange = (files: File[]) => {
    setSelectedFiles(files)
    setProjectResult(null)
    setClassesAnalyzed(0)
    setDependenciesFound(0)
    setImpactedClasses(0)
  }

  const handleProjectAnalysis = (result: ProjectAnalysisResult) => {
    setProjectResult(result)
    setClassesAnalyzed(result.totalClasses)
    setDependenciesFound(result.totalDependencies)
    setImpactedClasses(0)
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <h2>⚡ ChangeLens AI</h2>
        <p>Code Intelligence Platform</p>

        <nav>
          {menuItems.map((item) => (
            <button
              key={item}
              className={activePage === item ? 'active' : ''}
              onClick={() => setActivePage(item)}
            >
              {item}
            </button>
          ))}
        </nav>
      </aside>

      <main className="main-content">
        <header>
          <h1>{activePage}</h1>
          <p>Understand code changes before they happen.</p>
        </header>

        {activePage === 'Dashboard' && (
          <>
            <section className="stats">
              <div className="stat-card">
                <h3>Classes Analyzed</h3>
                <h2>{classesAnalyzed}</h2>
              </div>

              <div className="stat-card">
                <h3>Dependencies Found</h3>
                <h2>{dependenciesFound}</h2>
              </div>

              <div className="stat-card">
                <h3>Impacted Classes</h3>
                <h2>{impactedClasses}</h2>
              </div>
            </section>

            <section className="workspace">
              <h2>Dashboard Workspace</h2>

              {projectResult ? (
                <>
                  <p>
                    Current project: {projectResult.totalFiles} files,{' '}
                    {projectResult.totalClasses} classes,{' '}
                    {projectResult.totalDependencies} dependencies.
                  </p>

                  <p>
                    Uploaded files:{' '}
                    {projectResult.files
                      .map((file) => file.fileName)
                      .join(', ')}
                  </p>
                </>
              ) : (
                <p>
                  Upload a Java project to see its analysis here.
                </p>
              )}
            </section>
          </>
        )}

        {activePage === 'Project Upload' && (
          <section className="workspace">
            <ProjectUpload
              selectedFiles={selectedFiles}
              onSelectedFilesChange={handleFilesChange}
              result={projectResult}
              onAnalysisComplete={handleProjectAnalysis}
            />
          </section>
        )}

        {activePage === 'Code Analysis' && (
          <section className="workspace">
            <CodeAnalysis
              onClassesAnalyzed={setClassesAnalyzed}
            />
          </section>
        )}

        {activePage === 'Dependency Graph' && (
          <section className="workspace">
            <DependencyGraph
              dependencies={projectResult?.dependencies}
              onDependenciesFound={setDependenciesFound}
            />
          </section>
        )}

        {activePage === 'Impact Analysis' && (
          <section className="workspace">
            <ImpactAnalysis
              dependencies={projectResult?.dependencies}
              onImpactedClasses={setImpactedClasses}
            />
          </section>
        )}

        {activePage === 'AI Chat' && (
          <section className="workspace">
            <AiChat
              dependencies={projectResult?.dependencies}
              classMethods={projectResult?.classMethods}
            />
          </section>
        )}

        {activePage === 'Settings' && (
          <section className="workspace">
            <h2>Settings Workspace</h2>
            <p>Application settings will appear here.</p>
          </section>
        )}
      </main>
    </div>
  )
}

export default App
