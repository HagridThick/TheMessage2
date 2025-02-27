package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_lian_min_toc
import com.fengsheng.protos.Role.skill_lian_min_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 白菲菲技能【怜悯】：你传出的非黑色情报被接收后，可以从你或接收者的情报区选择一张黑色情报加入你的手牌。
 */
class LianMin : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.LIAN_MIN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        fsm.askWhom === fsm.sender || return null
        fsm.sender.findSkill(skillId) != null || return null
        !fsm.messageCard.isBlack() || return null
        fsm.sender.getSkillUseCount(skillId) == 0 || return null
        fsm.sender.addSkillUseCount(skillId)
        return ResolveResult(executeLianMin(fsm), true)
    }

    private data class executeLianMin(val fsm: ReceivePhaseSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.sender.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.sender)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.sender) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_lian_min_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.sender
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]
            if (target !== r && target !== fsm.inFrontOfWhom) {
                log.error("只能以自己或者情报接收者为目标")
                (player as? HumanPlayer)?.sendErrorMessage("只能以自己或者情报接收者为目标")
                return null
            }
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            if (!card.colors.contains(color.Black)) {
                log.error("你选择的不是黑色情报")
                (player as? HumanPlayer)?.sendErrorMessage("你选择的不是黑色情报")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[怜悯]，将${target}面前的${card}加入了手牌")
            target.deleteMessageCard(card.id)
            r.cards.add(card)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_lian_min_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeLianMin::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeLianMin) return false
            val p = fsm0.fsm.sender
            for (target in arrayOf(p, fsm0.fsm.inFrontOfWhom)) {
                if (!target.alive || p.isEnemy(target)) continue
                val card = target.messageCards.find { it.colors.contains(color.Black) } ?: continue
                GameExecutor.post(p.game!!, {
                    val builder = skill_lian_min_tos.newBuilder()
                    builder.cardId = card.id
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.game!!.tryContinueResolveProtocol(p, builder.build())
                }, 2, TimeUnit.SECONDS)
                return true
            }
            return false
        }
    }
}