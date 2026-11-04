package imagedetectiontests

import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_imgproc.LINE_8
import org.bytedeco.opencv.global.opencv_imgproc.circle
import org.bytedeco.opencv.global.opencv_imgproc.line
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Point as CvPoint
import kotlin.test.Test

/** TEMPORARY: draws the current TABLE_CORNER_* quad on the raw frame to gauge the calibration offset. */
class _Calib {

    /**
     * Draws the TABLE_CORNER_* quad plus a reading grid onto the captured raw camera frame and
     * writes the annotated image to `debug/calib_current_corners.png`.
     */
    @Test
    fun drawCurrentCorners() {
        val img = imread("debug/raw_camera_frame.png")
        println("[CALIB] img=${img.cols()}x${img.rows()}")

        // Current TABLE_CORNER_* constants (TL, TR, BR, BL).
        val corners = listOf(102 to 40, 1522 to 88, 1556 to 1014, 118 to 1010)
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            line(img, CvPoint(a.first, a.second), CvPoint(b.first, b.second), Scalar(0.0, 0.0, 255.0, 0.0), 4, LINE_8, 0)
            circle(img, CvPoint(a.first, a.second), 18, Scalar(0.0, 255.0, 0.0, 0.0), 4, LINE_8, 0)
        }
        // Vertical grid lines every 200px and horizontal every 100px to read off positions.
        for (x in 200 until img.cols() step 200) {
            line(img, CvPoint(x, 0), CvPoint(x, img.rows()), Scalar(255.0, 255.0, 0.0, 0.0), 1, LINE_8, 0)
        }
        for (y in 100 until img.rows() step 100) {
            line(img, CvPoint(0, y), CvPoint(img.cols(), y), Scalar(255.0, 255.0, 0.0, 0.0), 1, LINE_8, 0)
        }

        imwrite("debug/calib_current_corners.png", img)
        println("[CALIB] wrote debug/calib_current_corners.png")
        img.release()
    }
}
