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
