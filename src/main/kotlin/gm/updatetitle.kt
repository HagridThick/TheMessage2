package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.Statistics
import java.util.function.Function

class updatetitle : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val title = form.getOrDefault("title", "")
            if (title.length > 20) return "{\"error\": \"称号太长\"}"
            if (name.contains(",") || name.contains("·")) return "{\"error\": \"称号中含有非法字符\"}"
            val result = Statistics.updateTitle(name, title)
            Game.playerNameCache[name]?.playerTitle = title
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}