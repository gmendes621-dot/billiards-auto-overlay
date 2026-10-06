package com.example.billiardsoverlay

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class DetectedObject(val x: Float, val y: Float, val radius: Float, val type: String, val confidence: Float)

data class DetectionResult(
    val tableLeft: Float, val tableTop: Float, val tableRight: Float, val tableBottom: Float,
    val pockets: List<DetectedObject>, val balls: List<DetectedObject>,
    // direção da tacada (dx, dy unitário, len = comprimento do taco / largura da imagem); null se não achou o taco
    val aim: FloatArray? = null,
    // tamanho (px) da imagem analisada; o JS precisa dele para converter a direção do taco para o canvas
    val imgW: Int = 0, val imgH: Int = 0
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
        .put("pockets", pockets.arr()).put("balls", balls.arr())
        .put("img", JSONObject().put("w", imgW).put("h", imgH))
        .also { j ->
            aim?.let { j.put("aim", JSONObject().put("dx", it[0].toDouble()).put("dy", it[1].toDouble()).put("len", it[2].toDouble())) }
        }.toString()
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

    /** Motivo da última falha, para mostrar no overlay (null se a última análise achou a mesa). */
    var lastFailure: String? = null
        private set

    private fun fail(msg: String): DetectionResult? {
        lastFailure = msg
        return null
    }

    fun process(image: Image): DetectionResult? {
        val bmp = imageToBitmap(image) ?: return fail("quadro de captura vazio")
        val w = min(800, bmp.width)
        val h = max(1, bmp.height * w / bmp.width)
        val small = if (w == bmp.width) bmp else Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        lastFailure = null
        return analyze(px, w, h)
            ?: fail("${lastFailure ?: "?"} [captura ${image.width}x${image.height}]")
    }

    private fun analyze(px: IntArray, w: Int, h: Int): DetectionResult? {
        // 1) matiz dominante (a mesa é a maior área saturada no centro da tela)
        val hist = FloatArray(36)
        val hCos = FloatArray(36)
        val hSin = FloatArray(36)
        var lit = 0
        var sampled = 0
        for (y in h / 6 until h * 5 / 6 step 2) for (x in w / 6 until w * 5 / 6 step 2) {
            Color.colorToHSV(px[y * w + x], hsv)
            sampled++
            if (hsv[2] > 0.1f) lit++
            if (hsv[1] > 0.25f && hsv[2] > 0.2f) {
                val b = min(35, (hsv[0] / 10f).toInt())
                val wt = hsv[1] * hsv[2]
                val a = Math.toRadians(hsv[0].toDouble())
                hist[b] += wt
                hCos[b] += (cos(a) * wt).toFloat()
                hSin[b] += (sin(a) * wt).toFloat()
            }
        }
        if (lit < sampled * 0.05f) return fail("a tela capturada está preta (o jogo pode estar bloqueando a captura)")
        var bi = 0
        var bv = 0f
        for (i in 0 until 36) {
            val v = hist[(i + 35) % 36] + hist[i] + hist[(i + 1) % 36]
            if (v > bv) { bv = v; bi = i }
        }
        if (bv <= 0f) return fail("nenhuma cor saturada no centro da tela (feltro muito cinza/escuro?)")
        // matiz médio real dentro das 3 faixas vencedoras (o centro da faixa pode errar até 15°)
        var sc = 0f
        var ss = 0f
        for (k in -1..1) { val j = (bi + k + 36) % 36; sc += hCos[j]; ss += hSin[j] }
        var feltHue = Math.toDegrees(atan2(ss, sc).toDouble()).toFloat()
        if (feltHue < 0f) feltHue += 360f

        // 2) máscara do feltro + limites da mesa
        val felt = BooleanArray(w * h)
        val rowC = IntArray(h)
        for (y in 0 until h) for (x in 0 until w) {
            Color.colorToHSV(px[y * w + x], hsv)
            var dh = abs(hsv[0] - feltHue)
            if (dh > 180f) dh = 360f - dh
            if (dh < 22f && hsv[1] > 0.2f && hsv[2] > 0.12f) { felt[y * w + x] = true; rowC[y]++ }
        }
        val hueTxt = "matiz ${feltHue.toInt()}°"
        val rows = longestRun(rowC, (rowC.max() * 0.4f).toInt(), h / 50)
            ?: return fail("feltro ($hueTxt) não forma uma faixa de linhas")
        val top = rows.first
        val bottom = rows.second
        val colC = IntArray(w)
        for (y in top..bottom) for (x in 0 until w) if (felt[y * w + x]) colC[x]++
        val cols = longestRun(colC, (colC.max() * 0.4f).toInt(), w / 50)
            ?: return fail("feltro ($hueTxt) não forma uma faixa de colunas")
        val left = cols.first
        val right = cols.second
        val tw = right - left
        val th = bottom - top
        val box = "$hueTxt, mesa ${tw}x$th de ${w}x$h"
        if (tw < w * 0.3f) return fail("mesa estreita demais ($box)")
        if (th < 20) return fail("mesa baixa demais ($box)")
        val ratio = tw.toFloat() / th
        if (ratio !in 1.3f..3.2f) return fail("proporção ${"%.2f".format(ratio)} fora de 1.3–3.2 ($box)")

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

        // 5) taco -> direção da tacada
        val cueBall = balls.filter { it.type == "cue" }.maxByOrNull { it.confidence }
        val aim = cueBall?.let { findAim(felt, w, h, left, top, right, bottom, it) }

        val pr = max(2f * med, w * 0.02f) / w
        val pockets = pk.map { DetectedObject(it.first.toFloat() / w, it.second.toFloat() / h, pr, "pocket", 0.6f) }
        return DetectionResult(
            left.toFloat() / w, top.toFloat() / h, right.toFloat() / w, bottom.toFloat() / h, pockets, balls, aim, w, h
        )
    }

    /**
     * Acha o taco: lança 720 raios a partir da bola branca e procura o mais longo que passa por pixels
     * "não feltro" com espessura >= 3px (linhas finas, como as do próprio overlay, são ignoradas).
     * Retorna a direção da TACADA = oposta à do taco (do taco para a bola).
     */
    private fun findAim(felt: BooleanArray, w: Int, h: Int, l: Int, t: Int, r: Int, b: Int, cue: DetectedObject): FloatArray? {
        val cx = cue.x * w
        val cy = cue.y * h
        val rp = cue.radius * w
        val n = 720
        val lens = FloatArray(n)
        val s0 = rp * 1.2f
        val sMaxStart = rp * 7f
        fun solid(x: Float, y: Float) = !felt[y.toInt() * w + x.toInt()]
        for (k in 0 until n) {
            val a = k * 2.0 * PI / n
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            var s = s0
            var first = -1f
            var last = -1f
            var gap = 0
            while (true) {
                val x = cx + dx * s
                val y = cy + dy * s
                if (x < l + 2 || x > r - 2 || y < t + 2 || y > b - 2) break
                val thick = solid(x, y) && solid(x - dy * 1.2f, y + dx * 1.2f) && solid(x + dy * 1.2f, y - dx * 1.2f)
                if (thick) {
                    if (first < 0) first = s
                    last = s
                    gap = 0
                } else {
                    gap++
                    if (first < 0 && s > sMaxStart) break
                    if (first >= 0 && gap > 4) break
                }
                s += 1f
            }
            lens[k] = if (first < 0) 0f else last - first
        }
        var best = 0
        for (k in 1 until n) if (lens[k] > lens[best]) best = k
        if (lens[best] < rp * 5f) return null
        var sx = 0.0
        var sy = 0.0
        for (k in 0 until n) {
            val dk = minOf(abs(k - best), n - abs(k - best))
            if (dk <= 16 && lens[k] >= lens[best] * 0.85f) {
                val a = k * 2.0 * PI / n
                sx += cos(a); sy += sin(a)
            }
        }
        val m = sqrt(sx * sx + sy * sy)
        if (m < 1e-6) return null
        return floatArrayOf((-sx / m).toFloat(), (-sy / m).toFloat(), lens[best] / w)
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

    fun imageToBitmap(image: Image): Bitmap? {
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
