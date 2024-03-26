package com.fengsheng.gm

import com.fengsheng.ScoreFactory
import com.fengsheng.Statistics
import java.util.function.Function

class Getscore : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val playerInfo = Statistics.getPlayerInfo(name)
            if (playerInfo == null) {
                "{\"result\": \"${name}已身死道消\"}"
            } else {
                val days = ((System.currentTimeMillis() - playerInfo.lastTime) / (24 * 3600000L)).toInt()
                val decay = days / 7 * 20
                val score = (playerInfo.score - decay).coerceAtLeast(0)
                val rank = ScoreFactory.getRankNameByScore(score)
                val winRate =
                    if (playerInfo.gameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.winCount * 100.0 / playerInfo.gameCount)
                "{\"result\": \"$name·$rank·$score，总场次：${playerInfo.gameCount}，胜率：$winRate\"}"
            }
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
