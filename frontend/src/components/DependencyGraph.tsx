
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MarkerType,
  Position,
  Handle,
  ReactFlowProvider,
  useReactFlow,
  type Node,
  type Edge,
  type NodeProps,
} from '@xyflow/react'

import '@xyflow/react/dist/style.css'
import './DependencyGraph.css'

type DependencyGraphProps = {
  dependencies?: Record<string, string[]>
  onDependenciesFound: (count: number) => void
}

type ClassNode = Node<
  { className: string; packageName: string },
  'classNode'
>

const NODE_WIDTH = 300
const HORIZONTAL_GAP = 100
const VERTICAL_GAP = 210

const sampleDependencies: Record<string, string[]> = {
  OrderController: ['OrderService'],
  OrderService: ['PaymentService'],
  PaymentService: [],
}

function ClassNodeComponent({
  data,
  selected,
}: NodeProps<ClassNode>) {
  return (
    <div
      className={`cl-class-node ${selected ? 'is-selected' : ''}`}
      title={
        data.packageName
          ? `${data.packageName}.${data.className}`
          : data.className
      }
    >
      <Handle type="target" position={Position.Top} />

      <span className="cl-node-icon">J</span>

      <div
        className="cl-node-name"
        title={data.className}
      >
        {data.className}
      </div>

      <div
        className="cl-node-package"
        title={data.packageName}
      >
        {data.packageName || 'default package'}
      </div>

      <Handle type="source" position={Position.Bottom} />
    </div>
  )
}

const nodeTypes = {
  classNode: ClassNodeComponent,
}

function shortName(fullName: string): string {
  return fullName.split('.').pop() || fullName
}

function createGraphLayout(
  dependencies: Record<string, string[]>
): {
  nodes: ClassNode[]
  edges: Edge[]
} {
  const allClasses = new Set<string>()

  Object.entries(dependencies).forEach(([source, targets]) => {
    allClasses.add(source)

    targets.forEach((target) => {
      allClasses.add(target)
    })
  })

  const classNames = [...allClasses].sort()

  const levels = new Map<string, number>()
  const visiting = new Set<string>()

  function getLevel(className: string): number {
    if (levels.has(className)) {
      return levels.get(className)!
    }

    if (visiting.has(className)) {
      return 0
    }

    visiting.add(className)

    const incoming = classNames.filter((candidate) =>
      (dependencies[candidate] ?? []).includes(className)
    )

    const parentLevels = incoming
      .filter((candidate) => !visiting.has(candidate))
      .map(getLevel)

    const level = parentLevels.length
      ? Math.max(...parentLevels) + 1
      : 0

    visiting.delete(className)
    levels.set(className, level)

    return level
  }

  classNames.forEach(getLevel)

  const groups = new Map<number, string[]>()

  classNames.forEach((name) => {
    const level = levels.get(name) ?? 0

    groups.set(level, [
      ...(groups.get(level) ?? []),
      name,
    ])
  })

  const nodes: ClassNode[] = []

  for (const [level, names] of groups) {
    const totalWidth =
      names.length * NODE_WIDTH +
      (names.length - 1) * HORIZONTAL_GAP

    names.forEach((name, index) => {
      const lastDot = name.lastIndexOf('.')

      nodes.push({
        id: name,
        type: 'classNode',
        position: {
          x:
            index * (NODE_WIDTH + HORIZONTAL_GAP) -
            totalWidth / 2,
          y: level * VERTICAL_GAP,
        },
        data: {
          className: shortName(name),
          packageName:
            lastDot >= 0
              ? name.slice(0, lastDot)
              : '',
        },
      })
    })
  }

  const edges: Edge[] = Object.entries(
    dependencies
  ).flatMap(([source, targets]) =>
    targets.map((target) => ({
      id: `${source}->${target}`,
      source,
      target,
      type: 'smoothstep',
      animated: true,
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
    }))
  )

  return {
    nodes,
    edges,
  }
}

function GraphCanvas({
  nodes,
  edges,
  selectedClass,
  onSelect,
}: {
  nodes: ClassNode[]
  edges: Edge[]
  selectedClass: string | null
  onSelect: (name: string | null) => void
}) {
  const { fitView } = useReactFlow()

  useEffect(() => {
    if (nodes.length === 0) {
      return
    }

    const frame = requestAnimationFrame(() => {
      fitView({
        padding: 0.25,
        minZoom: 0.35,
        maxZoom: 1.15,
        duration: 300,
      })
    })

    return () => cancelAnimationFrame(frame)
  }, [nodes, fitView])

  const displayNodes = useMemo(
    () =>
      nodes.map((node) => ({
        ...node,
        selected: node.id === selectedClass,
        className:
          selectedClass && node.id !== selectedClass
            ? 'cl-node-muted'
            : '',
      })),
    [nodes, selectedClass]
  )

  const displayEdges = useMemo(
    () =>
      edges.map((edge) => {
        const related = Boolean(
          selectedClass &&
            (edge.source === selectedClass ||
              edge.target === selectedClass)
        )

        return {
          ...edge,
          animated: !selectedClass || related,
          className: related ? 'cl-edge-active' : '',
          style: {
            stroke: selectedClass
              ? related
                ? '#38bdf8'
                : '#334155'
              : '#38bdf8',
            strokeWidth: related ? 3 : 2,
            opacity:
              selectedClass && !related ? 0.25 : 1,
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color:
              selectedClass && !related
                ? '#334155'
                : '#38bdf8',
          },
        }
      }),
    [edges, selectedClass]
  )

  return (
    <ReactFlow
      nodes={displayNodes}
      edges={displayEdges}
      nodeTypes={nodeTypes}
      onNodeClick={(_, node) => onSelect(node.id)}
      onPaneClick={() => onSelect(null)}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable
      fitView
      minZoom={0.35}
      maxZoom={1.5}
      proOptions={{ hideAttribution: true }}
    >
      <Background
        color="#243247"
        gap={22}
        size={1}
      />

      <Controls showInteractive={false} />
    </ReactFlow>
  )
}

function getGraphError(status: number): string {
  if (status === 400) {
    return 'The dependency data could not be processed. Try analyzing the project again.'
  }

  if (status === 413) {
    return 'The dependency graph is too large for the server to process.'
  }

  if (status === 429) {
    return 'Too many requests. Please wait and try again.'
  }

  if (status >= 500) {
    return 'The graph server encountered an error. Please try again later.'
  }

  return `Graph generation failed (HTTP ${status}).`
}

function DependencyGraphContent({
  dependencies,
  onDependenciesFound,
}: DependencyGraphProps) {
  const [nodes, setNodes] = useState<ClassNode[]>([])
  const [edges, setEdges] = useState<Edge[]>([])

  const [selectedClass, setSelectedClass] =
    useState<string | null>(null)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const requestRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  const projectDependencies = useMemo(
    () => dependencies ?? sampleDependencies,
    [dependencies]
  )

  useEffect(() => {
    return () => {
      requestIdRef.current += 1
      requestRef.current?.abort()
      requestRef.current = null
    }
  }, [])

  const directDependencies = selectedClass
    ? projectDependencies[selectedClass] ?? []
    : []

  const directDependents = selectedClass
    ? Object.entries(projectDependencies)
        .filter(([, targets]) =>
          targets.includes(selectedClass)
        )
        .map(([source]) => source)
    : []

  const generateGraph = async () => {
    requestRef.current?.abort()

    const controller = new AbortController()
    requestRef.current = controller

    const requestId = ++requestIdRef.current

    setLoading(true)
    setError('')
    setSelectedClass(null)

    try {
      const response = await fetch('/api/analyze/graph', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(projectDependencies),
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new Error(
          getGraphError(response.status)
        )
      }

      await response.json()

      if (
        controller.signal.aborted ||
        requestId !== requestIdRef.current
      ) {
        return
      }

      const graph = createGraphLayout(
        projectDependencies
      )

      setNodes(graph.nodes)
      setEdges(graph.edges)

      onDependenciesFound(graph.edges.length)
    } catch (err) {
      if (
        controller.signal.aborted ||
        requestId !== requestIdRef.current
      ) {
        return
      }

      setNodes([])
      setEdges([])

      onDependenciesFound(0)

      if (err instanceof TypeError) {
        setError(
          'Cannot connect to the graph server. Check that the backend is running.'
        )
      } else {
        setError(
          err instanceof Error
            ? err.message
            : 'Unable to generate the dependency graph.'
        )
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false)
        requestRef.current = null
      }
    }
  }

  const hasGraph = nodes.length > 0

  return (
    <section className="cl-explorer">
      <div className="cl-explorer-heading">
        <div>
          <span className="cl-eyebrow">
            ARCHITECTURE EXPLORER
          </span>

          <h2>Dependency Explorer</h2>

          <p>
            Explore your Java project's class relationships.
          </p>
        </div>

        <button
          type="button"
          className="cl-generate-button"
          onClick={generateGraph}
          disabled={loading}
        >
          {loading
            ? 'Generating...'
            : 'Generate Graph'}
        </button>
      </div>

      <div className="cl-explorer-stats">
        <div className="cl-explorer-stat">
          <span>Classes analyzed</span>
          <strong>{nodes.length}</strong>
        </div>

        <div className="cl-explorer-stat">
          <span>Dependencies mapped</span>
          <strong>{edges.length}</strong>
        </div>

        <div className="cl-explorer-stat">
          <span>Data source</span>

          <strong className="cl-stat-source">
            {dependencies
              ? 'Uploaded project'
              : 'Sample project'}
          </strong>
        </div>
      </div>

      {!dependencies && (
        <p
          className="cl-explorer-notice"
          role="status"
        >
          You're viewing sample dependency data.
          Upload and analyze a Java project to generate
          its dependency graph.
        </p>
      )}

      {error && (
        <p
          className="cl-explorer-error"
          role="alert"
        >
          {error}
        </p>
      )}

      <div className="cl-explorer-layout">
        <div className="cl-graph-panel">
          <div className="cl-panel-header">
            <span>CLASS RELATIONSHIPS</span>
            <span>Click any class to inspect</span>
          </div>

          <div className="cl-graph-canvas">
            <ReactFlowProvider>
              <GraphCanvas
                nodes={nodes}
                edges={edges}
                selectedClass={selectedClass}
                onSelect={setSelectedClass}
              />
            </ReactFlowProvider>

            {!hasGraph && !loading && (
              <div
                className="cl-graph-empty"
                role="status"
              >
                <div className="cl-graph-empty-icon">
                  ◎
                </div>

                <h3>No graph generated yet</h3>

                <p>
                  Click Generate Graph to visualize
                  the class dependencies.
                </p>
              </div>
            )}

            {loading && (
              <div
                className="cl-graph-empty"
                role="status"
              >
                <h3>
                  Generating dependency graph...
                </h3>

                <p>
                  Please wait while the graph is prepared.
                </p>
              </div>
            )}
          </div>
        </div>

        <aside className="cl-details-panel">
          {selectedClass ? (
            <>
              <span className="cl-eyebrow">
                SELECTED CLASS
              </span>

              <h3 title={shortName(selectedClass)}>
                {shortName(selectedClass)}
              </h3>

              <p
                className="cl-full-name"
                title={selectedClass}
              >
                {selectedClass}
              </p>

              <div className="cl-detail-divider" />

              <div className="cl-detail-section">
                <h4>
                  Depends on
                  <span>
                    {directDependencies.length}
                  </span>
                </h4>

                {directDependencies.length ? (
                  directDependencies.map((name) => (
                    <div
                      className="cl-relationship"
                      key={name}
                    >
                      <span className="cl-relationship-dot" />

                      <span title={name}>
                        {shortName(name)}
                      </span>
                    </div>
                  ))
                ) : (
                  <p className="cl-empty-message">
                    No direct dependencies
                  </p>
                )}
              </div>

              <div className="cl-detail-section">
                <h4>
                  Used by
                  <span>
                    {directDependents.length}
                  </span>
                </h4>

                {directDependents.length ? (
                  directDependents.map((name) => (
                    <div
                      className="cl-relationship"
                      key={name}
                    >
                      <span
                        className="cl-relationship-dot yellow"
                      />

                      <span title={name}>
                        {shortName(name)}
                      </span>
                    </div>
                  ))
                ) : (
                  <p className="cl-empty-message">
                    No direct dependents
                  </p>
                )}
              </div>

              <button
                type="button"
                className="cl-clear-button"
                onClick={() =>
                  setSelectedClass(null)
                }
              >
                Clear selection
              </button>
            </>
          ) : (
            <div className="cl-details-placeholder">
              <div className="cl-placeholder-icon">
                ◎
              </div>

              <h3>Class Inspector</h3>

              <p>
                Select a class in the graph to inspect
                its dependencies and dependents.
              </p>
            </div>
          )}
        </aside>
      </div>
    </section>
  )
}

function DependencyGraph(
  props: DependencyGraphProps
) {
  const signature = JSON.stringify(
    props.dependencies ?? sampleDependencies
  )

  return (
    <DependencyGraphContent
      key={signature}
      {...props}
    />
  )
}

export default DependencyGraph
