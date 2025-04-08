package biz.cunning.cunning_document_scanner.fallback.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

class OpenCVDocumentScanner(private val context: Context) {
    private val TAG = "OpenCVDocumentScanner"

    init {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV")
        }
    }

    fun detectDocument(bitmap: Bitmap): Bitmap? {
        try {
            // تبدیل Bitmap به Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // تبدیل به خاکستری
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            // اعمال Gaussian blur
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            // تشخیص لبه با Canny
            val edges = Mat()
            Imgproc.Canny(gray, edges, 75.0, 200.0)

            // پیدا کردن کانتورها
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // پیدا کردن بزرگترین کانتور (احتمالاً صفحه)
            var maxArea = 0.0
            var maxContourIndex = -1

            for (i in contours.indices) {
                val contour = contours[i]
                val area = Imgproc.contourArea(contour)
                if (area > maxArea) {
                    maxArea = area
                    maxContourIndex = i
                }
            }

            if (maxContourIndex != -1) {
                val maxContour = contours[maxContourIndex]
                val peri = Imgproc.arcLength(MatOfPoint2f(*maxContour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*maxContour.toArray()), approx, 0.02 * peri, true)

                if (approx.total() == 4L) {
                    // تبدیل نقاط به آرایه
                    val points = Array<Point>(4) { Point() }
                    approx.get(0, 0, points)

                    // مرتب‌سازی نقاط
                    val sortedPoints = sortPoints(points)

                    // محاسبه عرض و ارتفاع
                    val width = Math.max(
                        distance(sortedPoints[0], sortedPoints[1]),
                        distance(sortedPoints[2], sortedPoints[3])
                    ).toInt()
                    val height = Math.max(
                        distance(sortedPoints[0], sortedPoints[3]),
                        distance(sortedPoints[1], sortedPoints[2])
                    ).toInt()

                    // ایجاد ماتریس تبدیل
                    val src = MatOfPoint2f(
                        sortedPoints[0],
                        sortedPoints[1],
                        sortedPoints[2],
                        sortedPoints[3]
                    )
                    val dst = MatOfPoint2f(
                        Point(0.0, 0.0),
                        Point(width - 1.0, 0.0),
                        Point(width - 1.0, height - 1.0),
                        Point(0.0, height - 1.0)
                    )

                    // اعمال تبدیل
                    val transform = Imgproc.getPerspectiveTransform(src, dst)
                    val warped = Mat()
                    Imgproc.warpPerspective(mat, warped, transform, Size(width.toDouble(), height.toDouble()))

                    // تبدیل به Bitmap
                    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(warped, resultBitmap)

                    // پاکسازی
                    mat.release()
                    gray.release()
                    edges.release()
                    hierarchy.release()
                    warped.release()
                    transform.release()

                    return resultBitmap
                }
            }

            // پاکسازی
            mat.release()
            gray.release()
            edges.release()
            hierarchy.release()

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting document", e)
            return null
        }
    }

    private fun sortPoints(points: Array<Point>): Array<Point> {
        val center = Point(
            points.sumOf { it.x } / 4.0,
            points.sumOf { it.y } / 4.0
        )

        val topLeft = points.filter { it.x < center.x && it.y < center.y }.firstOrNull()
        val topRight = points.filter { it.x > center.x && it.y < center.y }.firstOrNull()
        val bottomRight = points.filter { it.x > center.x && it.y > center.y }.firstOrNull()
        val bottomLeft = points.filter { it.x < center.x && it.y > center.y }.firstOrNull()

        return arrayOf(
            topLeft ?: points[0],
            topRight ?: points[1],
            bottomRight ?: points[2],
            bottomLeft ?: points[3]
        )
    }

    private fun distance(p1: Point, p2: Point): Double {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
    }
} 