package domain

/**
 * Handles validation logic for a Level, checking for various types of errors
 * in the street network configuration.
 */
class LevelValidator(private val level: Level) {

    /**
     * Validates the level's network integrity.
     *
     * @return A list of [ValidationError] objects. If empty, the level is valid.
     */
    fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val sourceNodes = level.nodes.filterIsInstance<SourceNode>()
        val targetNodes = level.nodes.filterIsInstance<TargetNode>()

        // Perform all validation checks
        validateSourceNodesExist(sourceNodes, errors)
        validateSourceNodeConfigurations(sourceNodes, errors)
        validateTargetNodesExist(targetNodes, errors)
        validateConnectionNodes()?.let { errors.addAll(it) }
        validateSourceConfigurationsPaths(sourceNodes, targetNodes, errors)
        validateNodePositions(errors)

        // Add challenge mode validation errors
        errors += ChallengeMode.validationErrors(level)

        return errors
    }

    /** Validates that at least one source node exists.
     * 
     * @param sourceNodes The source nodes to validate.
     * @param errors The list of validation errors to add to.
     */
    private fun validateSourceNodesExist(
        sourceNodes: List<SourceNode>,
        errors: MutableList<ValidationError>
    ) {
        if (sourceNodes.isEmpty()) {
            errors.add(ValidationError("Level must have at least one SourceNode."))
        }
    }

    /**
     * Validates that each source node has at least one configuration.
     * @param sourceNodes The source nodes to validate.
     * @param errors The list of validation errors to add to.
     */
    private fun validateSourceNodeConfigurations(
        sourceNodes: List<SourceNode>,
        errors: MutableList<ValidationError>
    ) {
        for (sourceNode in sourceNodes) {
            if (sourceNode.configurations.isEmpty()) {
                errors.add(
                    ValidationError(
                        message = "SourceNode has no configurations.",
                        position = sourceNode.position,
                        affectedNodes = listOf(sourceNode),
                        constructionNodes = listOf(sourceNode),
                        constructionPositions = listOf(sourceNode.position)
                    )
                )
            }
        }
    }

    /**
     * Validates that at least one target node exists.
     * @param targetNodes The target nodes to validate.
     * @param errors The list of validation errors to add to.
     */
    private fun validateTargetNodesExist(
        targetNodes: List<TargetNode>,
        errors: MutableList<ValidationError>
    ) {
        if (targetNodes.isEmpty()) {
            errors.add(ValidationError("Level must have at least one TargetNode."))
        }
    }

    /**
     * Validates connection nodes have proper incoming/outgoing street connections.
     * @return List of validation errors for connection nodes, or null if no errors.
     */
    private fun validateConnectionNodes(): List<ValidationError>? {
        val errors = mutableListOf<ValidationError>()
        
        for (node in level.nodes.filterIsInstance<ConnectionNode>()) {
            val totalStreets = node.incomingStreets.size + node.outgoingStreets.size
            if (totalStreets >= 2) {
                validateConnectionNodeDirections(node, totalStreets, errors)
            }
        }
        
        return errors.ifEmpty { null }
    }

    /**
     * Validates a single connection node has both incoming and outgoing streets.
     * @param node The connection node to validate.
     * @param totalStreets The total number of streets connected to the node.
     * @param errors The list of validation errors to add to.
     */
    private fun validateConnectionNodeDirections(
        node: ConnectionNode,
        totalStreets: Int,
        errors: MutableList<ValidationError>
    ) {
        if (node.incomingStreets.isEmpty()) {
            errors.add(
                ValidationError(
                    message = "ConnectionNode has $totalStreets connections but no incoming streets.",
                    position = node.position,
                    affectedNodes = listOf(node),
                    affectedStreets = node.incomingStreets + node.outgoingStreets,
                    constructionNodes = listOf(node),
                    constructionPositions = listOf(node.position)
                )
            )
        }
        if (node.outgoingStreets.isEmpty()) {
            errors.add(
                ValidationError(
                    message = "ConnectionNode has $totalStreets connections but no outgoing streets.",
                    position = node.position,
                    affectedNodes = listOf(node),
                    affectedStreets = node.incomingStreets + node.outgoingStreets,
                    constructionNodes = listOf(node),
                    constructionPositions = listOf(node.position)
                )
            )
        }
    }

    /** Validates that all source node configurations have paths to matching targets.
     * 
     * @param sourceNodes The source nodes to validate.
     * @param targetNodes The target nodes to validate.
     * @param errors The list of validation errors to add to.
     */
    private fun validateSourceConfigurationsPaths(
        sourceNodes: List<SourceNode>,
        targetNodes: List<TargetNode>,
        errors: MutableList<ValidationError>
    ) {
        for (sourceNode in sourceNodes) {
            for (config in sourceNode.configurations) {
                validateSourceConfiguration(sourceNode, config, targetNodes, errors)
            }
        }
    }

    /**
     * Validates a single source node configuration by checking if a path exists to a matching target.
     *
     * @param sourceNode The source node being validated.
     * @param config The configuration to validate.
     * @param targetNodes All target nodes in the level.
     * @param errors The mutable list to add validation errors to.
     */
    private fun validateSourceConfiguration(
        sourceNode: SourceNode,
        config: SourceNodeConfiguration,
        targetNodes: List<TargetNode>,
        errors: MutableList<ValidationError>
    ) {
        // Keep the reachable subgraph so the error can explain where the path currently
        // ends, not only that a matching target is missing.
        val reachability = findReachableNetwork(sourceNode)
        
        // Check if any reachable node is a target with matching color
        val hasMatchingTarget = reachability.nodes.any { 
            it is TargetNode && it.color.name == config.color.name 
        }
        
        if (!hasMatchingTarget) {
            val matchingTargets = targetNodes.filter { it.color.name == config.color.name }
            val markerNodes = missingConnectionNodes(
                sourceNode,
                reachability,
                matchingTargets
            )
            errors.add(
                ValidationError(
                    message = "SourceNode has no path to a target for this configuration. Streets are " +
                        "one-way, so this can also mean a needed street only exists in the opposite " +
                        "direction - check whether a return street is missing.",
                    position = sourceNode.position,
                    color = config.color,
                    affectedNodes = (reachability.nodes + matchingTargets).toList(),
                    affectedStreets = reachability.streets.toList(),
                    constructionNodes = markerNodes,
                    constructionPositions = markerNodes.map { it.position }
                )
            )
        }
    }

    /**
     * Validates that all nodes are positioned within the game area boundaries.
     * @param errors The list of validation errors to add to.
     */
    private fun validateNodePositions(errors: MutableList<ValidationError>) {
        for (node in level.nodes) {
            if (node.position.x !in 0.0..Level.WIDTH || node.position.y < 0 || node.position.y > Level.HEIGHT) {
                errors.add(
                    ValidationError(
                        message = "Node is outside the game area (0, 0) to (${Level.WIDTH}, ${Level.HEIGHT}).",
                        position = node.position,
                        affectedNodes = listOf(node),
                        constructionNodes = listOf(node),
                        constructionPositions = listOf(node.position)
                    )
                )
            }
        }
    }

    /**
     * Result of a graph traversal from one start node.
     *
     * @property nodes all nodes reachable through directed outgoing streets.
     * @property streets all directed streets that were traversed during the search.
     */
    private data class Reachability(
        val nodes: Set<Node>,
        val streets: Set<Street>
    )

    /**
     * Finds the directed subgraph reachable from [startNode].
     *
     * This is used for missing-path validation so the resulting [ValidationError] can include the
     * affected nodes and streets that already belong to the incomplete route.
     *
     * @param startNode The node where the directed traversal starts.
     * @return The reachable nodes and streets from [startNode].
     */
    private fun findReachableNetwork(startNode: Node): Reachability {
        val visited = mutableSetOf<Node>()
        val reachedStreets = mutableSetOf<Street>()
        val queue = ArrayDeque<Node>()

        queue.add(startNode)
        visited.add(startNode)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            for (street in current.outgoingStreets) {
                reachedStreets.add(street)
                val nextNode = street.endNode
                if (nextNode !in visited) {
                    visited.add(nextNode)
                    queue.add(nextNode)
                }
            }
        }

        return Reachability(visited, reachedStreets)
    }

    /**
     * Selects the nodes where construction markers should be rendered for a missing path.
     *
     * The marker is placed at reachable dead ends when possible. If the reachable network has no
     * dead end, the nearest reachable non-target node is used so a missing branch can be shown at
     * an intersection instead of on a wrong-colored target.
     *
     * @param sourceNode The source whose color configuration currently has no complete path.
     * @param reachability The directed subgraph reachable from [sourceNode].
     * @param matchingTargets All target nodes that accept the source configuration color.
     * @return Distinct nodes where the UI should display construction markers.
     */
    private fun missingConnectionNodes(
        sourceNode: SourceNode,
        reachability: Reachability,
        matchingTargets: List<TargetNode>
    ): List<Node> {
        val reachableConnectionPoints = reachability.nodes.filter { it !is TargetNode }
        val deadEnds = reachableConnectionPoints.filter { it.outgoingStreets.isEmpty() }

        val startNodes = when {
            deadEnds.isNotEmpty() -> deadEnds
            matchingTargets.isNotEmpty() -> listOf(
                findNearestNodeToTargets(reachableConnectionPoints, matchingTargets) ?: sourceNode
            )
            else -> listOf(sourceNode)
        }

        return (startNodes + matchingTargets).distinct()
    }

    /**
     * Finds the reachable connection point that is closest to any of the matching targets.
     *
     * @param reachableNodes Nodes that are reachable from the source.
     * @param targets Target nodes to find the closest reachable node to.
     * @return The nearest reachable node, or null if no reachable nodes exist.
     */
    private fun findNearestNodeToTargets(
        reachableNodes: List<Node>,
        targets: List<TargetNode>
    ): Node? {
        if (reachableNodes.isEmpty() || targets.isEmpty()) {
            return null
        }
        
        return reachableNodes.minByOrNull { reachable ->
            targets.minOf { target -> reachable.position.distanceTo(target.position) }
        }
    }
}