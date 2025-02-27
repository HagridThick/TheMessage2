package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessageV3
import kotlinx.coroutines.runBlocking
import org.apache.log4j.Logger
import java.nio.charset.StandardCharsets

class join_room_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            player.sendErrorMessage("你已经在房间里了")
            return
        }
        val pb = message as Fengsheng.join_room_tos
        // 客户端版本号不对，直接返回错误信息
        if (pb.version < Config.ClientVersion.get()) {
            player.sendErrorMessage("客户端版本号过低，请更新客户端")
            return
        }
        val playerName = pb.name
        if (playerName.toByteArray(StandardCharsets.UTF_8).size > 24) {
            player.sendErrorMessage("名字太长了")
            return
        }
        if (playerName.isBlank() || playerName.contains(",") ||
            playerName.contains("\n") || playerName.contains("\r")
        ) {
            player.sendErrorMessage("用户名不合法")
            return
        }
        val playerInfo =
            try {
                Statistics.login(playerName, pb.password)
            } catch (e: Exception) {
                e.message?.let { player.sendErrorMessage(it) }
                return
            }
        val oldPlayer = Game.playerNameCache[playerName]
        val game = oldPlayer?.game
        if (game != null) {
            val continueLogin = runBlocking {
                GameExecutor.call(game) {
                    if (game !== oldPlayer.game) {
                        log.info("${oldPlayer}登录异常")
                        oldPlayer.sendErrorMessage("登录异常，请稍后重试")
                        false
                    } else if (game.isStarted && !game.isEnd) { // 断线重连
                        oldPlayer.send(Fengsheng.notify_kicked_toc.getDefaultInstance())
                        Game.exchangePlayer(oldPlayer, player)
                        oldPlayer.setAutoPlay(false)
                        if (oldPlayer.needWaitLoad) {
                            oldPlayer.isReconnecting = true
                            player.send(Fengsheng.game_start_toc.getDefaultInstance())
                        } else {
                            oldPlayer.reconnect()
                        }
                        log.info("${oldPlayer}断线重连成功")
                        false
                    } else true
                }
            }
            if (!continueLogin) return
        }
        if (Game.GameCache.size > Config.MaxRoomCount) {
            player.sendErrorMessage("房间已满，请稍后再试")
            return
        }
        val newGame = Game.newGame
        GameExecutor.post(newGame) {
            if (player.game !== null) {
                log.warn("${player}登录异常")
                player.sendErrorMessage("登录异常，请稍后重试")
                return@post
            }
            var failed = false
            var oldPlayer2: HumanPlayer? = null
            Game.playerNameCache.compute(playerName) { _, v ->
                if (v == null) return@compute player // 抢注成功
                if (v.game !== newGame) { // 已经被其它房间注册了
                    failed = true
                    v
                } else {
                    oldPlayer2 = v // 被这个房间注册了，直接顶号
                    player
                }
            }
            if (failed) {
                log.warn("${player}登录异常")
                player.sendErrorMessage("登录异常，请稍后重试")
                return@post
            }
            oldPlayer2?.apply {// 顶号
                log.info("${playerName}离开了房间")
                newGame.players[location] = null
                newGame.cancelStartTimer()
                this.game = null
                send(Fengsheng.notify_kicked_toc.getDefaultInstance())
                val builder = Fengsheng.leave_room_toc.newBuilder()
                builder.position = location
                val reply = builder.build()
                newGame.players.forEach { (it as? HumanPlayer)?.send(reply) }
            }
            if (!Config.IsGmEnable) {
                val humanCount = newGame.players.count { it is HumanPlayer }
                if (humanCount >= 1) newGame.removeAllRobot()
                if (humanCount >= 2) newGame.ensure5Position()
            }
            player.playerName = playerName
            player.playerTitle = playerInfo.title
            val count = PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
            if (!newGame.onPlayerJoinRoom(player, count)) {
                Game.playerNameCache.remove(playerName) // 登录失败的话，要把注册清掉
                player.sendErrorMessage("房间已满，请稍后再试")
                return@post
            }
            player.game = newGame
            val builder = Fengsheng.get_room_info_toc.newBuilder()
            builder.myPosition = player.location
            builder.onlineCount = Game.playerNameCache.size
            for (p in player.game!!.players) {
                if (p == null) {
                    builder.addNames("")
                    builder.addWinCounts(0)
                    builder.addGameCounts(0)
                    builder.addRanks("")
                    builder.addScores(0)
                    continue
                }
                val name = p.playerName
                val score = Statistics.getScore(name) ?: 0
                val rank = if (p is HumanPlayer) ScoreFactory.getRankNameByScore(score) else ""
                builder.addNames(name)
                val c =
                    if (p is HumanPlayer) Statistics.getPlayerGameCount(p.playerName)
                    else Statistics.totalPlayerGameCount.random()
                builder.addWinCounts(c.winCount)
                builder.addGameCounts(c.gameCount)
                builder.addRanks(rank)
                builder.addScores(score)
            }
            builder.notice = Config.Notice.get() + "\n\n" + Statistics.rankList.get()
            player.send(builder.build())
        }
    }

    companion object {
        private val log = Logger.getLogger(join_room_tos::class.java)

        private fun Game.removeAllRobot() {
            for ((index, robotPlayer) in players.withIndex()) {
                if (robotPlayer is RobotPlayer) {
                    players[index] = null
                    log.info("${robotPlayer.playerName}离开了房间")
                    val builder = Fengsheng.leave_room_toc.newBuilder()
                    builder.position = robotPlayer.location
                    val reply = builder.build()
                    for (p in players) {
                        if (p is HumanPlayer) {
                            p.send(reply)
                        }
                    }
                }
            }
        }

        private fun Game.ensure5Position() {
            if (players.size >= 5) return
            players = Array(5) {
                if (it < players.size) {
                    players[it]
                } else {
                    for (p in players)
                        (p as? HumanPlayer)?.send(Fengsheng.add_one_position_toc.getDefaultInstance())
                    null
                }
            }
        }
    }
}