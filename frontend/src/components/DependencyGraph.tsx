
import { useState } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MarkerType,
  Position,
  Handle,
  type Node,
  type Edge,
  type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

type DependencyGraphProps = {
  dependencies?: Record<string, string[]>
  onDependenciesFound: (count: number) => void
}

type ClassNode = Node<
  { className: string; packageName: string },
  'classNode'
>

function ClassNodeComponent({ data }: NodeProps<ClassNode>) {
  return (
    <div
      style={{
        width: 210,
        padding: '14px 12px',
        background: '#1e293b',
        border: '1px solid #38bdf8',
        borderRadius: 12,
        color: '#f8fafc',
        textAlign: 'center',
        boxSizing: 'border-box',
      }}
    >
      <Handle
        type="target"
        position={Position.Top}
        style={{ background: '#38bdf8' }}
      />

      <div
        style={{
          fontSize: 14,
          fontWeight: 700,
          overflowWrap: 'anywhere',
        }}
      >
        {data.className}
      </div>

      {data.packageName && (
        <div
          title={data.packageName}
          style={{
            marginTop: 6,
            fontSize: 10,
            color: '#94a3b8',
            overflowWrap: 'anywhere',
          }}
        >
          {data.packageName}
        </div>
      )}

      <Handle
        type="source"
        position={Position.Bottom}
        style={{ background: '#38bdf8' }}
      />
    </div>
  )
}

const nodeTypes = {
  classNode: ClassNodeComponent,
}

function splitClassName(fullName: string) {
  const lastDot = fullName.lastIndexOf('.')

  return {
    className: fullName.substring(lastDot + 1),
    packageName:
      lastDot === -1 ? '' : fullName.substring(0, lastDot),
  }
}

function createGraphLayout(
  dependencies: Record<string, string[]>
): { nodes: ClassNode[]; edges: Edge[] } {
  const allClasses = new Set<string>()

  Object.entries(dependencies).forEach(([source, targets]) => {
    allClasses.add(source)
    targets.forEach((target) => allClasses.add(target))
  })

  const classNames = [...allClasses].sort()

  // Place dependency sources above their referenced classes.
  // Cycles are handled by limiting traversal to visited nodes.
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
      .map((candidate) => getLevel(candidate))

    const level =
      parentLevels.length > 0 ? Math.max(...parentLevels) + 1 : 0

    visiting.delete(className)
    levels.set(className, level)

    return level
  }

  classNames.forEach((className) => getLevel(className))

  const groups = new Map<number, string[]>()

  classNames.forEach((className) => {
    const level = levels.get(className) ?? 0
    const group = groups.get(level) ?? []
    group.push(className)
    groups.set(level, group)
  })

  const nodeWidth = 210
  const horizontalGap = 90
  const verticalGap = 190

  const graphNodes: ClassNode[] = []

  for (const [level, names] of groups.entries()) {
    const totalWidth =
      names.length * nodeWidth +
      (names.length - 1) * horizontalGap

    names.forEach((fullName, index) => {
      const { className, packageName } =
        splitClassName(fullName)

      graphNodes.push({
        id: fullName,
        type: 'classNode',
        position: {
          x:
            index * (nodeWidth + horizontalGap) -
            totalWidth / 2 +
            nodeWidth / 2,
          y: level * verticalGap,
        },
        data: { className, packageName },
        sourcePosition: Position.Bottom,
        targetPosition: Position.Top,
      })
    })
  }

  const graphEdges: Edge[] = Object.entries(
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
        color: '#38bdf8',
      },
      style: {
        stroke: '#38bdf8',
        strokeWidth: 2,
      },
    }))
  )

  return { nodes: graphNodes, edges: graphEdges }
}

function DependencyGraph({
  dependencies,
  onDependenciesFound,
}: DependencyGraphProps) {
  const [nodes, setNodes] = useState<ClassNode[]>([])
  const [edges, setEdges] = useState<Edge[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const generateGraph = async () => {
    setLoading(true)
    setError('')

    const projectDependencies = dependencies ?? {
      OrderController: ['OrderService'],
      OrderService: ['PaymentService'],
      PaymentService: [],
    }

    try {
      const response = await fetch('/api/analyze/graph', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(projectDependencies),
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data = await response.json()

      if (!data) {
        throw new Error('No graph data returned')
      }

      const graph = createGraphLayout(projectDependencies)

      setNodes(graph.nodes)
      setEdges(graph.edges)
      onDependenciesFound(graph.edges.length)
    } catch (err) {
      setError(
        err instanceof Error
          ? `Graph generation failed: ${err.message}`
          : 'Unable to generate graph.'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h2>Java Dependency Graph</h2>

      <p>Visualize relationships between Java classes.</p>

      <p
        style={{
          color: '#94a3b8',
          marginTop: 12,
        }}
      >
        {dependencies
          ? 'Using dependencies from your uploaded Java project.'
          : 'No uploaded project found. Using sample dependencies.'}
      </p>

      <p
        style={{
          color: '#64748b',
          fontSize: 12,
          marginTop: 8,
        }}
      >
        Arrows point from a class to the classes it depends on.
      </p>

      <button
        onClick={generateGraph}
        disabled={loading}
        style={{
          marginTop: 20,
          marginBottom: 20,
          padding: '12px 24px',
          background: '#2563eb',
          color: '#ffffff',
          border: 'none',
          borderRadius: 8,
          cursor: loading ? 'wait' : 'pointer',
        }}
      >
        {loading
          ? 'Generating Graph...'
          : 'Generate Dependency Graph'}
      </button>

      {error && (
        <p style={{ color: '#f87171' }}>{error}</p>
      )}

      <div
        style={{
          width: '100%',
          height: 550,
          background: '#0b1120',
          border: '1px solid #334155',
          borderRadius: 12,
          overflow: 'hidden',
        }}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          minZoom={0.2}
          maxZoom={2}
        >
          <Background color="#334155" gap={20} />
          <Controls />
        </ReactFlow>
      </div>
    </div>
  )
}

export default DependencyGraph
