package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.NextTurn
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 李醒技能【搜辑】：争夺阶段，你可以翻开此角色牌，然后查看一名角色的手牌和待收情报，并且你可以选择其中任意张黑色牌，展示并加入你的手牌。
 */
class SouJi : ActiveSkill {
    override val skillId = SkillId.SOU_JI

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[搜辑]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[搜辑]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[搜辑]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[搜辑]")
            return
        }
        val pb = message as skill_sou_ji_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.deck.discard(fsm.messageCard)
        g.resolve(executeSouJi(fsm, r, target))
    }

    private data class executeSouJi(val fsm: FightPhaseIdle, val r: Player, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}对${target}发动了[搜辑]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_sou_ji_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond * 4 / 3
                    if (p === r) {
                        for (card in target.cards) builder.addCards(card.toPbCard())
                        builder.messageCard = fsm.messageCard.toPbCard()
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(
                            g,
                            {
                                if (p.checkSeq(seq2)) {
                                    val builder2 = skill_sou_ji_b_tos.newBuilder()
                                    builder2.addAllCardIds(target.cards.filter(Black).map { it.id })
                                    builder2.messageCard = false
                                    builder2.seq = seq2
                                    g.tryContinueResolveProtocol(r, builder2.build())
                                }
                            },
                            p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder = skill_sou_ji_b_tos.newBuilder()
                    builder.addAllCardIds(target.cards.filter(Black).map { it.id })
                    if (fsm.messageCard.isBlack() &&
                        r.calculateMessageCardValue(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard) <= 0
                    ) builder.messageCard = true
                    g.tryContinueResolveProtocol(r, builder.build())
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_sou_ji_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val cards = List(message.cardIdsCount) {
                val card = target.findCard(message.getCardIds(it))
                if (card == null) {
                    logger.error("没有这张牌")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
                if (!card.colors.contains(Black)) {
                    logger.error("这张牌不是黑色的")
                    (player as? HumanPlayer)?.sendErrorMessage("这张牌不是黑色的")
                    return null
                }
                card
            }
            if (message.messageCard && !fsm.messageCard.colors.contains(Black)) {
                logger.error("待收情报不是黑色的")
                (player as? HumanPlayer)?.sendErrorMessage("待收情报不是黑色的")
                return null
            }
            r.incrSeq()
            if (cards.isNotEmpty()) {
                logger.info("${r}将${target}的${cards.joinToString()}收归手牌")
                target.cards.removeAll(cards.toSet())
                r.cards.addAll(cards)
                g.addEvent(GiveCardEvent(fsm.whoseTurn, target, r))
            }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_sou_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    for (card in cards) builder.addCards(card.toPbCard())
                    if (message.messageCard) builder.messageCard = fsm.messageCard.toPbCard()
                    p.send(builder.build())
                }
            }
            if (message.messageCard) {
                logger.info("${r}将待收情报${fsm.messageCard}收归手牌，回合结束")
                r.cards.add(fsm.messageCard)
                return ResolveResult(NextTurn(fsm.whoseTurn), true)
            }
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            player.game!!.players.anyoneWillWinOrDie(e) || return false
            val p = player.game!!.players.filter { it!!.alive && player.isEnemy(it) }.run {
                minOf { it!!.cards.count(Black) }.let { min -> filter { it!!.cards.count(Black) == min } }
                    .ifEmpty { this }
            }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_sou_ji_a_tos.newBuilder()
                builder.targetPlayerId = player.getAlternativeLocation(p.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}