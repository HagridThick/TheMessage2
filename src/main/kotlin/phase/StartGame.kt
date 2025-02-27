package com.fengsheng.phase

import com.fengsheng.*
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 游戏马上开始
 */
data class StartGame(val game: Game) : Fsm {
    override fun resolve(): ResolveResult? {
        Game.GameCache[game.id] = game
        val players = game.players
        log.info("游戏开始了，场上的角色依次是：${players.contentToString()}")
        game.deck.init(players.size)
        val whoseTurn = Random.nextInt(players.size)
        for (i in players.indices) players[(whoseTurn + i) % players.size]!!.init()
        for (i in players.indices) players[(whoseTurn + i) % players.size]!!.draw(Config.HandCardCountBegin)
        GameExecutor.post(game, { game.resolve(DrawPhase(players[whoseTurn]!!)) }, 1, TimeUnit.SECONDS)
        return null
    }

    companion object {
        private val log = Logger.getLogger(StartGame::class.java)
    }
}