package biz.cunning.cunning_document_scanner.fallback.utils

import android.content.Context
import android.graphics.Bitmap
import biz.cunning.cunning_document_scanner.fallback.models.Point
import biz.cunning.cunning_document_scanner.fallback.models.Quad
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class DocumentScannerUtils(private val context: Context) {

    fun detectDocumentCorners(bitmap: Bitmap): Quad {
        // تبدیل Bitmap به Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // تبدیل به تصویر خاکستری
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // اعمال فیلتر گوسی برای کاهش نویز
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // تشخیص لبه‌ها
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)

        // پیدا کردن کانتورها
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        // پیدا کردن بزرگترین کانتور
        var maxContour = contours.maxByOrNull { Imgproc.contourArea(it) }

        if (maxContour == null) {
            // اگر کانتوری پیدا نشد، از گوشه‌های تصویر استفاده کن
            return Quad(
                Point(0f, 0f),
                Point(bitmap.width.toFloat(), 0f),
                Point(bitmap.width.toFloat(), bitmap.height.toFloat()),
                Point(0f, bitmap.height.toFloat())
            )
        }

        // تقریب کانتور به چندضلعی
        val peri = Imgproc.arcLength(MatOfPoint2f(*maxContour.toArray()), true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(MatOfPoint2f(*maxContour.toArray()), approx, 0.02 * peri, true)

        // اگر تعداد نقاط کمتر از 4 است، از گوشه‌های تصویر استفاده کن
        if (approx.total() < 4) {
            return Quad(
                Point(0f, 0f),
                Point(bitmap.width.toFloat(), 0f),
                Point(bitmap.width.toFloat(), bitmap.height.toFloat()),
                Point(0f, bitmap.height.toFloat())
            )
        }

        // مرتب‌سازی نقاط به ترتیب ساعتگرد از گوشه بالا-چپ
        val points = approx.toList().sortedWith(compareBy<Point> { it.y }.thenBy { it.x })

        // تبدیل نقاط به Quad
        return Quad(
            Point(points[0].x.toFloat(), points[0].y.toFloat()),
            Point(points[1].x.toFloat(), points[1].y.toFloat()),
            Point(points[2].x.toFloat(), points[2].y.toFloat()),
            Point(points[3].x.toFloat(), points[3].y.toFloat())
        )
    }

    fun cropDocument(bitmap: Bitmap, quad: Quad): Bitmap {
        // تبدیل Bitmap به Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // تبدیل نقاط Quad به MatOfPoint2f
        val srcPoints = MatOfPoint2f(
            Point(quad.topLeft.x.toDouble(), quad.topLeft.y.toDouble()),
            Point(quad.topRight.x.toDouble(), quad.topRight.y.toDouble()),
            Point(quad.bottomRight.x.toDouble(), quad.bottomRight.y.toDouble()),
            Point(quad.bottomLeft.x.toDouble(), quad.bottomLeft.y.toDouble())
        )

        // محاسبه عرض و ارتفاع تصویر نهایی
        val width = Math.max(
            Math.hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y),
            Math.hypot(quad.bottomRight.x - quad.bottomLeft.x, quad.bottomRight.y - quad.bottomLeft.y)
        ).toInt()

        val height = Math.max(
            Math.hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y),
            Math.hypot(quad.bottomRight.x - quad.topRight.x, quad.bottomRight.y - quad.topRight.y)
        ).toInt()

        // تعریف نقاط مقصد
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()),
            Point(0.0, height.toDouble())
        )

        // محاسبه ماتریس تبدیل
        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

        // اعمال تبدیل
        val warped = Mat()
        Imgproc.warpPerspective(mat, warped, transform, Size(width.toDouble(), height.toDouble()))

        // تبدیل Mat به Bitmap
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warped, result)

        return result
    }
}