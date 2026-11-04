package imageprocessing

import org.bytedeco.opencv.opencv_core.Mat

/**
 * Holds the application's single ArUco rectification transform for its entire runtime.
 *
 * The four corner markers can either be physical stickers (detected repeatedly, see
 * [ArucoRectificationCache]) or projected once by the beamer during calibration
 * ([scenes.ArucoDetectionScene]) and never shown again afterward. In the projected case the
 * camera never sees the markers again once calibration ends, so periodic revalidation would just
 * fail every single time and waste a full capture cycle for nothing - the transform detected during
 * calibration must simply be reused for the rest of the session instead.
 *
 * A plain singleton (rather than an instance field) is required here specifically because it must
 * survive across every scene change for the rest of the application's life, not just one
 * [imageprocessing.StreetDetection] instance's lifetime.
 */
object PermanentArucoTransform {
    private var transform: ArucoTableRectifier.Transform? = null

    /** True once a transform has been established via [seed]. */
    val isEstablished: Boolean get() = transform != null

    /**
     * Detects the four corner markers in [image] and stores the resulting transform for the rest of
     * the application's runtime, replacing any previously seeded one.
     *
     * The markers are expected in the beamer-projected inverted form shown by
     * [scenes.ArucoDetectionScene] (bright border on a black background), so detection inverts the
     * frame and searches the four projection corner crops - see
     * [ArucoTableRectifier.detectTransform]'s `invertedProjectedMarkers` parameter.
     *
     * @return true when detection succeeded and the transform was (re-)established.
     */
    fun seed(image: Mat): Boolean {
        val detected = ArucoTableRectifier.detectTransform(image, invertedProjectedMarkers = true) ?: return false
        transform?.release()
        transform = detected
        return true
    }

    /** Rectifies [image] using the established transform, or null when [seed] has not succeeded yet. */
    fun rectify(image: Mat): Mat? = transform?.apply(image)

    /** Discards the established transform, e.g. if calibration needs to be re-run from scratch. */
    fun clear() {
        transform?.release()
        transform = null
    }
}
