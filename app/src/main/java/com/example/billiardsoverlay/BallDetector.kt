package com.example.billiardsoverlay

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class DetectedObject(val x: Float, val y: Float, val radius: Float, val type: String, val confidence: Float)

data class DetectionResult(
    val tableLeft: Float, val tableTop: Float, val tableRight: Float, val tableBottom: Float,
    val pockets: List<DetectedObject>, val balls: List<DetectedObject>
) {
    private fun List<DetectedObject>.arr() = JSONArray().also { a ->
        forEach {
            a.put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())
                .put("r", it.radius.toDouble()).put("type", it.type).put("confidence", it.confidence.toDouble()))
        }
    }

    fun toJson(): String = JSONObject()
        .put("table", JSONObject().put("left", tableLeft.toDouble()).put("top", tableTop.toDouble())
            .put("right", tableRight.toDouble()).put("bottom", tableBottom.toDouble()))
        .put("pockets", pockets.arr()).put("balls", balls.arr()).toString()
}

/**
 * Detector independente da cor da mesa:
 * 1) acha a cor dominante (matiz) da mesa automaticamente;
 * 2) delimita a mesa por projeção de linhas/colunas;
 * 3) acha bolas como picos da transformada de distância (separa bolas encostadas e ignora taco/linhas finas);
 * 4) classifica: cue (branca), eight (preta), solid (lisa), stripe (listrada).
 */
class BallDetector {
    private val hsv = FloatArray(3)

    fun process(image: Image): DetectionResult? {
        val bmp = imageToBitmap(image) ?: return null
        val w = min(800, bmp.width)
        val h = max(1, bmp.height * w / bmp.width)
        val small = if (w == bmp.width) bmp else Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        return analyze(px, w, h)
    }

    private fun analyze(px: IntArray, w: Int, h: Int): DetectionResult? {
        // 1) matiz dominante (a mesa é a maior área saturada no centro da tela)
        val hist = FloatArray(36)
        for (y in h / 6 until h * 5 / 6 step 2) for (x in w / 6 until w * 5 / 6 step 2) {
            Color.colorToHSV(px[y * w + x], hsv)
            if (hsv[1] > 0.25f && hsv[2] > 0.2f) hist[min(35, (hsv[0] / 10f).toInt())] += hsv[1] * hsv[2]
        }
        var bi = 0
        var bv = 0f
        for (i in 0 until 36) {
            val v = hist[(i + 35) % 36] + hist[i] + hist[(i + 1) % 36]
            if (v > bv) { bv = v; bi = i }
        }
        if (bv <= 0f) return null
        val feltHue = bi * 10f + 5f

        // 2) máscara do feltro + limites da mesa
        val felt = BooleanArray(w * h)
        val rowC = IntArray(h)
        for (y in 0 until h) for (x in 0 until w) {
            Color.colorToHSV(px[y * w + x], hsv)
            var dh = abs(hsv[0] - feltHue)
            if (dh > 180f) dh = 360f - dh
            if (dh < 22f && hsv[1] > 0.2f && hsv[2] > 0.12f) { felt[y * w + x] = true; rowC[y]++ }
        }
        val rows = longestRun(rowC, (rowC.max() * 0.4f).toInt(), h / 50) ?: return null
        val top = rows.first
        val bottom = rows.second
        val colC = IntArray(w)
        for (y in top..bottom) for (x in 0 until w) if (felt[y * w + x]) colC[x]++
        val cols = longestRun(colC, (colC.max() * 0.4f).toInt(), w / 50) ?: return null
        val left = cols.first
        val right = cols.second
        val tw = right - left
        val th = bottom - top
        if (tw < w * 0.3f || th < 20 || tw.toFloat() / th !in 1.3f..2.6f) return null

        // 3) transformada de distância dos pixels "não feltro" dentro da mesa
        val big = 1e6f
        val d = FloatArray(w * h)
        for (y in top..bottom) for (x in left..right) if (!felt[y * w + x]) d[y * w + x] = big
        for (x in 0 until w) { d[x] = 0f; d[(h - 1) * w + x] = 0f }
        for (y in 0 until h) { d[y * w] = 0f; d[y * w + w - 1] = 0f }
        val s2 = 1.4142f
        for (y in 1 until h) for (x in 1 until w - 1) {
            val i = y * w + x
            if (d[i] > 0f) d[i] = min(min(d[i], d[i - 1] + 1f), min(d[i - w] + 1f, min(d[i - w - 1] + s2, d[i - w + 1] + s2)))
        }
        for (y in h - 2 downTo 0) for (x in w - 2 downTo 1) {
            val i = y * w + x
            if (d[i] > 0f) d[i] = min(min(d[i], d[i + 1] + 1f), min(d[i + w] + 1f, min(d[i + w - 1] + s2, d[i + w + 1] + s2)))
        }

        // caçapas (aproximadas: cantos e meio das laterais maiores)
        val pk = listOf(
            left to top, (left + right) / 2 to top, right to top,
            left to bottom, (left + right) / 2 to bottom, right to bottom
        )

        // 4) picos = centros das bolas
        val minR = w * 0.006f
        val maxR = w * 0.05f
        val cand = ArrayList<Int>()
        for (y in top + 1 until bottom) for (x in left + 1 until right) {
            val i = y * w + x
            val v = d[i]
            if (v < minR || v > maxR) continue
            if (v >= d[i - 1] && v >= d[i + 1] && v >= d[i - w] && v >= d[i + w] &&
                v >= d[i - w - 1] && v >= d[i - w + 1] && v >= d[i + w - 1] && v >= d[i + w + 1]
            ) cand += i
        }
        cand.sortByDescending { d[it] }
        val acc = ArrayList<Int>()
        for (i in cand) {
            val cx = i % w
            val cy = i / w
            if (pk.any { hypot((cx - it.first).toFloat(), (cy - it.second).toFloat()) < tw * 0.05f }) continue
            if (acc.any { j -> hypot((cx - j % w).toFloat(), (cy - j / w).toFloat()) < max(d[i], d[j]) * 1.15f }) continue
            acc += i
        }
        val med = if (acc.isEmpty()) w * 0.012f else acc.map { d[it] }.sorted()[acc.size / 2]
        val keep = acc.filter { d[it] in med * 0.7f..med * 1.4f }

        val balls = ArrayList<DetectedObject>()
        for (i in keep) {
            val cx = i % w
            val cy = i / w
            val r = d[i] + 0.5f
            val ri = r.toInt()
            var tot = 0; var nf = 0; var white = 0; var dark = 0; var col = 0; var rTot = 0; var rWhite = 0
            for (y in (cy - ri)..(cy + ri)) for (x in (cx - ri)..(cx + ri)) {
                if (x < 0 || y < 0 || x >= w || y >= h) continue
                val dd = hypot((x - cx).toFloat(), (y - cy).toFloat())
                if (dd > r) continue
                tot++
                if (!felt[y * w + x]) nf++
                Color.colorToHSV(px[y * w + x], hsv)
                val isW = hsv[1] < 0.2f && hsv[2] > 0.7f
                if (isW) white++
                if (hsv[2] < 0.28f) dark++
                if (hsv[1] > 0.35f && hsv[2] > 0.25f) col++
                if (dd >= r * 0.55f) { rTot++; if (isW) rWhite++ }
            }
            if (tot == 0 || nf.toFloat() / tot < 0.7f) continue
            val wf = white.toFloat() / tot
            val df = dark.toFloat() / tot
            val cf = col.toFloat() / tot
            val rwf = if (rTot > 0) rWhite.toFloat() / rTot else 0f
            val type = when {
                df > 0.55f -> "eight"
                wf > 0.8f || (wf > 0.55f && cf < 0.10f) -> "cue"
                rwf > 0.2f && wf > 0.22f -> "stripe"
                cf > 0.4f -> "solid"
                wf > 0.3f -> "stripe"
                else -> "unknown"
            }
            balls += DetectedObject(cx.toFloat() / w, cy.toFloat() / h, r / w, type, nf.toFloat() / tot)
        }

        val pr = max(2f * med, w * 0.02f) / w
        val pockets = pk.map { DetectedObject(it.first.toFloat() / w, it.second.toFloat() / h, pr, "pocket", 0.6f) }
        return DetectionResult(
            left.toFloat() / w, top.toFloat() / h, right.toFloat() / w, bottom.toFloat() / h, pockets, balls
        )
    }

    private fun longestRun(c: IntArray, thr: Int, maxGap: Int): Pair<Int, Int>? {
        if (thr <= 0) return null
        var bs = -1; var be = -1; var s = -1; var last = -1
        for (i in c.indices) {
            if (c[i] < thr) continue
            if (s < 0) s = i
            else if (i - last > maxGap + 1) { if (last - s > be - bs) { bs = s; be = last }; s = i }
            last = i
        }
        if (s >= 0 && last - s > be - bs) { bs = s; be = last }
        return if (bs < 0) null else bs to be
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val rowPadding = plane.rowStride - plane.pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        plane.buffer.rewind()
        bitmap.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}
