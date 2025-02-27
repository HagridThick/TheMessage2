package com.fengsheng

import com.fengsheng.protos.Common
import com.fengsheng.skill.RoleCache
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

object WinRate {
    private fun IntArray.inc(index: Int? = null) {
        this[0]++
        if (index != null) {
            this[2]++
            this[index]++
        } else {
            this[1]++
        }
    }

    private fun <K> HashMap<K, IntArray>.sum(index: Int): Int {
        var sum = 0
        this.forEach { sum += it.value[index] }
        return sum
    }

    private class Line(val roleName: String, val totalCount: Int, val winRates: DoubleArray)

    private class Gradient(values: Iterable<Double>) {
        val average = values.average()
        val min = values.minOrNull() ?: Double.NaN
        val max = values.maxOrNull() ?: Double.NaN
        fun getColor(value: Double): Color {
            if (average.isNaN()) return Color.WHITE
            val minColor = Color(99, 190, 123)
            val maxColor = Color(245, 105, 104)
            return when {
                value <= min -> minColor
                value >= max -> maxColor
                value == average -> Color.WHITE
                value < average -> {
                    val red = 256 - (256 - minColor.red) * (average - value) / (average - min)
                    val green = 256 - (256 - minColor.green) * (average - value) / (average - min)
                    val blue = 256 - (256 - minColor.blue) * (average - value) / (average - min)
                    Color(red.toInt(), green.toInt(), blue.toInt())
                }

                else -> {
                    val red = 256 - (256 - maxColor.red) * (value - average) / (max - average)
                    val green = 256 - (256 - maxColor.green) * (value - average) / (max - average)
                    val blue = 256 - (256 - maxColor.blue) * (value - average) / (max - average)
                    Color(red.toInt(), green.toInt(), blue.toInt())
                }
            }
        }
    }

    private const val CELL_W = 66
    private const val CELL_H = 18
    private val font = Font("宋体", 0, CELL_H - 3)
    private val columns = listOf(
        "角色", "场次", "总胜率", "军潜", "神秘人",
        "镇压者", "簒夺者", "双重间谍", "诱变者", "先行者", "搅局者", "清道夫",
    )

    fun getWinRateImage(): BufferedImage {
        val appearCount = HashMap<Common.role, IntArray>()
        val winCount = HashMap<Common.role, IntArray>()
        FileInputStream("stat.csv").use { `is` ->
            BufferedReader(InputStreamReader(`is`)).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line.split(Regex(",")).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val role = Common.role.valueOf(a[0])
                    val appear = appearCount.computeIfAbsent(role) { IntArray(10) }
                    val win = winCount.computeIfAbsent(role) { IntArray(10) }
                    val index =
                        if ("Black" == a[2]) Common.secret_task.valueOf(a[3]).number + 3
                        else null
                    appear.inc(index)
                    if (a[1].toBoolean()) win.inc(index)
                }
            }
        }
        val lines = ArrayList<Line>()
        for ((key, value) in appearCount) {
            lines.add(Line(
                RoleCache.getRoleName(key) ?: "",
                value[0],
                DoubleArray(value.size) { i ->
                    winCount[key]!![i] * 100.0 / value[i]
                }
            ))
        }
        lines.sortByDescending { it.winRates.firstOrNull() ?: 0.0 }

        val img = BufferedImage(CELL_W * 12 + 1, CELL_H * (lines.size + 2) + 1, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(img.minX, img.minY, img.width, img.height)
        g.color = Color.BLACK
        g.font = font
        columns.forEachIndexed { i, s ->
            g.drawString(s, i * CELL_W + 3, CELL_H - 3)
        }
        g.drawString("全部", 3, CELL_H * 2 - 3)
        g.drawString(appearCount.sum(0).toString(), CELL_W + 3, CELL_H * 2 - 3)
        val g1 = Gradient((1 until columns.size - 2).mapNotNull { i ->
            val winSum = winCount.sum(i)
            val appearSum = appearCount.sum(i)
            if (appearSum != 0) winSum * 100.0 / appearSum
            else null
        })
        for (i in 0 until columns.size - 2) {
            val winSum = winCount.sum(i)
            val appearSum = appearCount.sum(i)
            if (appearSum != 0) {
                if (i > 0) {
                    g.color = g1.getColor(winSum * 100.0 / appearSum)
                    g.fillRect(CELL_W * (i + 2), CELL_H, CELL_W, CELL_H)
                    g.color = Color.BLACK
                }
                g.drawString("%.2f%%".format(winSum * 100.0 / appearSum), CELL_W * (i + 2) + 3, CELL_H * 2 - 3)
            }
        }
        val g2 = Gradient(lines.map { it.totalCount.toDouble() })
        val gn = Array(columns.size - 2) {
            Gradient(lines.mapNotNull { line -> line.winRates[it].let { w -> if (w.isNaN()) null else w } })
        }
        lines.forEachIndexed { index, line ->
            val row = index + 2
            g.drawString(line.roleName, 3, (row + 1) * CELL_H - 3)
            g.color = g2.getColor(line.totalCount.toDouble())
            g.fillRect(CELL_W, row * CELL_H, CELL_W, CELL_H)
            g.color = Color.BLACK
            g.drawString(line.totalCount.toString(), CELL_W + 3, (row + 1) * CELL_H - 3)
            line.winRates.forEachIndexed { i, v ->
                if (!v.isNaN()) {
                    val col = i + 2
                    g.color = gn[i].getColor(v)
                    g.fillRect(col * CELL_W, row * CELL_H, CELL_W, CELL_H)
                    g.color = Color.BLACK
                    g.drawString("%.2f%%".format(v), col * CELL_W + 3, (row + 1) * CELL_H - 3)
                }
            }
        }
        g.color = Color(205, 204, 200)
        repeat(lines.size + 3) {
            g.drawLine(0, it * CELL_H, img.width, it * CELL_H)
        }
        repeat(columns.size + 1) { i ->
            g.drawLine(i * CELL_W, 0, i * CELL_W, img.height)
        }
        g.dispose()
        return img
    }
}