package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_cheng_qing_toc
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

class ChengQing : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为澄清使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Cheng_Qing

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (r.location in g.diaoHuLiShanPlayers) {
            log.error("你被调虎离山了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被调虎离山了，不能出牌")
            return false
        }
        if (type in g.qiangLingTypes) {
            log.error("澄清被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("澄清被禁止使用了")
            return false
        }
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        val fsm = g.fsm
        if (fsm is MainPhaseIdle) {
            if (r !== fsm.player) {
                log.error("澄清的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机不对")
                return false
            }
        } else if (fsm is WaitForChengQing) {
            if (r !== fsm.askWhom) {
                log.error("澄清的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机不对")
                return false
            }
            if (target !== fsm.whoDie) {
                log.error("正在求澄清的人是${fsm.whoDie}")
                (r as? HumanPlayer)?.sendErrorMessage("正在求澄清的人是${fsm.whoDie}")
                return false
            }
        } else {
            log.error("澄清的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机不对")
            return false
        }
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        val targetCard = target.messageCards.find { c -> c.id == targetCardId }
        if (targetCard == null) {
            log.error("没有这张情报")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
            return false
        }
        if (!targetCard.isBlack()) {
            log.error("澄清只能对黑情报使用")
            (r as? HumanPlayer)?.sendErrorMessage("澄清只能对黑情报使用")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val fsm = g.fsm
        val resolveFunc = { _: Boolean ->
            val targetCard = target.deleteMessageCard(targetCardId)!!
            log.info("${target}面前的${targetCard}被置入弃牌堆")
            g.deck.discard(targetCard)
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = use_cheng_qing_toc.newBuilder()
                    builder.card = toPbCard()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.targetPlayerId = player.getAlternativeLocation(target.location)
                    builder.targetCardId = targetCardId
                    player.send(builder.build())
                }
            }
            if (fsm is MainPhaseIdle) {
                OnFinishResolveCard(r, target, getOriginCard(), card_type.Cheng_Qing, fsm)
            } else {
                val newFsm = UseChengQingOnDying(fsm as WaitForChengQing)
                OnFinishResolveCard(r, target, getOriginCard(), card_type.Cheng_Qing, newFsm)
            }
        }
        if (fsm is MainPhaseIdle) g.resolve(
            OnUseCard(fsm.player, r, target, getOriginCard(), card_type.Cheng_Qing, resolveFunc, fsm)
        ) else if (fsm is WaitForChengQing) g.resolve(
            OnUseCard(fsm.whoseTurn, r, target, getOriginCard(), card_type.Cheng_Qing, resolveFunc, fsm)
        )
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}澄清"
    }

    companion object {
        private val log = Logger.getLogger(ChengQing::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Cheng_Qing)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
            val p = player.game!!.players.filter { p -> p!!.alive && p.isPartnerOrSelf(player) }
                .flatMap { p -> p!!.messageCards.filter(color.Black).map { c -> PlayerAndCard(p, c) } }
                .randomOrNull() ?: return false
            GameExecutor.post(
                player.game!!,
                { card.execute(player.game!!, player, p.player, p.card.id) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}