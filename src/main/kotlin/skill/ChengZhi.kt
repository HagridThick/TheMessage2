package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.DieSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 顾小梦技能【承志】：一名其他角色死亡前，若此角色牌已翻开，则你获得其所有手牌，并查看其身份牌，你可以获得该身份牌，并将你原本的身份牌面朝下移出游戏。
 */
class ChengZhi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.CHENG_ZHI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm
        if (fsm !is DieSkill) return null
        if (fsm.askWhom === fsm.diedQueue[fsm.diedIndex] || fsm.askWhom.findSkill(skillId) == null) return null
        if (!fsm.askWhom.alive) return null
        if (!fsm.askWhom.roleFaceUp) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeChengZhi(fsm), true)
    }

    private data class executeChengZhi(val fsm: DieSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            val whoDie = fsm.diedQueue[fsm.diedIndex]
            val cards = whoDie.cards.toTypedArray()
            whoDie.cards.clear()
            r.cards.addAll(cards)
            log.info("${r}发动了[承志]，获得了${whoDie}的${cards.contentToString()}")
            if (whoDie.identity == color.Has_No_Identity) return ResolveResult(fsm, true)
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_cheng_zhi_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    builder.diePlayerId = player.getAlternativeLocation(whoDie.location)
                    if (player === r) {
                        for (card in cards) builder.addCards(card.toPbCard())
                        builder.identity = whoDie.identity
                        builder.secretTask = whoDie.secretTask
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        player.timeout =
                            GameExecutor.post(
                                r.game!!,
                                {
                                    val builder2 = skill_cheng_zhi_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq2
                                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                                },
                                player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                                TimeUnit.SECONDS
                            )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) GameExecutor.post(
                r.game!!,
                { r.game!!.tryContinueResolveProtocol(r, skill_cheng_zhi_tos.newBuilder().setEnable(true).build()) },
                2,
                TimeUnit.SECONDS
            )
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.askWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cheng_zhi_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.askWhom
            val whoDie = fsm.diedQueue[fsm.diedIndex]
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_cheng_zhi_toc.newBuilder()
                        builder.enable = false
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.diePlayerId = p.getAlternativeLocation(whoDie.location)
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm, true)
            }
            r.incrSeq()
            r.identity = whoDie.identity
            r.secretTask = whoDie.secretTask
            whoDie.identity = color.Has_No_Identity
            log.info("${r}获得了${whoDie}的身份牌")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cheng_zhi_toc.newBuilder()
                    builder.enable = true
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.diePlayerId = p.getAlternativeLocation(whoDie.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeChengZhi::class.java)
        }
    }
}