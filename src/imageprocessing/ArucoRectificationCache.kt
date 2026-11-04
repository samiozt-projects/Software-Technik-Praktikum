package imageprocessing

import org.bytedeco.opencv.opencv_core.Mat

/**
 * Caches an [ArucoTableRectifier] transform and reuses it for [revalidationIntervalMs] before
 * re-detecting markers, instead of running full marker detection (an expensive multi-window
 * adaptive-threshold search) on every single capture. The camera and the four corner markers do not
 * move during normal use, so a once-detected transform stays valid far longer than one capture cycle
 * - re-applying it is a cheap image warp.
 *
 * A revalidation attempt that fails to find all four markers (e.g. a hand briefly covering one) keeps
 * using the last known-good transform rather than immediately falling back to the crude static crop,
 * and is retried on every following call until detection succeeds again. A camera or table that
 * genuinely moves is picked up the next time revalidation is attempted, not instantly.
 *
 * @property revalidationIntervalMs How long a detected transform is trusted before the next call
 * re-detects markers instead of reusing it.
 */
class ArucoRectificationCache(private val revalidationIntervalMs: Long) {
    private var transform: ArucoTableRectifier.Transform? = null
    private var validatedAtMs: Long = Long.MIN_VALUE

    /**
     * Rectifies [image], reusing the cached transform when it is still within
     * [revalidationIntervalMs] of [nowMs]. Returns null when no transform is cached yet and this
     * attempt also fails to detect all four markers.
     */
    fun rectify(image: Mat, nowMs: Long = System.currentTimeMillis()): Mat? {
        val cached = transform
        if (cached != null && nowMs - validatedAtMs < revalidationIntervalMs) {
            return cached.apply(image)
        }

        val fresh = ArucoTableRectifier.detectTransform(image)
        if (fresh != null) {
            transform?.release()
            transform = fresh
            validatedAtMs = nowMs
            return fresh.apply(image)
        }

        return cached?.apply(image)
    }
}
