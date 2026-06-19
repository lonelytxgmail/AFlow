import dagre from 'dagre'

export interface LayoutNode {
  id: string
  position: { x: number; y: number }
  [key: string]: any
}

export interface LayoutEdge {
  id: string
  source: string
  target: string
  [key: string]: any
}

export type LayoutDirection = 'TB' | 'LR'

interface AutoLayoutOptions {
  direction?: LayoutDirection
  nodeWidth?: number
  nodeHeight?: number
  rankSep?: number
  nodeSep?: number
}

/**
 * Composable that provides dagre-based automatic layout for VueFlow graphs.
 * Takes nodes and edges from VueFlow and computes optimized positions using
 * the dagre directed graph layout algorithm.
 */
export function useAutoLayout() {
  /**
   * Apply dagre auto-layout to a set of nodes and edges.
   * Returns a new array of nodes with updated positions (does not mutate input).
   */
  function applyAutoLayout(
    nodes: LayoutNode[],
    edges: LayoutEdge[],
    options: AutoLayoutOptions = {}
  ): LayoutNode[] {
    const {
      direction = 'TB',
      nodeWidth = 200,
      nodeHeight = 60,
      rankSep = 80,
      nodeSep = 50
    } = options

    const g = new dagre.graphlib.Graph()
    g.setDefaultEdgeLabel(() => ({}))
    g.setGraph({
      rankdir: direction,
      ranksep: rankSep,
      nodesep: nodeSep
    })

    // Add nodes to the dagre graph
    for (const node of nodes) {
      g.setNode(node.id, { width: nodeWidth, height: nodeHeight })
    }

    // Add edges to the dagre graph
    for (const edge of edges) {
      g.setEdge(edge.source, edge.target)
    }

    // Run the layout algorithm
    dagre.layout(g)

    // Map computed positions back to nodes
    return nodes.map((node) => {
      const dagreNode = g.node(node.id)
      if (!dagreNode) return node

      return {
        ...node,
        position: {
          // dagre returns center position; offset to top-left for VueFlow
          x: dagreNode.x - nodeWidth / 2,
          y: dagreNode.y - nodeHeight / 2
        }
      }
    })
  }

  return {
    applyAutoLayout
  }
}
