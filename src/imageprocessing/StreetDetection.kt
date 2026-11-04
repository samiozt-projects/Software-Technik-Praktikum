package imageprocessing

import config.ConfigManager
import domain.*
import geometry.simplifyPath
import imageprocessing.CameraManager.saveDebugImage
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.opencv.global.opencv_core.add
import org.bytedeco.opencv.global.opencv_core.CV_32FC2
import org.bytedeco.opencv.global.opencv_core.inRange
import org.bytedeco.opencv.global.opencv_core.rotate
import org.bytedeco.opencv.global.opencv_core.ROTATE_180
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.global.opencv_ximgproc.THINNING_ZHANGSUEN
import org.bytedeco.opencv.global.opencv_ximgproc.thinning
import org.bytedeco.opencv.opencv_core.*
import org.bytedeco.opencv.opencv_core.Size
import rendering.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.math.*
import org.bytedeco.opencv.opencv_core.Point as OpenCVPoint

// These dimensions are loaded from configuration to effectively connect broken street lines
// by bridging gaps that are typically wider than they are tall, based on the expected appearance of streets.
private val KERNEL_RED_WIDTH: Int get() = config.kernelRedWidth
private val KERNEL_RED_HEIGHT: Int get() = config.kernelRedHeight

// Epsilon value for approximating polylines (loaded from configuration).
private val POLYLINE_APPROX_EPSILON: Double get() = config.polylineApproxEpsilon

// Minimum arc length for a contour to be considered a valid polyline (loaded from configuration).
private val MIN_POLYLINE_ARC_LENGTH: Double get() = config.polylineMinArcLength

// Threshold value used for binarizing the grayscale image to detect the table (loaded from configuration).
private val TABLE_THRESHOLD_VALUE: Double get() = config.tableThreshold

// Physical/projected table ratio: 90 cm x 50 cm.
private const val TABLE_ASPECT_RATIO = 9.0 / 5.0

// A detected table contour is only trusted when its bounding box covers at least this fraction of the
// frame. Smaller detections (reflections, glare blobs) are discarded so the full frame is kept instead
// of cropping away real street pixels.
private const val MIN_TABLE_AREA_FRACTION = 0.30

// Allowed multiplicative deviation of a detected table rectangle's aspect ratio from TABLE_ASPECT_RATIO
// before the detection is treated as unreliable.
private const val MAX_TABLE_ASPECT_DEVIATION = 1.5

// Corners of the projected play area (the level/projection rectangle) as the camera sees it in the
// raw frame (CAMERA_WIDTH x CAMERA_HEIGHT), in top-left, top-right, bottom-right, bottom-left order.
// preCrop applies a perspective warp that maps this quad onto the full desk frame
// (GameAreaScale.DESK_W_PX x DESK_H_PX) so detector pixels share the same coordinate system the
// level/projection uses. This corrects the camera↔projector offset and any keystone/perspective.
// Tuned from a raw 1920x1080 camera capture (2026-06-22): the projected play area is a quad roughly
// x∈[100,1560], y∈[40,1015], noticeably narrower on the right than the old crop. To re-tune after the
// camera moves, read the four projection corners off a raw frame. Residual barrel distortion bows the
// edges slightly; SEARCH_RADIUS absorbs the small remainder so road ends still snap to their nodes.
private const val TABLE_CORNER_TL_X = 102.0f
private const val TABLE_CORNER_TL_Y = 40.0f
private const val TABLE_CORNER_TR_X = 1522.0f
private const val TABLE_CORNER_TR_Y = 88.0f
private const val TABLE_CORNER_BR_X = 1556.0f
private const val TABLE_CORNER_BR_Y = 1014.0f
private const val TABLE_CORNER_BL_X = 118.0f
private const val TABLE_CORNER_BL_Y = 1010.0f

// Dashed roads can leave gaps in the thinned skeleton that the morphological close does not bridge,
// which breaks skeleton pathfinding (a detected road then produces no street). After thinning, skeleton
// endpoints that face each other across a gap shorter than this distance are reconnected with a line.
private const val SKELETON_BRIDGE_MAX_DISTANCE = 120.0

// Number of skeleton steps walked inward from an endpoint to estimate its outgoing direction.
private const val SKELETON_BRIDGE_TANGENT_STEPS = 10

// Two endpoints are only bridged when both point roughly toward each other (collinear), so parallel
// roads are not merged. This is the maximum angle between an endpoint's tangent and the gap direction.
private const val SKELETON_BRIDGE_MAX_ANGLE_DEGREES = 40.0

// An endpoint's own immediate neighbor pixels are already connected, so anything closer than this
// cannot be a genuine gap.
private const val SKELETON_BRIDGE_MIN_DISTANCE = 4.0

// Thinning leaves short side branches ("spurs") wherever the street mask's edge is slightly frayed.
// Each spur turns into a phantom junction on an otherwise straight street, and those junctions come
// and go between captures - flickering the detected topology so the same network signature rarely
// repeats and confirmation/commits stall. Endpoint branches at most this many pixels long (2.5 m at
// 8 px/m - far below any real dead-end street, well above typical 3-15 px thinning artifacts) are
// removed from the skeleton before junction detection.
private const val SKELETON_SPUR_MAX_LENGTH_PX = 20

// All (dx, dy) offsets within the bridge search radius, nearest-first. Precomputed once and shared
// across every endpoint search instead of re-scanning the full radius (up to 241x241 pixels) for
// each endpoint: a search can then stop at the first valid candidate instead of always exhausting
// the whole window to find the true minimum.
private val BRIDGE_TARGET_OFFSETS: List<Pair<Int, Int>> by lazy {
    val maxRadius = SKELETON_BRIDGE_MAX_DISTANCE.toInt()
    val offsets = mutableListOf<Pair<Int, Int>>()
    for (dy in -maxRadius..maxRadius) {
        for (dx in -maxRadius..maxRadius) {
            val distance = sqrt((dx * dx + dy * dy).toDouble())
            if (distance < SKELETON_BRIDGE_MIN_DISTANCE || distance > SKELETON_BRIDGE_MAX_DISTANCE) continue
            offsets.add(dx to dy)
        }
    }
    offsets.sortedBy { (dx, dy) -> dx * dx + dy * dy }
}

// Fixed table working area inside the raw camera frame. The frame is cropped to this region
// (top-left corner PRE_CROP_X/PRE_CROP_Y, size PRE_CROP_WIDTH x PRE_CROP_HEIGHT) and then
// upscaled to the projection surface size (GameAreaScale.DESK_W_PX x DESK_H_PX) before the
// contour-based cropTable detection runs, removing the background around the table.
private val config: ConfigManager.ImageProcessingConfig by lazy {
    ConfigManager.loadImageProcessingConfig()
}

// Radius (pixels) for clustering nearby connection nodes; duplicates within this radius are merged (loaded from configuration).
private val CONNECTION_NODE_CLUSTER_RADIUS: Int get() = config.nodeClusterRadius

/**
 * Radius (detector pixels) within which two node positions count as the same physical node. Three
 * places must agree on this value: [StreetDetection.toLevelNode] merges detected endpoints closer
 * than this into the level's own source/target nodes when a detection is committed,
 * [StreetDetection.addPhysicalSegment]/[StreetDetection.nodeForPoint] drop or reuse endpoints below
 * it while tracing, and [StreetDetection.SegmentDirectionContext] classifies segment endpoints as
 * source/target with it so direction locking matches the commit-time node identity (a junction
 * detected inside this radius around a source *becomes* the source on commit, so its segments must
 * already be direction-locked as source streets during resolution).
 *
 * Was 1.0 (a single detector pixel, ~0.05cm on the real 90x50cm desk) - too tight for
 * [nodeForPoint]'s actual use in practice: an elevation-transition point sampled from a thinned,
 * simplified ([simplifyPath]) skeleton path routinely lands a few pixels away from the segment's own
 * already-known endpoint even when it is really the same physical point, so a 1px radius let a
 * spurious near-endpoint [ConnectionNode] slip in instead of reusing the real one - a phantom
 * micro-segment right next to a real junction/source/target, exactly the kind of extra junction that
 * can end up direction-resolved independently of (and inconsistently with) its neighbor. Widened to a
 * few pixels: still far tighter than the detection radii used for merely "nearby, not identical"
 * matches ([SNAP_RADIUS]=25, [SEARCH_RADIUS]=120, `CONNECTION_NODE_CLUSTER_RADIUS`=75 by default), so
 * two genuinely distinct, closely-spaced junctions (e.g. a tight intersection) still do not merge.
 */
private const val NODE_MERGE_RADIUS = 4.0

/**
 * Position bucket size (detector pixels) used only for [StreetDetection.cachedSegmentDirections]
 * keys - matches [StreetNetworkStabilityTracker]'s own node-position bucket size, the tolerance
 * already established for absorbing ordinary per-capture camera/skeleton position jitter on a real
 * table without risking merging two genuinely different junctions.
 */
private const val DIRECTION_CACHE_POSITION_BUCKET_SIZE = 3.0

// Radius (pixels) used to search for the nearest skeleton pixel when snapping a known level node (loaded from configuration).
private val SEARCH_RADIUS: Int get() = config.nodeSearchRadius

// How close a walked skeleton pixel must be to a key node to count as reaching that node (loaded from configuration).
private val SNAP_RADIUS: Int get() = config.nodeSnapRadius

// Nearby bridge color is sampled around red street pixels because the red road can cover the bridge
// center. The radius must absorb the camera parallax of a tall bridge: the colored bridge body can
// appear shifted sideways from the lane on top of it by roughly a lane width.
private const val ELEVATION_SAMPLE_RADIUS = 30

// Bridge colors must follow the same direction as the street. This avoids treating projected overpasses as ramps.
private const val ELEVATION_ORIENTATION_RADIUS = 70
private const val ELEVATION_ORIENTATION_MIN_PIXELS = 20
private const val ELEVATION_ORIENTATION_MAX_ANGLE_DEGREES = 35.0

// Small color runs are usually mask noise around object edges; merge them into neighboring runs.
private const val MIN_ELEVATION_RUN_LENGTH = 6

// Morphological closing size for bridge bodies, joining the blue side rails and green bridge center.
// The white street pieces resting on a bridge cover its colored body in stripes roughly one lane
// (~35 px) wide, so the closing must span a full lane or the masks fall apart into blobs mid-deck
// and the sampled elevation collapses to 0 in the holes.
private const val BRIDGE_CLOSE_KERNEL_SIZE = 41

// Elevated bridge endpoints can land on opposite sides of a projected level-0 crossing.
private const val ELEVATED_ENDPOINT_CONNECT_RADIUS = 30.0

// Physical bridge pieces carry no red lane marking of their own (only blue, or blue+green for
// level 2), so the red skeleton always has a genuine gap the width of the bridge - longer than a
// dashed-road gap. A larger search radius is only safe because it is combined with the bridge-color
// coverage check below, which rejects any candidate that is not actually a bridge.
private const val ELEVATED_BRIDGE_MAX_DISTANCE = 250.0

// Fraction of the straight line between two red-skeleton endpoints that must be covered by blue or
// green bridge-color pixels before the gap is trusted to be a bridge and reconnected.
private const val BRIDGE_COLOR_COVERAGE_RATIO = 0.6

/**
 * Represents an elevation transition error.
 * @property fromElevation The elevation from which the transition originates.
 * @property toElevation The elevation to which the transition leads.
 * @property position The position of the transition.
 * @property message The message describing the error.
 */
data class ElevationTransitionError(
    val fromElevation: Int,
    val toElevation: Int,
    val position: Point,
    val message: String = "Invalid direct elevation transition from $fromElevation to $toElevation."
)

/**
 * Represents one user-facing issue found during street detection.
 * @property kind Machine-readable issue category.
 * @property message Short user-facing description.
 * @property position Optional position where the issue was detected.
 */
data class StreetDetectionIssue(
    val kind: StreetDetectionIssueKind,
    val message: String,
    val position: Point? = null
)

/**
 * Categories of issues that can be surfaced to the construction UI.
 */
enum class StreetDetectionIssueKind {
    /** Required ArUco corner markers were missing from the captured table image. */
    MISSING_ARUCO_MARKERS,

    /** A physical bridge piece skipped an elevation (e.g. level 0 straight to level 2). */
    INVALID_ELEVATION_TRANSITION
}

/**
 * Represents the result of the street graph detection.
 * @property streets The streets.
 * @property invalidElevationTransitions The invalid elevation transitions.
 * @property issues User-facing detection issues that the UI can surface.
 */
data class StreetGraphDetectionResult(
    val streets: List<Street>,
    val invalidElevationTransitions: List<ElevationTransitionError>,
    val issues: List<StreetDetectionIssue> = emptyList()
)

/**
 * A street-graph detection already computed for [level] from one captured image, not yet applied.
 *
 * Running the full detection pipeline twice for the same capture - once to preview it, once to
 * commit it - would waste the most expensive part of the pipeline for no benefit, since the result
 * is deterministic for the same image. Splitting detection ([StreetDetection.detectStreetGraph])
 * from applying it ([StreetDetection.commitDetection]) lets a caller compute the result once, show
 * it as an instant preview, and only apply that same computed result once it has proven stable.
 *
 */
data class PendingStreetDetection(
    /** The level this detection was computed against. */
    val level: Level,
    /** The raw pixel-space detection result. */
    val detectionResult: StreetGraphDetectionResult,
    /** Maps [level]'s source/target nodes to their pixel-space stand-ins. */
    val detectorNodesByOriginal: Map<Node, Node>
)

/**
 * Detects streets and creates a street graph.
 */
class StreetDetection {

    /**
     * Reuses a detected ArUco rectification across captures instead of re-detecting markers every
     * time. Callers that create a fresh [StreetDetection] per capture (rather than reusing one
     * instance) get no caching benefit, since this cache does not outlive its own instance.
     */
    private val arucoCache = ArucoRectificationCache(config.arucoRevalidationIntervalMs)

    /** Last logged set of direction-consistency issues, so [logDirectionConsistency] logs changes, not every capture. */
    private var lastLoggedDirectionIssues: Set<String> = emptySet()

    /** Last logged roundabout signature, so [logRoundaboutState] logs changes, not every capture. */
    private var lastLoggedRoundaboutKey: String? = null

    /**
     * The roundabout (if any) [SegmentDirectionContext] classified on the previous call to
     * [buildStreetGraphWithElevations], fed back in as [RoundaboutGeometry.detect]'s
     * `previousCandidate` so a roundabout right at the edge of the normal detection tolerance stays
     * classified consistently instead of flapping in and out between captures (see [RoundaboutGeometry.detect]'s
     * hysteresis doc). Lives on this instance - not [SegmentDirectionContext], which is recreated
     * fresh every call - so it survives across captures exactly like [arucoCache] does.
     */
    private var previousRoundaboutCandidate: RoundaboutGeometry.Candidate? = null

    /**
     * Pins the direction of every ordinary (not source/target-locked, not roundabout) junction-to-
     * junction segment the first time it is decided, keyed by [cacheSegmentKey] and storing
     * [cacheNodeKey] of the node the segment flows FROM. [SegmentDirectionContext.resolveAll] reuses
     * the pinned direction on every later capture instead of re-running the tie-break rule chain
     * against that capture's own (still slightly noisy) geometry - the direct fix for one segment's
     * direction flip-flopping between captures even after [StreetDirectionResolver]'s tie tolerances
     * were widened, which was itself enough noise to keep the exact-repeat confirmation signature from
     * ever settling and to occasionally split what is really one physical road (a spurious mid-road
     * junction from skeleton noise) into two independently-decided halves that could disagree.
     *
     * Storing the FROM node's key rather than a plain forward/backward boolean is deliberate:
     * [PhysicalSegment]'s own nodeA/nodeB assignment for "the same" physical segment is not guaranteed
     * to stay in the same order capture to capture, so a raw boolean would silently invert on capture
     * where the order happened to flip.
     *
     * Pruned back to exactly the current capture's undecided segments at the end of every
     * [SegmentDirectionContext.resolveAll] call ([pruneSegmentDirectionCache]), so a segment that
     * disappears (player erased that part of the road) can never leave behind a stale direction a
     * later, unrelated road drawn through a similar position could incorrectly inherit.
     */
    private val cachedSegmentDirections = mutableMapOf<String, String>()

    /**
     * Coarser position bucket used only for [cachedSegmentDirections] keys - the same tolerance
     * [StreetNetworkStabilityTracker]'s own node-position bucketing uses to absorb the couple of
     * detector pixels a real camera capture wobbles a node's position by between otherwise identical
     * frames, chosen independently here since a cache lookup that missed on that same wobble would
     * defeat the whole point of pinning a direction.
     */
    private fun cacheNodeKey(node: Node): String {
        val bucketX = (node.position.x / DIRECTION_CACHE_POSITION_BUCKET_SIZE).roundToInt()
        val bucketY = (node.position.y / DIRECTION_CACHE_POSITION_BUCKET_SIZE).roundToInt()
        return "$bucketX:$bucketY:${node.elevation}"
    }

    /** Order-independent [cachedSegmentDirections] key for the segment between [nodeA] and [nodeB]. */
    private fun cacheSegmentKey(nodeA: Node, nodeB: Node): String {
        val keyA = cacheNodeKey(nodeA)
        val keyB = cacheNodeKey(nodeB)
        return if (keyA <= keyB) "$keyA|$keyB" else "$keyB|$keyA"
    }

    /** The direction pinned for [undirected] in [cachedSegmentDirections], if any (see its doc). */
    private fun cachedDirection(undirected: UndirectedSegment): Boolean? =
        when (cachedSegmentDirections[cacheSegmentKey(undirected.nodeA, undirected.nodeB)]) {
            cacheNodeKey(undirected.nodeA) -> true
            cacheNodeKey(undirected.nodeB) -> false
            else -> null
        }

    /**
     * Pins [forward]'s direction for [undirected] into [cachedSegmentDirections], overwriting any
     * previous entry - used both to record a freshly-resolved segment's direction for the first time
     * and to keep an already-cached one in sync when [StreetDirectionResolver.repairDeadEnds] or
     * [StreetDirectionResolver.enforceSourceTargetOrientation] corrects it, so the *next* capture pins
     * the corrected direction rather than reverting to the stale one and needing to be corrected again
     * every single time.
     */
    private fun cacheDirection(undirected: UndirectedSegment, forward: Boolean) {
        val fromKey = if (forward) cacheNodeKey(undirected.nodeA) else cacheNodeKey(undirected.nodeB)
        cachedSegmentDirections[cacheSegmentKey(undirected.nodeA, undirected.nodeB)] = fromKey
    }

    /** Drops every [cachedSegmentDirections] entry not among [currentUndecided]'s keys - see its doc. */
    private fun pruneSegmentDirectionCache(currentUndecided: List<UndirectedSegment>) {
        val currentKeys = currentUndecided.mapTo(mutableSetOf()) { cacheSegmentKey(it.nodeA, it.nodeB) }
        cachedSegmentDirections.keys.retainAll(currentKeys)
    }

    /**
     * Diagnostic only. After direction resolution, every [ConnectionNode] should have at least one
     * incoming and one outgoing street - a junction where every segment points inward (dead end) or
     * outward (ghost source) means the per-segment direction rules produced an inconsistent
     * orientation that [StreetDirectionResolver.repairDeadEnds] could not (or is not allowed to)
     * repair. Logged with position keys so the offending junction can be located on the real table;
     * deduplicated so a persistent issue logs once, not once per capture.
     */
    private fun logDirectionConsistency(streets: List<Street>) {
        val incomingCounts = mutableMapOf<String, Int>()
        val outgoingCounts = mutableMapOf<String, Int>()
        val connectionNodeKeys = mutableSetOf<String>()

        for (street in streets) {
            val fromKey = logicalNodeKey(street.startNode)
            val toKey = logicalNodeKey(street.endNode)
            outgoingCounts[fromKey] = (outgoingCounts[fromKey] ?: 0) + 1
            incomingCounts[toKey] = (incomingCounts[toKey] ?: 0) + 1
            if (street.startNode is ConnectionNode) connectionNodeKeys.add(fromKey)
            if (street.endNode is ConnectionNode) connectionNodeKeys.add(toKey)
        }

        val issues = connectionNodeKeys
            .filter { (incomingCounts[it] ?: 0) == 0 || (outgoingCounts[it] ?: 0) == 0 }
            .map { "$it in=${incomingCounts[it] ?: 0} out=${outgoingCounts[it] ?: 0}" }
            .toSet()

        if (issues == lastLoggedDirectionIssues) return
        val hadIssues = lastLoggedDirectionIssues.isNotEmpty()
        lastLoggedDirectionIssues = issues

        if (issues.isNotEmpty()) {
            println("[Direction] Inconsistent junction orientation after direction resolution: ${issues.joinToString()}")
        } else if (hadIssues) {
            println("[Direction] Junction orientation consistent again.")
        }
    }

    /**
     * Diagnostic only. When [RoundaboutGeometry] classifies detected junctions as a roundabout, every
     * ring segment is force-locked to the right-hand direction and excluded from dead-end repair - a
     * false positive (e.g. a large street loop that happens to fit a circle) therefore silently forces
     * wrong one-way directions that no other rule can override. Logging when the classification
     * appears, changes, or disappears makes that visible on the real table; deduplicated so a stable
     * detection logs once, not once per capture.
     */
    private fun logRoundaboutState(roundabout: RoundaboutGeometry.Candidate?) {
        val key = roundabout?.nodeKeys?.sorted()?.joinToString("|")
        if (key == lastLoggedRoundaboutKey) return
        lastLoggedRoundaboutKey = key

        if (roundabout != null) {
            println(
                "[Direction] Roundabout detected (${roundabout.nodeKeys.size} junctions around " +
                    "${roundabout.center}) - ring segments forced to right-hand direction: ${roundabout.nodeKeys}"
            )
        } else {
            println("[Direction] Previously detected roundabout no longer present - ring segments follow the normal rule chain again.")
        }
    }

    /**
     * Represents a key node.
     * @property node The node.
     * @property snapped The snapped skeleton pixel.
     */
    private data class KeyNode(val node: Node, val snapped: SkeletonPathfinder.SkeletonPixel)
    
    /**
     * Represents a physical segment.
     * @property startNode The start node.
     * @property endNode The end node.
     * @property path The path.
     */
    private data class PhysicalSegment(val startNode: Node, val endNode: Node, val path: List<Point>)

    /**
     * Represents a split result of the elevation.
     * @property segments The segments of the elevation.
     * @property invalidTransitions The invalid transitions of the elevation.
     */
    private data class ElevationSplitResult(
        val segments: List<PhysicalSegment>,
        val invalidTransitions: List<ElevationTransitionError>
    )
    
    /**
     * Represents a HSV range.
     * @property lower The lower bound of the HSV range.
     * @property upper The upper bound of the HSV range.
     */
    private data class HsvRange(val lower: Scalar, val upper: Scalar)

    /**
     * Compact row-major copy of a binary skeleton image for fast pixel lookups.
     * @property data The raw skeleton bytes in row-major order (non-zero = foreground).
     * @property cols The image width in pixels.
     * @property rows The image height in pixels.
     */
    private data class SkeletonPixels(val data: ByteArray, val cols: Int, val rows: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SkeletonPixels

            if (cols != other.cols) return false
            if (rows != other.rows) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = cols
            result = 31 * result + rows
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * A degree-1 skeleton pixel together with its outward direction.
     * @property x The x-coordinate of the endpoint pixel.
     * @property y The y-coordinate of the endpoint pixel.
     * @property tangent The outward tangent estimated by walking a few steps inward.
     */
    private data class SkeletonEndpoint(val x: Int, val y: Int, val tangent: Direction)

    /**
     * A single pixel step while walking along the skeleton.
     * @property x The x-coordinate of the step pixel.
     * @property y The y-coordinate of the step pixel.
     */
    private data class SkeletonStep(val x: Int, val y: Int)

    /**
     * A candidate skeleton pixel an endpoint can be bridged to across a gap.
     * @property x The x-coordinate of the target pixel.
     * @property y The y-coordinate of the target pixel.
     * @property distance The Euclidean distance from the endpoint to the target in pixels.
     */
    private data class BridgeTarget(val x: Int, val y: Int, val distance: Double)

    /**
     * Represents a bridge elevation map.
     * @property blueMask The blue mask of the bridge.
     * @property greenMask The green mask of the bridge.
     */
    private data class BridgeElevationMap(
        val blueMask: Mat,
        val greenMask: Mat
    ) {
        /** Releases the OpenCV masks owned by this map. */
        fun release() {
            blueMask.release()
            greenMask.release()
        }
    }

    /**
     * Represents the direction of a vector in 2D space.
     * @property x The x-component of the direction vector.
     * @property y The y-component of the direction vector.
     */
    private data class Direction(val x: Double, val y: Double)

    /**
     * Detects street polylines in the given image and returns their approximated contours as Mat objects.
     *
     * @param image The input image in BGR format.
     * @return A list of Mat objects, where each Mat represents an approximated polyline contour.
     *         Each Mat contains points in the format expected by OpenCV's polylines function.
     */
    fun detectStreetPolylines(image: Mat): List<Mat> {
        val skeleton = createSkeletonStreetImage(image)
        // Find contours in the thinned binary image. These contours represent the potential street polylines.
        val redContours = MatVector()
        // RETR_EXTERNAL retrieves only the extreme outer contours. CHAIN_APPROX_SIMPLE compresses horizontal, vertical, and diagonal segments and leaves only their end points.
        findContours(skeleton, redContours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)

        val detectedPolylinesMats = mutableListOf<Mat>()

        // Iterate through each detected contour.
        for (i in 0 until redContours.size().toInt()) {
            val contour = redContours[i.toLong()]
            // Filter contours based on their arc length. This helps to remove very small contours that are likely noise, ensuring only significant lines are considered streets.
            if (arcLength(contour, false) > MIN_POLYLINE_ARC_LENGTH) {
                val approx = Mat()
                // Approximate the contour with a simpler polygon. This reduces the number of points while maintaining the shape, making the polylines smoother and more manageable.
                // POLYLINE_APPROX_EPSILON determines the maximum distance between the original contour and its approximation.
                approxPolyDP(contour, approx, POLYLINE_APPROX_EPSILON, false)
                val polyContours = MatVector(1)
                polyContours.put(0, approx)
                detectedPolylinesMats.add(approx)
                polyContours.close()
            }
            contour.release()
        }

        redContours.close()
        skeleton.release()

        return detectedPolylinesMats
    }

    /**
     * Detects and extracts the skeleton (thin, 1-pixel wide representation) of streets.
     *
     * Performs: table cropping → BGR to HSV conversion → red color masking (2 ranges) →
     * morphological closing → thinning (skeletonization).
     *
     * @param originalImage The input image in BGR format.
     * @return Binary skeleton image where white pixels represent street lines, black pixels are background.
     */
    fun createSkeletonStreetImage(originalImage: Mat): Mat {
        if (originalImage.empty()) {
            return Mat()
        }

        // Rectify the table via ArUco corner markers; falls back to the contour-based crop when
        // markers are not available (e.g. test fixtures).
        val table = prepareTableImage(originalImage)
        val skeleton = createSkeletonFromTable(table)
        table.release()
        return skeleton
    }

    /**
     * Builds the street skeleton from an already-rectified/cropped table image.
     *
     * Performs: BGR to HSV conversion → red color masking (2 ranges) → morphological closing →
     * thinning (skeletonization). Split out from [createSkeletonStreetImage] so
     * [buildStreetGraphWithElevations] can rectify the table once and reuse it here and for the
     * bridge elevation masks, instead of running ArUco marker detection twice per capture.
     */
    private fun createSkeletonFromTable(table: Mat): Mat {
        // Save the image for debugging purposes (overwrites existing file)
        saveDebugImage(table)

        // Convert the cropped image into HSV for better color segmentation.
        val hsvImage = Mat()
        cvtColor(table, hsvImage, COLOR_BGR2HSV)

        // Define the color ranges for red in HSV. Red color wraps around the 0-180 hue scale,
        // so two ranges are needed to capture the full spectrum of red. Thresholds come from the
        // configuration so they can be tuned against the real table's lighting without a rebuild.
        val hsv = config.hsv
        val lowerRedMask = Mat()
        val upperRedMask = Mat()
        val lowerRedRange1 = Mat(hsvImage.size(), hsvImage.type(), Scalar(0.0, hsv.redSaturationMin.toDouble(), hsv.redValueMin.toDouble(), 0.0))
        val upperRedRange1 = Mat(hsvImage.size(), hsvImage.type(), Scalar(hsv.redHueLowMax.toDouble(), 255.0, 255.0, 0.0))
        val lowerRedRange2 = Mat(hsvImage.size(), hsvImage.type(), Scalar(hsv.redHueHighMin.toDouble(), hsv.redSaturationMin.toDouble(), hsv.redValueMin.toDouble(), 0.0))
        val upperRedRange2 = Mat(hsvImage.size(), hsvImage.type(), Scalar(180.0, 255.0, 255.0, 0.0))

        // Create binary masks for each red color range. Pixels within the range are white (255), others are black (0).
        inRange(hsvImage, lowerRedRange1, upperRedRange1, lowerRedMask)
        inRange(hsvImage, lowerRedRange2, upperRedRange2, upperRedMask)

        // Combine the two red masks to get a complete mask for all red pixels.
        val redMask = Mat()
        add(lowerRedMask, upperRedMask, redMask)

        // Apply morphological closing to the red mask. This operation helps to close small holes
        // within the detected red regions and connect nearby components, making the street lines more continuous.
        val kernelRed = getStructuringElement(MORPH_RECT, Size(KERNEL_RED_WIDTH, KERNEL_RED_HEIGHT))
        val closedRedMask = Mat()
        morphologyEx(redMask, closedRedMask, MORPH_CLOSE, kernelRed)

        // Thinning the mask to get a 1-pixel wide skeleton of the detected red regions.
        val thinnedRedMask = Mat()
        thinning(closedRedMask, thinnedRedMask, THINNING_ZHANGSUEN)

        // Remove short thinning spurs BEFORE bridging: each spur would otherwise create a flickering
        // phantom junction on a straight street, and its loose end could also be mistaken for a
        // dashed-road gap endpoint by the bridging below.
        pruneShortSkeletonSpurs(thinnedRedMask)

        // Reconnect skeleton fragments left by gaps in dashed roads so pathfinding can cross them.
        bridgeSkeletonGaps(thinnedRedMask)

        hsvImage.release()
        lowerRedMask.release()
        upperRedMask.release()
        lowerRedRange1.release()
        upperRedRange1.release()
        lowerRedRange2.release()
        upperRedRange2.release()
        redMask.release()
        kernelRed.release()
        closedRedMask.release()

        return thinnedRedMask
    }

    /** Creates blue and green bridge masks in cropped table coordinates. */
    private fun createBridgeElevationMap(originalImage: Mat): BridgeElevationMap {
        if (originalImage.empty()) {
            return BridgeElevationMap(Mat(), Mat())
        }

        // Rectify the table via ArUco corner markers; falls back to the contour-based crop.
        val table = prepareTableImage(originalImage)
        val map = createBridgeElevationMapFromTable(table)
        table.release()
        return map
    }

    /** Creates blue and green bridge masks from an already-rectified/cropped table image. */
    private fun createBridgeElevationMapFromTable(table: Mat): BridgeElevationMap {
        val hsvImage = Mat()
        cvtColor(table, hsvImage, COLOR_BGR2HSV)

        val rawBlueMask = createMaskForRanges(hsvImage, blueBridgeRanges())
        val rawGreenMask = createMaskForRanges(hsvImage, greenBridgeRanges())
        val blueMask = closeBridgeMask(rawBlueMask)
        val greenMask = closeBridgeMask(rawGreenMask)

        hsvImage.release()
        rawBlueMask.release()
        rawGreenMask.release()

        return BridgeElevationMap(blueMask, greenMask)
    }

    /**
     * Produces the rectified, game-oriented table image used for detection.
     *
     * Prefers an ArUco four-marker perspective rectification ([ArucoTableRectifier]). When the
     * markers are not available (e.g. test fixtures or a missing marker) it falls back to the
     * previous behavior: pre-crop, 180° rotation for the camera mounting, then the contour-based
     * table crop.
     *
     * @param originalImage The raw input image in BGR format.
     * @return The prepared table image, or an empty Mat for empty input.
     */
    private fun prepareTableImage(originalImage: Mat): Mat {
        if (originalImage.empty()) return Mat()
        return rectifyViaAruco(originalImage) ?: fallbackTableImage(originalImage)
    }

    /**
     * Rectifies [originalImage] via ArUco markers: the permanent, once-detected transform
     * ([PermanentArucoTransform], established during calibration if the markers were projected
     * rather than stuck to the table) takes priority, since re-detecting projected markers later
     * would just fail forever - the beamer stops showing them once calibration ends. Falls back to
     * the periodic per-session cache ([arucoCache]) for a physical-sticker setup, or null when
     * neither currently has a usable transform.
     */
    private fun rectifyViaAruco(originalImage: Mat): Mat? =
        PermanentArucoTransform.rectify(originalImage) ?: arucoCache.rectify(originalImage)

    /** Contour-based table crop used when ArUco rectification is unavailable (e.g. test fixtures, a missing marker). */
    private fun fallbackTableImage(originalImage: Mat): Mat {
        val preCropped = preCropInputImage(originalImage)
        val preCroppedAndRotated = rotateImage180(preCropped)
        val table = cropTable(preCroppedAndRotated)
        preCropped.release()
        preCroppedAndRotated.release()
        return table
    }

    /** Closes small gaps in a bridge color mask. */
    private fun closeBridgeMask(mask: Mat): Mat {
        val kernel = getStructuringElement(MORPH_RECT, Size(BRIDGE_CLOSE_KERNEL_SIZE, BRIDGE_CLOSE_KERNEL_SIZE))
        val closedMask = Mat()
        morphologyEx(mask, closedMask, MORPH_CLOSE, kernel)
        kernel.release()
        return closedMask
    }

    /** Combines multiple HSV ranges into one binary mask. */
    private fun createMaskForRanges(hsvImage: Mat, ranges: List<HsvRange>): Mat {
        var combinedMask: Mat? = null

        for (range in ranges) {
            val lowerRange = Mat(hsvImage.size(), hsvImage.type(), range.lower)
            val upperRange = Mat(hsvImage.size(), hsvImage.type(), range.upper)
            val rangeMask = Mat()
            inRange(hsvImage, lowerRange, upperRange, rangeMask)

            val previousMask = combinedMask
            combinedMask = if (previousMask == null) {
                rangeMask.clone()
            } else {
                val mergedMask = Mat()
                add(previousMask, rangeMask, mergedMask)
                previousMask.release()
                mergedMask
            }

            lowerRange.release()
            upperRange.release()
            rangeMask.release()
        }

        return combinedMask ?: Mat()
    }

    /** HSV ranges used for blue level-1 bridge parts (thresholds from configuration). */
    private fun blueBridgeRanges(): List<HsvRange> = listOf(
        HsvRange(
            Scalar(config.hsv.blueHueMin.toDouble(), config.hsv.blueSaturationMin.toDouble(), config.hsv.blueValueMin.toDouble(), 0.0),
            Scalar(config.hsv.blueHueMax.toDouble(), 255.0, 255.0, 0.0)
        )
    )

    /** HSV ranges used for green level-2 bridge parts (thresholds from configuration). */
    private fun greenBridgeRanges(): List<HsvRange> = listOf(
        HsvRange(
            Scalar(config.hsv.greenHueMin.toDouble(), config.hsv.greenSaturationMin.toDouble(), config.hsv.greenValueMin.toDouble(), 0.0),
            Scalar(config.hsv.greenHueMax.toDouble(), 255.0, 255.0, 0.0)
        )
    )

    /**
     * Detects visible street geometry and returns a flat list of points for visualization.
     *
     * This method intentionally does not create domain [Street] objects. The contour detector can
     * produce point sequences whose endpoints are not valid gameplay nodes, so turning them into
     * streets here would create incorrect graph connections.
     *
     * @param originalImage The input image in BGR format.
     * @return Detected polyline points in in-game coordinates. Empty when no street is detected.
     */
    fun detectStreets(originalImage: Mat): List<Point> {
        // First, detect the polylines representing the streets in the image.
        val polylines = detectStreetPolylines(originalImage)
        val points = mutableListOf<Point>()

        // Flatten all detected contours into one point list for debug/preview rendering.
        for (polyline in polylines) {
            for (i in 0 until polyline.rows()) {
                val opencvPoint = OpenCVPoint(polyline.ptr(i))
                points.add(GameAreaScale.translateVirtualPointToInGamePoint(Point(opencvPoint.x().toDouble(), opencvPoint.y().toDouble())))
            }
            polyline.release()
        }

        return points
    }

    /**
     * Removes duplicate connection nodes by clustering nearby nodes and replacing them with their centroid.
     *
     * Groups nodes within CONNECTION_NODE_CLUSTER_RADIUS distance, then merges each cluster into a single node
     * at the centroid position. Eliminates noisy or closely-spaced duplicate detections.
     *
     * @param nodes List of ConnectionNode objects that may contain near-duplicates.
     * @return Cleaned list with duplicates merged into centroids.
     */
    private fun removeDuplicateConnectionNodes(nodes: List<ConnectionNode>): List<ConnectionNode> {
        val result = mutableListOf<ConnectionNode>()
        val visited = BooleanArray(nodes.size)

        for (i in nodes.indices) {
            if (visited[i]) continue
            val cluster = mutableListOf(nodes[i])
            // Group nodes within cluster radius.
            for (j in i + 1 until nodes.size) {
                if (visited[j]) continue
                val dx = nodes[i].position.x - nodes[j].position.x
                val dy = nodes[i].position.y - nodes[j].position.y
                if (dx * dx + dy * dy <= CONNECTION_NODE_CLUSTER_RADIUS * CONNECTION_NODE_CLUSTER_RADIUS) {
                    cluster.add(nodes[j])
                    visited[j] = true
                }
            }
            // Replace cluster with centroid node.
            val cx = cluster.map { it.position.x }.average().toInt()
            val cy = cluster.map { it.position.y }.average().toInt()
            result.add(ConnectionNode(Point(cx, cy), 0))
            visited[i] = true
        }

        return result
    }

    /**
     * Detects connection nodes (junctions and branch points) using the Crossing Number algorithm.
     *
     * For each foreground pixel, counts 0→1 transitions in its 8 clockwise neighbors.
     * Crossing number ≥ 3 indicates a junction. Results are deduplicated via clustering.
     *
     * @param skeleton Binary skeleton image (white pixels = lines, black = background).
     * @return List of ConnectionNode objects representing detected junctions/branch points.
     */
    fun detectConnectionNodes(skeleton: Mat): List<ConnectionNode> {
        val nodes = mutableListOf<ConnectionNode>()
        if (skeleton.empty()) {
            return removeDuplicateConnectionNodes(nodes)
        }

        val rows = skeleton.rows()
        val cols = skeleton.cols()

        // Copy the single-channel skeleton into a JVM array once. Per-pixel ptr() access crosses the
        // JNI boundary several million times for a full-frame skeleton and dominates detection time;
        // indexing a byte array keeps the neighbor sampling inside the JVM.
        val pixels = ByteArray(rows * cols)
        skeleton.data().get(pixels)

        /** Returns 1 when the skeleton pixel at (x, y) is foreground, 0 otherwise. */
        fun foreground(x: Int, y: Int): Int = if (pixels[y * cols + x].toInt() and 0xFF != 0) 1 else 0

        for (y in 1 until rows - 1) {
            for (x in 1 until cols - 1) {
                if (foreground(x, y) == 0) continue

                // Sample 8 neighbors clockwise.
                val p = Array(9) { 0 }
                p[1] = foreground(x,     y - 1)
                p[2] = foreground(x + 1, y - 1)
                p[3] = foreground(x + 1, y    )
                p[4] = foreground(x + 1, y + 1)
                p[5] = foreground(x,     y + 1)
                p[6] = foreground(x - 1, y + 1)
                p[7] = foreground(x - 1, y    )
                p[8] = foreground(x - 1, y - 1)

                // Count 0→1 transitions (crossing number).
                val crossingNumber = listOf(1,2,3,4,5,6,7,8,1).zipWithNext().count { (a, b) -> p[a] == 0 && p[b] == 1 }

                // Crossing number ≥ 3 indicates a junction/branch point.
                if (crossingNumber >= 3) {
                    nodes.add(ConnectionNode(Point(x, y), 0))
                }
            }
        }
        return removeDuplicateConnectionNodes(nodes)
    }

    /**
     * Removes short thinning spurs from the 1-pixel skeleton, in place.
     *
     * A spur is a branch that starts at a degree-1 endpoint and reaches a junction pixel (3+
     * neighbors) within [SKELETON_SPUR_MAX_LENGTH_PX] steps. Such branches are thinning artifacts of
     * a slightly frayed street mask, not real streets - but each one turns into a phantom junction
     * on an otherwise straight street. Those phantom junctions appear and disappear between captures,
     * changing the detected topology every cycle, so the network signature rarely repeats and the
     * two-consecutive-captures confirmation stalls.
     *
     * Deliberately conservative: a short branch ending in another endpoint (an isolated fragment) is
     * NOT removed - it may be a piece of a dashed road that [bridgeSkeletonGaps] reconnects right
     * after this pass. Only branches provably hanging off a junction are pruned; real dead-end
     * streets are far longer than the threshold.
     */
    fun pruneShortSkeletonSpurs(skeleton: Mat) {
        val pixels = readSkeletonPixels(skeleton) ?: return
        val spurPixels = mutableListOf<Pair<Int, Int>>()

        for (y in 1 until pixels.rows - 1) {
            for (x in 1 until pixels.cols - 1) {
                if (!isForeground(pixels, x, y)) continue
                if (foregroundNeighborCount(pixels, x, y) != 1) continue
                collectSpurBranch(pixels, x, y)?.let { spurPixels.addAll(it) }
            }
        }

        for ((x, y) in spurPixels) {
            skeleton.ptr(y, x).put(0)
        }
    }

    /**
     * Walks inward from the endpoint at ([startX], [startY]) and returns the branch's pixels when it
     * reaches a junction pixel within [SKELETON_SPUR_MAX_LENGTH_PX] steps - or null when the branch
     * is longer than that (a real street) or ends without a junction (an isolated fragment).
     */
    private fun collectSpurBranch(pixels: SkeletonPixels, startX: Int, startY: Int): List<Pair<Int, Int>>? {
        val branch = mutableListOf(startX to startY)
        var previousX = -1
        var previousY = -1
        var currentX = startX
        var currentY = startY

        while (branch.size <= SKELETON_SPUR_MAX_LENGTH_PX) {
            val next = nextSkeletonStep(pixels, currentX, currentY, previousX, previousY) ?: return null
            if (foregroundNeighborCount(pixels, next.x, next.y) >= 3) {
                // Junction reached: everything walked so far hangs off it as a spur. The junction
                // pixel itself stays - it belongs to the streets that actually cross there.
                return branch
            }
            previousX = currentX
            previousY = currentY
            currentX = next.x
            currentY = next.y
            branch.add(currentX to currentY)
        }

        return null
    }

    /**
     * Reconnects skeleton fragments separated by small gaps (e.g. between dashes of a dashed road).
     *
     * Each degree-1 skeleton endpoint gets an outgoing direction (tangent). The endpoint is then joined
     * by a drawn line to the nearest skeleton pixel that lies ahead of it (within
     * [SKELETON_BRIDGE_MAX_ANGLE_DEGREES] of its tangent) and no farther than
     * [SKELETON_BRIDGE_MAX_DISTANCE]. Searching for any skeleton pixel ahead — not only another
     * endpoint — also reconnects an endpoint to a corner or junction across the gap. The forward cone
     * keeps separate, parallel roads from being merged. The skeleton is modified in place.
     */
    private fun bridgeSkeletonGaps(skeleton: Mat) {
        val pixels = readSkeletonPixels(skeleton) ?: return

        for (endpoint in findSkeletonEndpoints(pixels)) {
            val target = nearestBridgeTarget(endpoint, pixels) ?: continue
            line(
                skeleton,
                OpenCVPoint(endpoint.x, endpoint.y),
                OpenCVPoint(target.x, target.y),
                Scalar(255.0, 0.0, 0.0, 0.0),
                1,
                8,
                0
            )
        }
    }

    /**
     * Reconnects red-skeleton endpoints across a physical bridge piece.
     *
     * Bridge pieces carry no red lane marking of their own (only blue, or blue+green for level 2),
     * so the red skeleton always has a genuine gap the width of the bridge - [bridgeSkeletonGaps]'s
     * short, color-blind dash-gap bridging cannot be trusted to close it safely. This pass only
     * draws a connecting line when the straight line between two endpoints is substantially covered
     * by blue or green bridge-color pixels ([isBridgeColored]), so an accidental long-distance merge
     * between two unrelated roads is not possible even at the larger [ELEVATED_BRIDGE_MAX_DISTANCE].
     * Must run before [detectConnectionNodes] so junction detection sees the bridged skeleton, and
     * the resulting line lets [splitSegmentsByElevation] sample real elevation across the bridge.
     */
    private fun bridgeElevatedGaps(skeleton: Mat, elevationMap: BridgeElevationMap) {
        val pixels = readSkeletonPixels(skeleton) ?: return
        val bluePixels = readSkeletonPixels(elevationMap.blueMask) ?: return
        val greenPixels = readSkeletonPixels(elevationMap.greenMask) ?: return

        for (endpoint in findSkeletonEndpoints(pixels)) {
            val target = nearestElevatedBridgeTarget(endpoint, pixels, bluePixels, greenPixels) ?: continue
            line(
                skeleton,
                OpenCVPoint(endpoint.x, endpoint.y),
                OpenCVPoint(target.x, target.y),
                Scalar(255.0, 0.0, 0.0, 0.0),
                1,
                8,
                0
            )
        }
    }

    /** Finds the nearest forward skeleton pixel whose connecting line is confirmed bridge-colored. */
    private fun nearestElevatedBridgeTarget(
        endpoint: SkeletonEndpoint,
        pixels: SkeletonPixels,
        bluePixels: SkeletonPixels,
        greenPixels: SkeletonPixels
    ): BridgeTarget? {
        val maxRadius = ELEVATED_BRIDGE_MAX_DISTANCE.toInt()
        var best: BridgeTarget? = null

        for (dy in -maxRadius..maxRadius) {
            for (dx in -maxRadius..maxRadius) {
                val candidate = bridgeTargetCandidate(
                    endpoint, pixels, dx, dy,
                    maxDistance = ELEVATED_BRIDGE_MAX_DISTANCE
                ) ?: continue
                if (best != null && candidate.distance >= best.distance) continue
                if (!isBridgeColored(endpoint.x, endpoint.y, candidate.x, candidate.y, bluePixels, greenPixels)) continue
                best = candidate
            }
        }

        return best
    }

    /** Returns whether most of the straight line between two points is covered by bridge coloring. */
    private fun isBridgeColored(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        bluePixels: SkeletonPixels,
        greenPixels: SkeletonPixels
    ): Boolean {
        val distance = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble())
        val steps = max(4, distance.roundToInt())
        var covered = 0
        for (step in 0..steps) {
            val t = step.toDouble() / steps
            val x = (x0 + (x1 - x0) * t).roundToInt()
            val y = (y0 + (y1 - y0) * t).roundToInt()
            if (isForeground(bluePixels, x, y) || isForeground(greenPixels, x, y)) covered++
        }
        return covered.toDouble() / (steps + 1) >= BRIDGE_COLOR_COVERAGE_RATIO
    }

    /** Copies non-empty skeleton image bytes into a compact lookup structure. */
    private fun readSkeletonPixels(skeleton: Mat): SkeletonPixels? {
        val rows = skeleton.rows()
        val cols = skeleton.cols()
        if (rows < 3 || cols < 3) return null

        val pixels = ByteArray(rows * cols)
        skeleton.data().get(pixels)
        return SkeletonPixels(pixels, cols, rows)
    }

    /** Returns whether a skeleton coordinate is inside the image and contains a foreground pixel. */
    private fun isForeground(pixels: SkeletonPixels, x: Int, y: Int): Boolean {
        val inBounds = x in 0 until pixels.cols && y in 0 until pixels.rows
        return inBounds && (pixels.data[y * pixels.cols + x].toInt() and 0xFF != 0)
    }

    /**
     * Copies a binary mask's bytes into a compact lookup structure for fast pixel checks, or null for
     * an empty mask. Used for the bridge color masks so per-pixel elevation sampling reads a JVM byte
     * array instead of crossing the JNI boundary for every sampled pixel.
     */
    private fun readMaskPixels(mask: Mat): SkeletonPixels? {
        if (mask.empty()) return null

        val rows = mask.rows()
        val cols = mask.cols()
        val pixels = ByteArray(rows * cols)
        mask.data().get(pixels)
        return SkeletonPixels(pixels, cols, rows)
    }

    /** True when [pixels] contains at least one foreground pixel. */
    private fun hasAnyForeground(pixels: SkeletonPixels): Boolean =
        pixels.data.any { it.toInt() and 0xFF != 0 }

    /** Finds degree-1 skeleton pixels and stores their outward tangent. */
    private fun findSkeletonEndpoints(pixels: SkeletonPixels): List<SkeletonEndpoint> {
        val endpoints = mutableListOf<SkeletonEndpoint>()
        for (y in 1 until pixels.rows - 1) {
            for (x in 1 until pixels.cols - 1) {
                val tangent = endpointTangent(pixels, x, y) ?: continue
                endpoints.add(SkeletonEndpoint(x, y, tangent))
            }
        }
        return endpoints
    }

    /** Returns an endpoint tangent only when the pixel is foreground and has exactly one neighbor. */
    private fun endpointTangent(pixels: SkeletonPixels, x: Int, y: Int): Direction? {
        if (!isForeground(pixels, x, y)) return null
        if (foregroundNeighborCount(pixels, x, y) != 1) return null
        return skeletonTangent(pixels, x, y)
    }

    /** Counts foreground pixels in the 8-neighborhood around a skeleton coordinate. */
    private fun foregroundNeighborCount(pixels: SkeletonPixels, x: Int, y: Int): Int {
        return (-1..1).sumOf { dy ->
            (-1..1).count { dx ->
                (dx != 0 || dy != 0) && isForeground(pixels, x + dx, y + dy)
            }
        }
    }

    /**
     * Finds the nearest foreground pixel that lies in the forward cone of the endpoint.
     *
     * Walks [BRIDGE_TARGET_OFFSETS] (shared, precomputed nearest-first) and returns the first valid
     * candidate. Since that list is sorted by distance and stable with respect to its original
     * generation order, the first hit is always the same candidate a full-window scan for the
     * minimum distance would have found.
     */
    private fun nearestBridgeTarget(endpoint: SkeletonEndpoint, pixels: SkeletonPixels): BridgeTarget? {
        for ((dx, dy) in BRIDGE_TARGET_OFFSETS) {
            val candidate = bridgeTargetCandidate(endpoint, pixels, dx, dy)
            if (candidate != null) return candidate
        }
        return null
    }

    /** Builds a bridge target candidate when an offset points to a valid forward skeleton pixel. */
    private fun bridgeTargetCandidate(
        endpoint: SkeletonEndpoint,
        pixels: SkeletonPixels,
        dx: Int,
        dy: Int,
        maxDistance: Double = SKELETON_BRIDGE_MAX_DISTANCE,
        maxAngleDegrees: Double = SKELETON_BRIDGE_MAX_ANGLE_DEGREES
    ): BridgeTarget? {
        val distance = sqrt((dx * dx + dy * dy).toDouble())
        if (distance < SKELETON_BRIDGE_MIN_DISTANCE || distance > maxDistance) return null

        val candidateX = endpoint.x + dx
        val candidateY = endpoint.y + dy
        if (!isForeground(pixels, candidateX, candidateY)) return null

        val minimumAlignment = cos(Math.toRadians(maxAngleDegrees))
        val alignment = (dx / distance) * endpoint.tangent.x + (dy / distance) * endpoint.tangent.y
        if (alignment < minimumAlignment) return null

        return BridgeTarget(candidateX, candidateY, distance)
    }

    /** Estimates the outward direction of a skeleton endpoint by walking a few steps inward. */
    private fun skeletonTangent(pixels: SkeletonPixels, endpointX: Int, endpointY: Int): Direction? {
        var currentX = endpointX
        var currentY = endpointY
        var previousX = -1
        var previousY = -1

        repeat(SKELETON_BRIDGE_TANGENT_STEPS) {
            val next = nextSkeletonStep(pixels, currentX, currentY, previousX, previousY) ?: return@repeat
            previousX = currentX
            previousY = currentY
            currentX = next.x
            currentY = next.y
        }

        val directionX = (endpointX - currentX).toDouble()
        val directionY = (endpointY - currentY).toDouble()
        val length = sqrt(directionX * directionX + directionY * directionY)
        if (length < 1.0) return null
        return Direction(directionX / length, directionY / length)
    }

    /** Finds the next foreground neighbor while walking inward from a skeleton endpoint. */
    private fun nextSkeletonStep(
        pixels: SkeletonPixels,
        currentX: Int,
        currentY: Int,
        previousX: Int,
        previousY: Int
    ): SkeletonStep? {
        var next: SkeletonStep? = null
        for (dy in -1..1) {
            for (dx in -1..1) {
                val candidateX = currentX + dx
                val candidateY = currentY + dy
                if (isNextSkeletonStep(pixels, candidateX, candidateY, previousX, previousY, dx, dy)) {
                    next = SkeletonStep(candidateX, candidateY)
                }
            }
        }
        return next
    }

    /** Returns true when a neighbor can be used as the next inward skeleton step. */
    private fun isNextSkeletonStep(
        pixels: SkeletonPixels,
        candidateX: Int,
        candidateY: Int,
        previousX: Int,
        previousY: Int,
        dx: Int,
        dy: Int
    ): Boolean {
        if (dx == 0 && dy == 0) return false
        if (candidateX == previousX && candidateY == previousY) return false
        return isForeground(pixels, candidateX, candidateY)
    }

    /**
     * Maps the raw camera frame onto the fixed desk frame using a perspective warp before [cropTable]
     * runs.
     *
     * Frames delivered at the camera resolution ([CameraManager.CAMERA_WIDTH] x
     * [CameraManager.CAMERA_HEIGHT]) are warped from the projected play-area quad
     * (TABLE_CORNER_* constants) onto the full projection surface
     * ([GameAreaScale.DESK_W_PX] x [GameAreaScale.DESK_H_PX]). This aligns detector pixels with the
     * level/projection coordinate system and corrects the camera↔projector offset/keystone. Images of
     * any other size (e.g. test fixtures) are passed through unchanged so the later table detection
     * still sees the full picture.
     *
     * @param originalImage The input image in BGR format.
     * @return A new Mat with the warped desk frame, or an unchanged copy for non-camera-sized input.
     */
    private fun preCropInputImage(originalImage: Mat): Mat {
        val matchesCameraResolution = originalImage.cols() == CameraManager.CAMERA_WIDTH &&
            originalImage.rows() == CameraManager.CAMERA_HEIGHT
        if (!matchesCameraResolution) {
            return originalImage.clone()
        }

        return warpTableToDeskFrame(originalImage)
    }

    /**
     * Perspective-warps the projected play-area quad (TABLE_CORNER_* constants, in raw camera pixels)
     * onto the full desk frame ([GameAreaScale.DESK_W_PX] x [GameAreaScale.DESK_H_PX]).
     */
    private fun warpTableToDeskFrame(image: Mat): Mat {
        val deskWidth = GameAreaScale.DESK_W_PX.toFloat()
        val deskHeight = GameAreaScale.DESK_H_PX.toFloat()

        val sourceCorners = FloatPointer(
            TABLE_CORNER_TL_X, TABLE_CORNER_TL_Y,
            TABLE_CORNER_TR_X, TABLE_CORNER_TR_Y,
            TABLE_CORNER_BR_X, TABLE_CORNER_BR_Y,
            TABLE_CORNER_BL_X, TABLE_CORNER_BL_Y
        )
        val destinationCorners = FloatPointer(
            0.0f, 0.0f,
            deskWidth, 0.0f,
            deskWidth, deskHeight,
            0.0f, deskHeight
        )

        val source = Mat(4, 1, CV_32FC2, sourceCorners)
        val destination = Mat(4, 1, CV_32FC2, destinationCorners)
        val transform = getPerspectiveTransform(source, destination)

        val warped = Mat()
        warpPerspective(image, warped, transform, Size(GameAreaScale.DESK_W_PX, GameAreaScale.DESK_H_PX))

        source.release()
        destination.release()
        transform.release()
        return warped
    }

    /**
     * Rotates the input image by 180 degrees.
     *
     * @param image The input image.
     * @return A new Mat containing the rotated image.
     */
    fun rotateImage180(image: Mat): Mat {
        val rotated = Mat()
        rotate(image, rotated, ROTATE_180)
        return rotated
    }

    /**
     * Detects the main table region, removes the surrounding background and normalizes the result to
     * the fixed projection-surface frame ([GameAreaScale.DESK_W_PX] x [GameAreaScale.DESK_H_PX]).
     *
     * The grayscale blur and threshold are kept as noise reduction for the contour search. Unlike the
     * previous implementation, the detected rectangle is no longer shrunk and re-centered (which cut
     * into the table) and the crop is rescaled back to the desk frame, so the produced image always
     * stays in the same coordinate system the rest of the pipeline assumes. When no contour is found
     * or the detection looks unreliable, the full input frame is used so a bad detection can never
     * crop away real street pixels.
     *
     * @param originalImage The input image in BGR format.
     * @return A new Mat in the fixed desk frame containing the table region.
     */
    private fun cropTable(originalImage: Mat) : Mat {
        val gray = Mat()
        cvtColor(originalImage, gray, COLOR_BGR2GRAY)

        val blurredGrayImage = Mat()
        // Noise reduction
        GaussianBlur(gray, blurredGrayImage, Size(9, 9), 0.0)

        val thresh = Mat()
        // Thresholding to remove more noise
        threshold(blurredGrayImage, thresh, TABLE_THRESHOLD_VALUE, 255.0, THRESH_BINARY)

        val tableContours = MatVector()
        findContours(thresh, tableContours, RETR_LIST, CHAIN_APPROX_SIMPLE)

        // Only trust the detected rectangle when it plausibly covers the table.
        val tableRect = largestTableRect(tableContours)
            ?.takeIf { isPlausibleTableRect(it, originalImage.cols(), originalImage.rows()) }
        val resultImage = tableRectToDeskFrame(originalImage, tableRect)

        gray.release()
        blurredGrayImage.release()
        thresh.release()
        tableContours.close()

        return resultImage
    }

    /** Returns the bounding rectangle of the largest contour, if any contour exists. */
    private fun largestTableRect(tableContours: MatVector): Rect? {
        var maxArea = 0.0
        var bestRect: Rect? = null

        for (i in 0 until tableContours.size().toInt()) {
            val contour = tableContours[i.toLong()]
            val area = contourArea(contour)
            if (area > maxArea) {
                maxArea = area
                bestRect = boundingRect(contour)
            }
            contour.release()
        }

        return bestRect
    }

    /** Converts a plausible table crop to desk coordinates or keeps the full frame as fallback. */
    private fun tableRectToDeskFrame(originalImage: Mat, tableRect: Rect?): Mat {
        if (tableRect == null) return originalImage.clone()

        // Rescale the detected table region to the fixed desk frame so the skeleton coordinate system
        // stays consistent with GameAreaScale. In the live pipeline the input is already the desk-sized
        // preCrop output, so this removes the residual border without shifting.
        val cropped = Mat(originalImage, tableRect)
        val resized = Mat()
        resize(cropped, resized, Size(GameAreaScale.DESK_W_PX, GameAreaScale.DESK_H_PX))
        cropped.release()
        return resized
    }

    /** Returns true when a detected table rectangle is large enough and close enough to 9:5 to trust. */
    private fun isPlausibleTableRect(rect: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val imageArea = imageWidth.toDouble() * imageHeight.toDouble()
        if (imageArea <= 0.0) return false

        val rectArea = rect.width().toDouble() * rect.height().toDouble()
        if (rectArea < imageArea * MIN_TABLE_AREA_FRACTION) return false

        val aspect = rect.width().toDouble() / rect.height().coerceAtLeast(1)
        val minAspect = TABLE_ASPECT_RATIO / MAX_TABLE_ASPECT_DEVIATION
        val maxAspect = TABLE_ASPECT_RATIO * MAX_TABLE_ASPECT_DEVIATION
        return aspect in minAspect..maxAspect
    }

    /**
     * Build a street graph by extracting the skeleton, detecting junctions, and
     * assigning skeleton-following paths to the provided streets. Returns segments
     * split at junctions.
     *
     * @param originalImage Image used to compute the skeleton.
     * @param streets List of Street objects to populate with paths.
     * @return List of street segments after splitting at junctions.
     */
    fun buildStreetGraph(originalImage: Mat, streets: List<Street>): List<Street> {
        return buildStreetGraphWithElevations(originalImage, streets).streets
    }

    /**
     * Builds a bridge-aware street graph.
     *
     * Red street skeletons still define the drivable geometry. Blue and green bridge masks
     * are sampled around the red skeleton and split the graph into logical elevation sections:
     * level 0 for the table, level 1 for blue bridges, and level 2 for green bridge sections.
     */
    fun buildStreetGraphWithElevations(originalImage: Mat, streets: List<Street>): StreetGraphDetectionResult {
        if (originalImage.empty()) {
            return StreetGraphDetectionResult(
                streets = emptyList(),
                invalidElevationTransitions = emptyList()
            )
        }

        // Rectify (or fall back) once and reuse the result for both the skeleton and the bridge
        // elevation masks, instead of running ArUco marker detection twice per capture.
        // rectifyViaAruco additionally reuses an already-detected transform across captures rather
        // than re-detecting markers every single time.
        val rectified = rectifyViaAruco(originalImage)
        val table = rectified ?: fallbackTableImage(originalImage)

        val skeleton = createSkeletonFromTable(table)
        val elevationMap = createBridgeElevationMapFromTable(table)
        // Physical bridge pieces can interrupt the red lane marking. Reconnect only gaps whose
        // path is sufficiently covered by detected blue/green bridge color, then detect junctions
        // on the completed skeleton.
        bridgeElevatedGaps(skeleton, elevationMap)
        val junctions = detectConnectionNodes(skeleton)
        table.release()

        val segments = traceSegmentsBetweenJunctions(skeleton, streets, junctions)
        val splitResult = splitSegmentsByElevation(segments, elevationMap)
        val connectedSegments = connectNearbyElevatedBreaks(splitResult.segments)
        val directedSegments = orientSegmentsByKnownEndpoints(connectedSegments, streets)
        val normalizedStreets = normalizeNodes(directedSegments)
        logDirectionConsistency(normalizedStreets)

        skeleton.release()
        elevationMap.release()

        return StreetGraphDetectionResult(
            streets = normalizedStreets,
            invalidElevationTransitions = splitResult.invalidTransitions,
            issues = listOfNotNull(
                arucoMarkerIssueFor(originalImage, rectificationSucceeded = rectified != null)
            ) + invalidElevationTransitionIssues(splitResult.invalidTransitions)
        )
    }

    /**
     * Writes a bridge-detection acceptance overlay for one captured table photo.
     *
     * The overlay shows the rectified table image with the detected level-1 (blue) and level-2
     * (green) bridge masks painted in saturated color, the red street skeleton after the
     * bridge-aware gap reconnection, and every detected junction as a yellow circle. On top of
     * that, the real elevation-splitting pipeline is run with the skeleton's own endpoints as
     * request nodes: resulting level-1 street sections are drawn orange, level-2 sections
     * magenta, and every rejected elevation jump as a thick red circle. The documented table
     * acceptance for bridge detection (see
     * `test/imagedetectiontests/BridgeDetectionTableAcceptanceTest.kt`) uses this to verify the
     * pipeline against real camera captures without starting the full game.
     *
     * @param originalImage The captured photo in BGR format (raw camera frame or rectified capture).
     * @param outputDirectory The directory the overlay is written to; created when missing.
     * @param baseName The base file name; the overlay is written as `<baseName>_bridge_overlay.png`.
     * @return The path of the written overlay, or null when [originalImage] is empty.
     */
    fun writeBridgeDetectionAcceptanceOverlay(
        originalImage: Mat,
        outputDirectory: String,
        baseName: String
    ): String? {
        if (originalImage.empty()) return null

        val table = prepareTableImage(originalImage)
        val skeleton = createSkeletonStreetImage(originalImage)
        val elevationMap = createBridgeElevationMap(originalImage)
        bridgeElevatedGaps(skeleton, elevationMap)
        val junctions = detectConnectionNodes(skeleton)

        val overlay = table.clone()
        paintMask(overlay, elevationMap.blueMask, Scalar(255.0, 64.0, 0.0, 0.0))
        paintMask(overlay, elevationMap.greenMask, Scalar(0.0, 200.0, 0.0, 0.0))
        paintMask(overlay, skeleton, Scalar(0.0, 0.0, 255.0, 0.0))
        for (junction in junctions) {
            circle(
                overlay,
                OpenCVPoint(junction.position.x.roundToInt(), junction.position.y.roundToInt()),
                8,
                Scalar(0.0, 255.0, 255.0, 0.0),
                2,
                8,
                0
            )
        }

        // Each acceptance photo is an independent, unrelated capture (callers may reuse one
        // StreetDetection instance across a whole photo directory) - reset the roundabout hysteresis
        // and the pinned segment directions so a previous, unrelated photo's classification can never
        // bias this one's diagnostic overlay.
        previousRoundaboutCandidate = null
        cachedSegmentDirections.clear()
        val diagnosticResult = buildStreetGraphWithElevations(originalImage, buildEndpointRequestStreets(skeleton))
        for (street in diagnosticResult.streets) {
            val elevation = max(street.startNode.elevation, street.endNode.elevation)
            if (elevation == 0) continue
            val color = if (elevation >= 2) Scalar(255.0, 0.0, 255.0, 0.0) else Scalar(0.0, 165.0, 255.0, 0.0)
            drawDiagnosticPath(overlay, street.path, color)
        }
        for (transition in diagnosticResult.invalidElevationTransitions) {
            circle(
                overlay,
                OpenCVPoint(transition.position.x.roundToInt(), transition.position.y.roundToInt()),
                14,
                Scalar(0.0, 0.0, 255.0, 0.0),
                3,
                8,
                0
            )
        }

        // imwrite cannot open non-ASCII Windows paths (e.g. umlauts in photo names), so the
        // overlay is written to an ASCII temp file first and then moved to the real target.
        Files.createDirectories(Paths.get(outputDirectory))
        val outputPath = Paths.get(outputDirectory, "${baseName}_bridge_overlay.png")
        val temporaryPath = Files.createTempFile("bridge_overlay", ".png")
        imwrite(temporaryPath.toString(), overlay)
        Files.move(temporaryPath, outputPath, StandardCopyOption.REPLACE_EXISTING)

        table.release()
        skeleton.release()
        elevationMap.release()
        overlay.release()

        return outputPath.toString()
    }

    /**
     * Builds throwaway request streets from the skeleton's own degree-1 endpoints so the
     * acceptance overlay can run the real elevation-splitting pipeline on photos whose true
     * source/target node positions are unknown. The pairing is irrelevant: segment tracing only
     * collects the endpoints as key nodes and finds paths between all pairs.
     */
    private fun buildEndpointRequestStreets(skeleton: Mat): List<Street> {
        val pixels = readSkeletonPixels(skeleton) ?: return emptyList()
        val junctions = detectConnectionNodes(skeleton)
        val harvestedPoints = findSkeletonEndpoints(pixels).map { Point(it.x, it.y) } +
            componentExtremePoints(pixels)

        // Harvested points next to a junction (e.g. a tiny spur beyond a crossing) would block
        // every segment through that junction via the crosses-another-key-node check, so they are
        // dropped; the junction itself already is a key node. Remaining points are deduplicated.
        val keyPoints = mutableListOf<Point>()
        for (point in harvestedPoints) {
            val nearJunction = junctions.any { it.position.distanceTo(point) <= SNAP_RADIUS * 2.0 }
            val nearKeptPoint = keyPoints.any { it.distanceTo(point) <= SNAP_RADIUS.toDouble() }
            if (!nearJunction && !nearKeptPoint) keyPoints.add(point)
        }

        return keyPoints.chunked(2).mapNotNull { pair ->
            if (pair.size < 2) return@mapNotNull null
            Street(
                SourceNode(pair[0], 0, Angle.ZERO, emptyList()),
                TargetNode(pair[1], 0, Angle.ZERO, Color("Diagnostic", RGBA(255, 0, 0))),
                emptyList()
            )
        }
    }

    /**
     * Finds two far-apart pixels for every skeleton component (a road whose ends loop around a
     * projected node marker has no degree-1 endpoint there), so every component receives
     * diagnostic key nodes and is traced by the acceptance overlay. Points that coincide with
     * regular endpoints are deduplicated during segment tracing.
     */
    private fun componentExtremePoints(pixels: SkeletonPixels): List<Point> {
        val visited = BooleanArray(pixels.cols * pixels.rows)
        val extremes = mutableListOf<Point>()
        for (index in visited.indices) {
            if (visited[index] || pixels.data[index].toInt() and 0xFF == 0) continue
            val componentStart = farthestComponentPixel(pixels, index, visited)
            val componentEnd = farthestComponentPixel(pixels, componentStart, null)
            extremes.add(Point(componentStart % pixels.cols, componentStart / pixels.cols))
            extremes.add(Point(componentEnd % pixels.cols, componentEnd / pixels.cols))
        }
        return extremes
    }

    /**
     * Returns the BFS-farthest pixel of the component containing [startIndex], optionally
     * marking the traversal in [visited].
     */
    private fun farthestComponentPixel(pixels: SkeletonPixels, startIndex: Int, visited: BooleanArray?): Int {
        val seen = BooleanArray(pixels.cols * pixels.rows)
        val queue = ArrayDeque(listOf(startIndex))
        seen[startIndex] = true
        var last = startIndex
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            last = index
            visited?.set(index, true)
            forEachForegroundNeighbor(pixels, index) { neighbor ->
                if (!seen[neighbor]) {
                    seen[neighbor] = true
                    queue.add(neighbor)
                }
            }
        }
        return last
    }

    /** Invokes [action] for every foreground 8-neighbor of the pixel at [index]. */
    private inline fun forEachForegroundNeighbor(pixels: SkeletonPixels, index: Int, action: (Int) -> Unit) {
        val x = index % pixels.cols
        val y = index / pixels.cols
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until pixels.cols || ny !in 0 until pixels.rows) continue
                if (pixels.data[ny * pixels.cols + nx].toInt() and 0xFF == 0) continue
                action(ny * pixels.cols + nx)
            }
        }
    }

    /** Draws one street path as a thick diagnostic polyline onto the overlay. */
    private fun drawDiagnosticPath(overlay: Mat, path: List<Point>, color: Scalar) {
        for (i in 1 until path.size) {
            line(
                overlay,
                OpenCVPoint(path[i - 1].x.roundToInt(), path[i - 1].y.roundToInt()),
                OpenCVPoint(path[i].x.roundToInt(), path[i].y.roundToInt()),
                color,
                3,
                8,
                0
            )
        }
    }

    /** Paints every non-zero mask pixel onto the BGR overlay in the given BGR color. */
    private fun paintMask(overlay: Mat, mask: Mat, color: Scalar) {
        if (mask.empty()) return
        val maskPixels = readSkeletonPixels(mask) ?: return
        val rows = overlay.rows()
        val cols = overlay.cols()
        if (maskPixels.rows != rows || maskPixels.cols != cols) return

        val overlayBytes = ByteArray(rows * cols * 3)
        overlay.data().get(overlayBytes)
        val blue = color.get(0).roundToInt().toByte()
        val green = color.get(1).roundToInt().toByte()
        val red = color.get(2).roundToInt().toByte()

        for (index in 0 until rows * cols) {
            if (maskPixels.data[index].toInt() and 0xFF == 0) continue
            overlayBytes[index * 3] = blue
            overlayBytes[index * 3 + 1] = green
            overlayBytes[index * 3 + 2] = red
        }
        overlay.data().put(overlayBytes, 0, overlayBytes.size)
    }

    /**
     * Detects the street graph for all source-target pairs in [level], converts the result back
     * into level coordinates, and adds the resulting nodes and streets to the level.
     *
     * Equivalent to [detectStreetGraph] immediately followed by [commitDetection]. Callers that need
     * to preview or stability-check a detection before applying it (e.g. live camera capture) should
     * call those two steps separately instead, so the expensive detection step only ever runs once
     * per captured image.
     */
    fun processLevelImage(level: Level, originalImage: Mat): StreetGraphDetectionResult =
        commitDetection(detectStreetGraph(level, originalImage))

    /**
     * Detects the street graph for all of [level]'s source-target pairs in [originalImage], without
     * applying the result to [level] yet. The returned [PendingStreetDetection] carries everything
     * [commitDetection] later needs to apply this same, already-computed result.
     */
    fun detectStreetGraph(level: Level, originalImage: Mat): PendingStreetDetection {
        val emptyResult = StreetGraphDetectionResult(emptyList(), emptyList())
        if (originalImage.empty()) {
            return PendingStreetDetection(level, emptyResult, emptyMap())
        }

        val sources = level.nodes.filterIsInstance<SourceNode>()
        val targets = level.nodes.filterIsInstance<TargetNode>()
        if (sources.isEmpty() || targets.isEmpty()) {
            return PendingStreetDetection(level, emptyResult, emptyMap())
        }

        // Persist the raw camera frame so the TABLE_CORNER_* warp can be re-calibrated after the camera
        // is moved (read the four projection corners off this file).
        saveRawCameraFrameForCalibration(originalImage)

        val sourcesAndTargets: List<Node> = sources + targets
        val detectorNodesByOriginal = sourcesAndTargets.associateWith { node ->
            when (node) {
                is SourceNode -> SourceNode(
                    toPixelPoint(node.position),
                    node.elevation,
                    node.orientation,
                    node.configurations
                )
                is TargetNode -> TargetNode(
                    toPixelPoint(node.position),
                    node.elevation,
                    node.orientation,
                    node.color
                )
                else -> ConnectionNode(toPixelPoint(node.position), node.elevation)
            }
        }

        val requestedStreets = sources.flatMap { source ->
            targets.map { target ->
                Street(
                    detectorNodesByOriginal.getValue(source),
                    detectorNodesByOriginal.getValue(target),
                    emptyList()
                )
            }
        }

        val detectionResult = buildStreetGraphWithElevations(originalImage, requestedStreets.toMutableList())

        return PendingStreetDetection(level, detectionResult, detectorNodesByOriginal)
    }

    /**
     * Applies an already-computed [pending] detection onto its level: discards the previous street
     * network and adds the newly detected streets/nodes, reusing [pending]'s source/target node
     * identities.
     */
    fun commitDetection(pending: PendingStreetDetection): StreetGraphDetectionResult {
        val level = pending.level

        // Discard the result of any previous detection so repeated captures do not accumulate streets
        // and connection nodes. removeStreet also unlinks orphaned ConnectionNodes, leaving only the
        // level's own source and target nodes behind.
        level.streets.toList().forEach { level.removeStreet(it) }

        val connectionNodesByDetectorKey = mutableMapOf<String, Node>()
        val addedStreets = pending.detectionResult.streets.map { detected ->
            val start = toLevelNode(detected.startNode, pending.detectorNodesByOriginal, connectionNodesByDetectorKey)
            val end = toLevelNode(detected.endNode, pending.detectorNodesByOriginal, connectionNodesByDetectorKey)
            val street = Street(start, end, detected.path.map(::toGamePoint))

            level.addNode(start)
            level.addNode(end)
            level.addStreet(street)
            street
        }

        return StreetGraphDetectionResult(
            streets = addedStreets,
            invalidElevationTransitions = pending.detectionResult.invalidElevationTransitions,
            issues = pending.detectionResult.issues
        )
    }

    /**
     * Converts an already-computed [pending] detection to game-space streets for preview rendering,
     * without applying it to its level. Reuses the same source/target node matching as
     * [commitDetection] so the preview shows the same node positions a later commit would use - but
     * bound to *detached copies* of those nodes, never the level's own instances.
     *
     * The detachment is load-bearing: [Street]'s constructor registers every street on its endpoint
     * nodes, and preview streets are created on every capture (about once per second) without ever
     * being deleted. Attached to the level's real source/target nodes they would permanently pile up
     * in [Node.outgoingStreets]/[Node.incomingStreets] - the object graph [domain.LevelValidator]
     * walks - making the level report a COMPLETE network although no detection was ever committed
     * ([Level.streets] still empty). The auto-start countdown then launched a simulation on an empty
     * street network: no vehicle could ever spawn, and only a full level reload (fresh nodes without
     * the leaked links) recovered.
     */
    fun toPreviewStreets(pending: PendingStreetDetection): List<Street> {
        val connectionNodesByDetectorKey = mutableMapOf<String, Node>()
        val detachedCopiesByLevelNode = mutableMapOf<Node, Node>()

        /** A render-only stand-in for [node]: same position/elevation/type, not the level's instance. */
        fun detached(node: Node): Node = detachedCopiesByLevelNode.getOrPut(node) {
            when (node) {
                is SourceNode -> SourceNode(node.position, node.elevation, node.orientation, node.configurations)
                is TargetNode -> TargetNode(node.position, node.elevation, node.orientation, node.color)
                // Connection nodes from toLevelNode are created fresh per call and never belong to
                // the level, so registering preview streets on them leaks nothing.
                else -> node
            }
        }

        return pending.detectionResult.streets.map { detected ->
            val start = toLevelNode(detected.startNode, pending.detectorNodesByOriginal, connectionNodesByDetectorKey)
            val end = toLevelNode(detected.endNode, pending.detectorNodesByOriginal, connectionNodesByDetectorKey)
            Street(detached(start), detached(end), detected.path.map(::toGamePoint))
        }
    }

    /**
     * Reports missing ArUco corner markers for live camera-sized frames, based on whether the one
     * rectification attempt already made for this capture ([rectificationSucceeded]) found all four
     * markers - avoiding a second, independent marker-detection pass over the same image.
     */
    private fun arucoMarkerIssueFor(originalImage: Mat, rectificationSucceeded: Boolean): StreetDetectionIssue? {
        val isCameraFrame = originalImage.cols() == CameraManager.CAMERA_WIDTH &&
            originalImage.rows() == CameraManager.CAMERA_HEIGHT
        if (!isCameraFrame || rectificationSucceeded) return null

        return StreetDetectionIssue(
            kind = StreetDetectionIssueKind.MISSING_ARUCO_MARKERS,
            message = "Bitte die schwarz-weißen Marker am Rand nicht verdecken."
        )
    }

    /**
     * Turns rejected level-jumping bridge transitions into user-facing issues so the construction
     * phase shows *why* a physical bridge piece produced no street, instead of staying silent.
     */
    private fun invalidElevationTransitionIssues(
        transitions: List<ElevationTransitionError>
    ): List<StreetDetectionIssue> = transitions.map { transition ->
        StreetDetectionIssue(
            kind = StreetDetectionIssueKind.INVALID_ELEVATION_TRANSITION,
            message = "Ungültiger Ebenensprung erkannt (Ebene ${transition.fromElevation} zu " +
                "${transition.toElevation}). Bitte eine Rampe über die dazwischenliegende Ebene legen.",
            position = toGamePoint(transition.position)
        )
    }

    /**
     * Writes the raw camera frame to `debug/raw_camera_frame.png` for projector↔camera calibration.
     *
     * Only real camera-resolution frames are saved, so test fixtures never overwrite a capture. After
     * the camera is moved, read the four projection corners off this file and update the TABLE_CORNER_*
     * constants (a frame showing the level's white play-area border is easiest to measure).
     */
    private fun saveRawCameraFrameForCalibration(originalImage: Mat) {
        if (originalImage.cols() != CameraManager.CAMERA_WIDTH || originalImage.rows() != CameraManager.CAMERA_HEIGHT) {
            return
        }
        try {
            imwrite("debug/raw_camera_frame.png", originalImage)
        } catch (e: Exception) {
            println("Error saving calibration frame: ${e.message}")
        }
    }

    /** Maps a detector-space node back to an existing or new level node. */
    private fun toLevelNode(
        detectorNode: Node,
        detectorNodesByOriginal: Map<Node, Node>,
        connectionNodesByDetectorKey: MutableMap<String, Node>
    ): Node {
        val originalNode = detectorNodesByOriginal.entries
            .firstOrNull { (_, mappedDetectorNode) ->
                detectorNode.position.distanceTo(mappedDetectorNode.position) < NODE_MERGE_RADIUS &&
                    detectorNode.elevation == mappedDetectorNode.elevation
            }
            ?.key

        if (originalNode != null) return originalNode

        return connectionNodesByDetectorKey.getOrPut(logicalNodeKey(detectorNode)) {
            ConnectionNode(toGamePoint(detectorNode.position), detectorNode.elevation)
        }
    }

    /** Converts a virtual game point into detector pixel coordinates. */
    private fun toPixelPoint(point: Point): Point = Point(
        GameAreaScale.virtualMetersToPixels(point.x),
        GameAreaScale.virtualMetersToPixels(point.y)
    )

    /** Converts a detector pixel point into virtual game coordinates. */
    private fun toGamePoint(point: Point): Point = Point(
        GameAreaScale.pixelToVirtualMeters(point.x),
        GameAreaScale.pixelToVirtualMeters(point.y)
    )

    /** Traces skeleton paths between sources, targets, and detected junctions. */
    private fun traceSegmentsBetweenJunctions(skeleton: Mat, streets: List<Street>, junctions: List<ConnectionNode>): List<PhysicalSegment> {
        val rawNodes = mutableListOf<Node>()
        for (street in streets) {
            rawNodes.add(street.startNode)
            rawNodes.add(street.endNode)
        }
        rawNodes.addAll(junctions)

        // Read the skeleton into a flat JVM buffer once instead of crossing the JNI boundary on every
        // pixel lookup of every one of the many searches below (same reasoning as
        // detectConnectionNodes's own full-image scan).
        val skeletonBuffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val keyNodes = rawNodes
            .distinctBy { positionKey(it.position) }
            .mapNotNull { node ->
                SkeletonPathfinder.snapToSkeleton(skeletonBuffer, node.position, SEARCH_RADIUS)?.let { snapped -> KeyNode(node, snapped) }
            }
            .distinctBy { "${it.snapped.x}:${it.snapped.y}" }

        // Component ids let most other key nodes be ruled out per search with a plain array lookup
        // instead of contributing to a pathfinding search that could only ever conclude "no path" -
        // see SkeletonPathfinder.ComponentLabels for why this is exact, not a heuristic shortcut.
        val components = SkeletonPathfinder.labelComponents(skeletonBuffer)
        val componentByKeyNode = keyNodes.associateWith { components.labelAt(it.snapped) }

        val segments = mutableListOf<PhysicalSegment>()
        val seen = mutableSetOf<String>()

        for (start in keyNodes) {
            val startComponent = componentByKeyNode[start] ?: continue
            val otherKeyNodes = keyNodes.filter { it !== start && componentByKeyNode[it] == startComponent }
            if (otherKeyNodes.isEmpty()) continue

            // One search per key node instead of one per (key node, other key node) pair: Dijkstra
            // already finds the shortest path to every reachable pixel in a single run, so serving
            // every other key node from that one run turns the search count linear in keyNodes.size
            // instead of quadratic - see SkeletonPathfinder.findPathsFromSource for details.
            val paths = SkeletonPathfinder.findPathsFromSource(
                skeletonBuffer, start.node.position, otherKeyNodes.map { it.node.position }, SEARCH_RADIUS
            )

            for ((end, path) in otherKeyNodes.zip(paths)) {
                if (path == null || path.size < 2) continue

                val crossesAnotherKeyNode = keyNodes.any { keyNode ->
                    keyNode != start &&
                        keyNode != end &&
                        path.any { it.distanceTo(Point(keyNode.snapped.x, keyNode.snapped.y)) <= SNAP_RADIUS.toDouble() }
                }
                if (crossesAnotherKeyNode) continue

                val segmentKey = unorderedSegmentKey(start.node, end.node)
                if (segmentKey in seen) continue
                seen.add(segmentKey)

                val normalizedPath = mutableListOf<Point>()
                normalizedPath.add(start.node.position)
                normalizedPath.addAll(path.drop(1).dropLast(1))
                normalizedPath.add(end.node.position)
                segments.add(PhysicalSegment(start.node, end.node, normalizedPath))
            }
        }

        return segments
    }

    /** Splits physical street segments whenever the sampled bridge elevation changes. */
    private fun splitSegmentsByElevation(
        segments: List<PhysicalSegment>,
        elevationMap: BridgeElevationMap
    ): ElevationSplitResult {
        val splitSegments = mutableListOf<PhysicalSegment>()
        val invalidTransitions = mutableListOf<ElevationTransitionError>()

        // Read each mask into a fast lookup structure once per detection run instead of once per
        // sampled path point: elevationAt is called for every raw skeleton pixel of every segment.
        // A mask without a single foreground pixel (no bridge piece of that color on the table in
        // this capture) is dropped entirely: it could never produce a hit anyway, and treating it as
        // absent skips the per-path-point neighborhood scans that would otherwise only ever confirm
        // "nothing there". Decided fresh per capture, so a bridge placed mid-game is picked up by the
        // very next capture as usual.
        val bluePixels = readMaskPixels(elevationMap.blueMask)?.takeIf(::hasAnyForeground)
        val greenPixels = readMaskPixels(elevationMap.greenMask)?.takeIf(::hasAnyForeground)

        for (segment in segments) {
            val elevations = repairSkippedRampLevels(
                smoothElevationRuns(
                    segment.path.indices.map { elevationAt(segment.path, it, bluePixels, greenPixels) }
                ),
                segment.path,
                bluePixels
            )
            if (segment.path.size < 2 || elevations.size != segment.path.size) continue

            var runStartIndex = 0
            var runElevation = elevations.first()
            var runStartNode = nodeAtElevation(segment.startNode, runElevation)

            for (i in 1 until segment.path.size) {
                val nextElevation = elevations[i]
                if (nextElevation == runElevation) continue

                val transitionStartIndex = i - 1
                val transitionStartNode = nodeForPoint(
                    segment.path[transitionStartIndex],
                    runElevation,
                    segment.startNode,
                    segment.endNode
                )
                addPhysicalSegment(
                    splitSegments,
                    runStartNode,
                    transitionStartNode,
                    segment.path.subList(runStartIndex, transitionStartIndex + 1)
                )

                val transitionEndNode = nodeForPoint(
                    segment.path[i],
                    nextElevation,
                    segment.startNode,
                    segment.endNode
                )
                val elevationDelta = abs(nextElevation - runElevation)
                if (elevationDelta == 1) {
                    addPhysicalSegment(
                        splitSegments,
                        transitionStartNode,
                        transitionEndNode,
                        listOf(transitionStartNode.position, transitionEndNode.position)
                    )
                } else {
                    invalidTransitions.add(
                        ElevationTransitionError(
                            fromElevation = runElevation,
                            toElevation = nextElevation,
                            position = segment.path[i]
                        )
                    )
                }

                runStartIndex = i
                runElevation = nextElevation
                runStartNode = transitionEndNode
            }

            val runEndNode = nodeAtElevation(segment.endNode, runElevation)
            addPhysicalSegment(
                splitSegments,
                runStartNode,
                runEndNode,
                segment.path.subList(runStartIndex, segment.path.size)
            )
        }

        return ElevationSplitResult(splitSegments, invalidTransitions)
    }

    /** Adds short same-level links between elevated fragments split by projected crossings. */
    private fun connectNearbyElevatedBreaks(segments: List<PhysicalSegment>): List<PhysicalSegment> {
        val connectedSegments = segments.toMutableList()
        val connectedKeys = segments
            .map { unorderedLogicalSegmentKey(it.startNode, it.endNode) }
            .toMutableSet()

        val endpoints = segments
            .flatMap { listOf(it.startNode, it.endNode) }
            .filter { it.elevation > 0 }
            .distinctBy(::logicalNodeKey)

        // Increased connection radius for better visual continuity
        val connectionRadius = ELEVATED_ENDPOINT_CONNECT_RADIUS

        for (i in 0 until endpoints.size - 1) {
            for (j in i + 1 until endpoints.size) {
                val start = endpoints[i]
                val end = endpoints[j]
                if (start.elevation != end.elevation) continue
                if (start.position.distanceTo(end.position) > connectionRadius) continue

                val key = unorderedLogicalSegmentKey(start, end)
                if (key in connectedKeys) continue

                connectedKeys.add(key)
                connectedSegments.add(
                    PhysicalSegment(
                        startNode = start,
                        endNode = end,
                        path = listOf(start.position, end.position)
                    )
                )
            }
        }

        return connectedSegments
    }

    /**
     * Inserts the skipped ramp level into a sampled elevation sequence when a direct 0-to-2 jump
     * has provable blue ramp color at the transition.
     *
     * On the physical level-2 bridge the blue base is often only visible as small offset blobs
     * beside the lane, so the sampled sequence can jump from ground straight to the green deck
     * although a real ramp is present. Such a jump is a sampling artifact, not a construction
     * error: when blue is detectable around the transition point, a short level-1 run is inserted
     * so the segment climbs one level at a time. Without any blue evidence the jump keeps being
     * reported as an invalid transition (streets must never skip a level).
     */
    private fun repairSkippedRampLevels(
        elevations: List<Int>,
        path: List<Point>,
        bluePixels: SkeletonPixels?
    ): List<Int> {
        if (elevations.size != path.size) return elevations

        val repaired = elevations.toMutableList()
        for (index in 1 until repaired.size) {
            val delta = repaired[index] - repaired[index - 1]
            if (abs(delta) != 2 || !hasRampColorNearTransition(path, index, bluePixels)) continue

            val rampIndices = if (delta > 0) {
                (index - MIN_ELEVATION_RUN_LENGTH).coerceAtLeast(1) until index
            } else {
                index until (index + MIN_ELEVATION_RUN_LENGTH).coerceAtMost(repaired.size - 1)
            }
            for (rampIndex in rampIndices) {
                repaired[rampIndex] = 1
            }
        }
        return repaired
    }

    /** Returns whether blue ramp color is detectable around one elevation transition point. */
    private fun hasRampColorNearTransition(
        path: List<Point>,
        transitionIndex: Int,
        bluePixels: SkeletonPixels?
    ): Boolean {
        val windowStart = (transitionIndex - MIN_ELEVATION_RUN_LENGTH).coerceAtLeast(0)
        val windowEnd = (transitionIndex + MIN_ELEVATION_RUN_LENGTH).coerceAtMost(path.lastIndex)
        for (index in windowStart..windowEnd) {
            val x = path[index].x.roundToInt()
            val y = path[index].y.roundToInt()
            if (nearestMaskPixelDistance(bluePixels, x, y) != null) return true
        }
        return false
    }

    /** Merges very short elevation runs into neighboring values. */
    private fun smoothElevationRuns(elevations: List<Int>): List<Int> {
        if (elevations.size < MIN_ELEVATION_RUN_LENGTH * 2) return elevations

        val smoothed = elevations.toMutableList()
        var start = 0
        while (start < smoothed.size) {
            var end = start + 1
            while (end < smoothed.size && smoothed[end] == smoothed[start]) {
                end++
            }

            val runLength = end - start
            if (runLength < MIN_ELEVATION_RUN_LENGTH) {
                val previousElevation = if (start > 0) smoothed[start - 1] else null
                val nextElevation = if (end < smoothed.size) smoothed[end] else null
                val replacement = previousElevation ?: nextElevation

                val canMergeIntoNeighbor =
                    previousElevation == nextElevation || previousElevation == null || nextElevation == null
                if (replacement != null && canMergeIntoNeighbor) {
                    for (i in start until end) {
                        smoothed[i] = replacement
                    }
                }
            }

            start = end
        }

        return smoothed
    }

    /**
     * Samples the logical elevation at one path point.
     *
     * The green level-2 deck of a physical bridge reaches almost to the bridge ends, so a fixed
     * green-over-blue priority would skip the short blue ramp zones and turn the whole bridge
     * into an invalid direct 0-to-2 jump. Once the green deck is confirmed street-aligned, the
     * nearer of the two bridge colors therefore decides the level: blue directly beneath the
     * lane wins over a green deck that is merely within sampling range. Blue on its own still
     * has to pass the street-alignment gate so crossing bridges do not contaminate the streets
     * they pass over.
     */
    private fun elevationAt(path: List<Point>, index: Int, bluePixels: SkeletonPixels?, greenPixels: SkeletonPixels?): Int {
        val point = path[index]
        val direction = tangentAt(path, index) ?: return 0
        val x = point.x.roundToInt()
        val y = point.y.roundToInt()

        val greenDistance = alignedMaskDistance(greenPixels, x, y, direction)
        if (greenDistance != null) {
            val blueDistance = nearestMaskPixelDistance(bluePixels, x, y)
            return if (blueDistance != null && blueDistance < greenDistance) 1 else 2
        }
        if (alignedMaskDistance(bluePixels, x, y, direction) != null) return 1
        return 0
    }

    /** Returns the distance to the nearest street-aligned mask pixel, or null when there is none. */
    private fun alignedMaskDistance(
        mask: SkeletonPixels?,
        centerX: Int,
        centerY: Int,
        streetDirection: Direction
    ): Double? {
        val pixels = mask ?: return null
        val distance = nearestMaskPixelDistance(pixels, centerX, centerY) ?: return null
        if (!isMaskAlignedWithStreet(pixels, centerX, centerY, streetDirection)) return null
        return distance
    }

    /** Estimates the local path direction around a point index. */
    private fun tangentAt(path: List<Point>, index: Int): Direction? {
        var previousIndex = index
        while (previousIndex > 0 && path[previousIndex].distanceTo(path[index]) < 1.0) {
            previousIndex--
        }

        var nextIndex = index
        while (nextIndex < path.lastIndex && path[nextIndex].distanceTo(path[index]) < 1.0) {
            nextIndex++
        }

        if (previousIndex == index && nextIndex == index) return null

        val from = path[previousIndex]
        val to = path[nextIndex]
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1.0) return null

        return Direction(dx / length, dy / length)
    }

    /** Checks whether the mask pixels around a point align with the street direction. */
    private fun isMaskAlignedWithStreet(
        mask: SkeletonPixels,
        centerX: Int,
        centerY: Int,
        streetDirection: Direction
    ): Boolean {
        val minX = (centerX - ELEVATION_ORIENTATION_RADIUS).coerceAtLeast(0)
        val maxX = (centerX + ELEVATION_ORIENTATION_RADIUS).coerceAtMost(mask.cols - 1)
        val minY = (centerY - ELEVATION_ORIENTATION_RADIUS).coerceAtLeast(0)
        val maxY = (centerY + ELEVATION_ORIENTATION_RADIUS).coerceAtMost(mask.rows - 1)
        val radiusSquared = ELEVATION_ORIENTATION_RADIUS * ELEVATION_ORIENTATION_RADIUS

        var count = 0
        var sumX = 0.0
        var sumY = 0.0
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy > radiusSquared || !isForeground(mask, x, y)) continue

                count++
                sumX += x.toDouble()
                sumY += y.toDouble()
            }
        }

        if (count < ELEVATION_ORIENTATION_MIN_PIXELS) return false

        val meanX = sumX / count
        val meanY = sumY / count
        var covarianceXX = 0.0
        var covarianceYY = 0.0
        var covarianceXY = 0.0

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dxFromCenter = x - centerX
                val dyFromCenter = y - centerY
                if (dxFromCenter * dxFromCenter + dyFromCenter * dyFromCenter > radiusSquared || !isForeground(mask, x, y)) continue

                val dx = x - meanX
                val dy = y - meanY
                covarianceXX += dx * dx
                covarianceYY += dy * dy
                covarianceXY += dx * dy
            }
        }

        val bridgeAngle = 0.5 * atan2(2.0 * covarianceXY, covarianceXX - covarianceYY)
        val streetAngle = atan2(streetDirection.y, streetDirection.x)
        val alignment = abs(cos(bridgeAngle - streetAngle))
        val minimumAlignment = cos(Math.toRadians(ELEVATION_ORIENTATION_MAX_ANGLE_DEGREES))

        return alignment >= minimumAlignment
    }

    /** Returns the distance to the nearest mask pixel within the sample radius, or null. */
    private fun nearestMaskPixelDistance(mask: SkeletonPixels?, centerX: Int, centerY: Int): Double? {
        if (mask == null) return null

        val minX = (centerX - ELEVATION_SAMPLE_RADIUS).coerceAtLeast(0)
        val maxX = (centerX + ELEVATION_SAMPLE_RADIUS).coerceAtMost(mask.cols - 1)
        val minY = (centerY - ELEVATION_SAMPLE_RADIUS).coerceAtLeast(0)
        val maxY = (centerY + ELEVATION_SAMPLE_RADIUS).coerceAtMost(mask.rows - 1)
        val radiusSquared = ELEVATION_SAMPLE_RADIUS * ELEVATION_SAMPLE_RADIUS

        var bestDistanceSquared: Int? = null
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - centerX
                val dy = y - centerY
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared > radiusSquared || !isForeground(mask, x, y)) continue
                if (bestDistanceSquared == null || distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared
                }
            }
        }

        return bestDistanceSquared?.let { sqrt(it.toDouble()) }
    }

    /** Reuses a node if its elevation matches, otherwise creates a connection node. */
    private fun nodeAtElevation(node: Node, elevation: Int): Node {
        return if (node.elevation == elevation) {
            node
        } else {
            ConnectionNode(node.position, elevation)
        }
    }

    /** Chooses an endpoint node or creates a connection node for a split point. */
    private fun nodeForPoint(point: Point, elevation: Int, segmentStart: Node, segmentEnd: Node): Node {
        return when {
            point.distanceTo(segmentStart.position) < NODE_MERGE_RADIUS -> nodeAtElevation(segmentStart, elevation)
            point.distanceTo(segmentEnd.position) < NODE_MERGE_RADIUS -> nodeAtElevation(segmentEnd, elevation)
            else -> ConnectionNode(point, elevation)
        }
    }

    /** Adds a normalized physical segment if it has a usable length. */
    private fun addPhysicalSegment(
        segments: MutableList<PhysicalSegment>,
        startNode: Node,
        endNode: Node,
        path: List<Point>
    ) {
        if (path.size < 2) return
        if (startNode.elevation == endNode.elevation &&
            startNode.position.distanceTo(endNode.position) < NODE_MERGE_RADIUS
        ) {
            return
        }

        val normalizedPath = path.toMutableList()
        normalizedPath[0] = startNode.position
        normalizedPath[normalizedPath.lastIndex] = endNode.position
        segments.add(PhysicalSegment(startNode, endNode, simplifyPath(normalizedPath, POLYLINE_APPROX_EPSILON)))
    }

    /**
     * Precomputed helpers shared by every segment orientation decision within one call to
     * [orientSegmentsByKnownEndpoints]: which positions are known sources/targets, the roundabout
     * (if any) detected among [segments]' junctions, and the fallback rule-chain resolver.
     *
     * @param segments All physical segments of the detected network, used to build the roundabout
     * and fallback-resolver skeleton graphs.
     * @param streets The known source/target request streets, used to derive [sourceKeys]/[targetKeys].
     * @property sourceKeys Rounded positions of every known source node.
     * @property targetKeys Rounded positions of every known target node.
     */
    private inner class SegmentDirectionContext(
        private val segments: List<PhysicalSegment>,
        private val streets: List<Street>
    ) {
        private val sourceKeys = streets.map { positionKey(it.startNode.position) }.toSet()
        private val targetKeys = streets.map { positionKey(it.endNode.position) }.toSet()
        private val sourcePositions = streets.map { it.startNode.position }.distinct()
        private val targetPositions = streets.map { it.endNode.position }.distinct()

        /**
         * Whether [node] is (or on commit will be merged into) a known source node. Matched by
         * proximity ([NODE_MERGE_RADIUS]) in addition to the rounded position key: [toLevelNode]
         * merges every detected node within that radius into the level's source node, and the
         * connecting stub between such a node and the source is itself below the radius, so it never
         * survives as a segment ([addPhysicalSegment]). Without the proximity match a junction
         * detected right next to a source would get plain rule-chain directions - sometimes pointing
         * *into* the source - and the commit would then bind exactly those streets to the source.
         */
        private fun isSourceEndpoint(node: Node): Boolean =
            positionKey(node.position) in sourceKeys ||
                sourcePositions.any { node.position.distanceTo(it) < NODE_MERGE_RADIUS }

        /** Whether [node] is (or on commit will be merged into) a known target node - same matching rules as [isSourceEndpoint]. */
        private fun isTargetEndpoint(node: Node): Boolean =
            positionKey(node.position) in targetKeys ||
                targetPositions.any { node.position.distanceTo(it) < NODE_MERGE_RADIUS }

        /** Decides directions for segments that touch neither a known source nor a known target. */
        private val fallbackResolver = StreetDirectionResolver(
            segments.map { UndirectedSegment(it.startNode, it.endNode, it.path) },
            streets.map { it.startNode }.filterIsInstance<SourceNode>().distinctBy(::logicalNodeKey),
            streets.map { it.endNode }.filterIsInstance<TargetNode>().distinctBy(::logicalNodeKey)
        )

        /**
         * The roundabout (if any) formed by [segments]' junctions, kept driving as a single ring.
         *
         * Passes the previous capture's classification as `previousCandidate` so a roundabout right at
         * the edge of the normal detection tolerance is re-confirmed instead of flapping in and out
         * between captures - see [RoundaboutGeometry.detect]'s hysteresis doc and
         * [previousRoundaboutCandidate].
         */
        private val roundabout = RoundaboutGeometry.detect(
            segments.flatMap { listOf(it.startNode, it.endNode) }.filterIsInstance<ConnectionNode>(),
            // Segment paths let detect verify the ring actually follows the fitted circle - a street
            // grid's block outline passes the plain junction circle fit but its straight edges do not.
            segments.map { RoundaboutGeometry.RingEdge(it.startNode, it.endNode, it.path) },
            previousCandidate = previousRoundaboutCandidate
        )

        /** True when both endpoints of [segment] lie on the detected roundabout circle. */
        private fun isRoundaboutSegment(segment: PhysicalSegment): Boolean =
            roundabout != null &&
                logicalNodeKey(segment.startNode) in roundabout.nodeKeys &&
                logicalNodeKey(segment.endNode) in roundabout.nodeKeys

        /**
         * Resolves every segment's direction (true means nodeA -> nodeB, i.e. startNode -> endNode):
         * segments touching a known source/target keep that hard requirement (away from the source,
         * into the target - see [StreetDirectionResolver.requiredEndpointDirection]), a detected
         * roundabout keeps its circumcircle-based direction, and every other segment follows
         * [StreetDirectionResolver]'s rule chain. A final pass repairs any dead end a network loop may
         * have caused among the rule-chain-decided segments (see
         * [StreetDirectionResolver.repairDeadEnds]) - segments with a hard requirement are never
         * touched by that repair - and [StreetDirectionResolver.enforceSourceTargetOrientation] then
         * guarantees the source/target rule on the complete result.
         */
        fun resolveAll(): Map<UndirectedSegment, Boolean> {
            logRoundaboutState(roundabout)
            previousRoundaboutCandidate = roundabout

            val locked = mutableMapOf<UndirectedSegment, Boolean>()
            val undecided = mutableListOf<UndirectedSegment>()

            for (segment in segments) {
                val undirected = UndirectedSegment(segment.startNode, segment.endNode, segment.path)
                val required = fallbackResolver.requiredEndpointDirection(
                    undirected, ::isSourceEndpoint, ::isTargetEndpoint
                )

                when {
                    required != null -> locked[undirected] = required
                    isRoundaboutSegment(segment) -> locked[undirected] =
                        RoundaboutGeometry.isRightHandDirection(segment.startNode, segment.endNode, roundabout!!.center)
                    else -> undecided.add(undirected)
                }
            }

            // Demand flows first: orient undecided segments along an actual route for every
            // (source, color) demand, so reachability is built into the result. Only segments no
            // demand needs fall through to the per-segment nearest-source rule chain below - which
            // alone can orient e.g. every rung of a ladder layout the same way and silently leave
            // some source with no directed route at all (see StreetDirectionResolver.orientByDemandFlows).
            val flowOriented = fallbackResolver.orientByDemandFlows(locked)

            // Pin each ordinary segment's direction the first time it is decided: a cache hit is used
            // as-is, skipping the rule chain (and its noisy nearest-source/bend-angle geometry)
            // entirely, so a segment that keeps existing capture to capture keeps the same direction
            // capture to capture, never re-deciding into a different answer on its own. Only a segment
            // never seen before falls through to resolveForward.
            val ruleChain = mutableMapOf<UndirectedSegment, Boolean>()
            for (undirected in undecided) {
                if (undirected in flowOriented) continue
                ruleChain[undirected] = cachedDirection(undirected) ?: fallbackResolver.resolveForward(undirected)
            }

            // repairDeadEnds needs every segment's current direction - including the locked and
            // flow-oriented ones - to correctly count each node's outgoing streets; only ruleChain's
            // keys are eligible to be flipped (flow-oriented segments carry a demand's route and
            // must never be reversed by the repair).
            val repaired = fallbackResolver.repairDeadEnds(locked + flowOriented + ruleChain, ruleChain.keys)
            val resolved = locked + flowOriented + repaired.filterKeys { it in ruleChain }

            // Hard guarantee of the source/target rule on the complete result: no street may point
            // into a source or out of a target, no matter which stage decided it. With the locking
            // above this never flips anything; if it does, that is a direction-resolution bug worth
            // surfacing in the log rather than on the table.
            val enforced = fallbackResolver.enforceSourceTargetOrientation(
                resolved, ::isSourceEndpoint, ::isTargetEndpoint
            )
            val flippedCount = resolved.count { (segment, forward) -> enforced.getValue(segment) != forward }
            if (flippedCount > 0) {
                println(
                    "[Direction] Source/target rule flipped $flippedCount segment(s) that a later " +
                        "resolution stage had oriented into a source or out of a target."
                )
            }

            // Pin every ordinary segment's just-applied (post-repair, post-enforcement) direction for
            // the next capture, and forget any segment no longer part of this capture's network - see
            // cachedSegmentDirections's doc for why both the write-back and the pruning matter.
            for (undirected in undecided) {
                cacheDirection(undirected, enforced.getValue(undirected))
            }
            pruneSegmentDirectionCache(undecided)

            return enforced
        }
    }

    /**
     * Orients physical segments using known source and target endpoints.
     *
     * Segments that touch a source or target directly get their direction from that endpoint.
     * Every other segment (junction-to-junction) is not allowed to stay bidirectional — streets are
     * one-way — so its direction is decided by [SegmentDirectionContext]: a detected roundabout keeps
     * its existing circumcircle-based right-hand direction so the ring stays one continuously
     * drivable circle, and every other segment follows [StreetDirectionResolver]'s rule chain
     * (nearest source by path distance, then a demand filter, then direction continuity, then a
     * deterministic fallback), with dead ends caused by network loops repaired afterward.
     */
    private fun orientSegmentsByKnownEndpoints(segments: List<PhysicalSegment>, streets: List<Street>): List<Street> {
        val directedByKey = linkedMapOf<String, Street>()

        /** Adds one directed segment if it has not been created yet. */
        fun addSegment(start: Node, end: Node, path: List<Point>) {
            if (path.size < 2) return
            val key = "${logicalNodeKey(start)}->${logicalNodeKey(end)}"
            directedByKey.getOrPut(key) { Street(start, end, path) }
        }

        for ((undirected, forward) in SegmentDirectionContext(segments, streets).resolveAll()) {
            if (forward) addSegment(undirected.nodeA, undirected.nodeB, undirected.path)
            else addSegment(undirected.nodeB, undirected.nodeA, undirected.path.asReversed())
        }

        return directedByKey.values.toList()
    }

    /**
     * Reuses one Node instance for each physical position so adjacency lookups see all outgoing arcs.
     */
    private fun normalizeNodes(streets: List<Street>): List<Street> {
        val nodesByPosition = mutableMapOf<String, Node>()

        /** Returns the shared node for one logical node key. */
        fun canonical(node: Node): Node = nodesByPosition.getOrPut(logicalNodeKey(node)) { node }

        return streets.map { street ->
            val start = canonical(street.startNode)
            val end = canonical(street.endNode)
            val path = street.path.toMutableList()
            if (path.isNotEmpty()) {
                path[0] = start.position
                path[path.lastIndex] = end.position
            }
            Street(start, end, path)
        }
    }

    /** Builds a rounded coordinate key for physical positions. */
    private fun positionKey(point: Point): String = "${point.x.roundToInt()}:${point.y.roundToInt()}"

    /** Builds a position-and-elevation key for logical nodes. */
    private fun logicalNodeKey(node: Node): String = "${positionKey(node.position)}:${node.elevation}"

    /** Builds an order-independent key for a physical segment. */
    private fun unorderedSegmentKey(start: Node, end: Node): String {
        val startKey = positionKey(start.position)
        val endKey = positionKey(end.position)
        return if (startKey <= endKey) "$startKey|$endKey" else "$endKey|$startKey"
    }

    /** Builds an order-independent key including endpoint elevations. */
    private fun unorderedLogicalSegmentKey(start: Node, end: Node): String {
        val startKey = logicalNodeKey(start)
        val endKey = logicalNodeKey(end)
        return if (startKey <= endKey) "$startKey|$endKey" else "$endKey|$startKey"
    }

}
