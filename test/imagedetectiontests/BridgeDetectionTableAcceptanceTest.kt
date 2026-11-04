package imagedetectiontests

import imageprocessing.StreetDetection
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR
import org.bytedeco.opencv.global.opencv_imgcodecs.imdecode
import org.bytedeco.opencv.global.opencv_imgproc.line
import org.bytedeco.opencv.global.opencv_imgproc.rectangle
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Point as OpenCVPoint
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Documented table-acceptance harness for bridge detection (Issue #283).
 *
 * How to run a table acceptance ("Tisch-Abnahme"):
 * 1. Capture photos of the physical table with the bridge pieces in place (raw camera frames or
 *    already-rectified captures both work) and copy them into `debug/bridge-acceptance/`.
 *    The `debug/` folder is gitignored, so acceptance photos never bloat the repository.
 * 2. Run this test class:
 *    `./gradlew.bat jvmTest --tests "imagedetectiontests.BridgeDetectionTableAcceptanceTest"`.
 * 3. Inspect the `<photo>_bridge_overlay.png` written next to each photo. In the overlay,
 *    red pixels are the detected street skeleton (after bridge-aware gap reconnection),
 *    blue/green pixels are the detected level-1/level-2 bridge masks, yellow circles mark
 *    detected junctions, orange/magenta polylines are the street sections the elevation
 *    pipeline classified as level 1/level 2, and thick red circles mark rejected elevation
 *    jumps. Detection is working when the blue/green masks cover exactly the physical bridge
 *    pieces (and nothing of the projected world texture), the red skeleton runs continuously
 *    across every bridge, and every bridge carries an orange/magenta section without thick
 *    red circles.
 *
 * Without photos in the folder, the acceptance test passes as a no-op so the CI suite stays
 * independent of local table captures; the synthetic smoke test below always exercises the
 * overlay writer itself.
 */
class BridgeDetectionTableAcceptanceTest {

    private val acceptanceDirectory: Path = Paths.get("debug", "bridge-acceptance")

    /**
     * Runs the bridge-detection pipeline on every acceptance photo in `debug/bridge-acceptance/`
     * and writes one diagnostic overlay per photo (AC: the function is verified by tests or a
     * documented table acceptance).
     */
    @Test
    fun acceptancePhotosProduceBridgeOverlays() {
        val photos = listAcceptancePhotos()
        if (photos.isEmpty()) {
            println(
                "[BRIDGE-ACCEPTANCE] No photos found in $acceptanceDirectory - " +
                    "copy table captures there and re-run this test to produce overlays."
            )
            return
        }

        val streetDetection = StreetDetection()
        for (photo in photos) {
            val image = readImage(photo)
            assertTrue(!image.empty(), "Acceptance photo could not be read: $photo")

            val overlayPath = streetDetection.writeBridgeDetectionAcceptanceOverlay(
                image,
                acceptanceDirectory.toString(),
                photo.fileName.toString().substringBeforeLast('.')
            )
            image.release()

            assertTrue(
                overlayPath != null && Files.exists(Paths.get(overlayPath)),
                "Overlay was not written for acceptance photo $photo"
            )
            println("[BRIDGE-ACCEPTANCE] Wrote $overlayPath")
        }
    }

    /**
     * Smoke test: the overlay writer must always produce an output file for a valid bridge image,
     * so the acceptance harness itself is covered even when no table photos are present.
     */
    @Test
    fun writeBridgeDetectionAcceptanceOverlayWritesOverlayForASyntheticBridgeImage() {
        val image = Mat(100, 300, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        rectangle(image, OpenCVPoint(75, 34), OpenCVPoint(225, 66), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        rectangle(image, OpenCVPoint(115, 34), OpenCVPoint(185, 66), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(280, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val outputDirectory = Files.createTempDirectory("bridge-acceptance-test")
        val overlayPath = StreetDetection().writeBridgeDetectionAcceptanceOverlay(
            image,
            outputDirectory.toString(),
            "synthetic"
        )
        image.release()

        assertTrue(
            overlayPath != null && Files.exists(Paths.get(overlayPath)),
            "The overlay writer must produce an output file for a synthetic bridge image"
        )
    }

    /**
     * Decodes one photo from raw bytes. OpenCV's imread cannot open non-ASCII Windows paths
     * (e.g. German file names containing umlauts), so the bytes are read via java.nio instead.
     */
    private fun readImage(photo: Path): Mat {
        val bytes = Files.readAllBytes(photo)
        val buffer = Mat(1, bytes.size, CV_8UC1)
        buffer.data().put(bytes, 0, bytes.size)
        val image = imdecode(buffer, IMREAD_COLOR)
        buffer.release()
        return image
    }

    /**
     * Lists the acceptance photos in [acceptanceDirectory], excluding previously written overlays.
     */
    private fun listAcceptancePhotos(): List<Path> {
        if (!Files.isDirectory(acceptanceDirectory)) return emptyList()
        Files.list(acceptanceDirectory).use { stream ->
            return stream
                .filter { path ->
                    val name = path.fileName.toString().lowercase()
                    val isImage = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    isImage && !name.endsWith("_bridge_overlay.png")
                }
                .toList()
        }
    }
}
