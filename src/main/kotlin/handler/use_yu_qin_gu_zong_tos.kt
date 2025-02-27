package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class use_yu_qin_gu_zong_tos : AbstractProtoHandler<Fengsheng.use_yu_qin_gu_zong_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_yu_qin_gu_zong_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (card.type != card_type.Yu_Qin_Gu_Zong) {
            log.error("这张牌不是欲擒故纵，而是$card")
            r.sendErrorMessage("这张牌不是欲擒故纵，而是$card")
            return
        }
        val messageCard = r.findMessageCard(pb.messageCardId)
        if (messageCard == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (Red !in messageCard.colors && Blue !in messageCard.colors) {
            log.error("选择的不是真情报")
            r.sendErrorMessage("选择的不是真情报")
            return
        }
        if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        if (r.findSkill(SkillId.LIAN_LUO) == null && pb.cardDir != messageCard.direction) {
            log.error("方向错误: ${pb.cardDir}")
            r.sendErrorMessage("方向错误: ${pb.cardDir}")
            return
        }
        var targetLocation = when (pb.cardDir) {
            Left -> r.getNextLeftAlivePlayer().location
            Right -> r.getNextRightAlivePlayer().location
            else -> 0
        }
        if (pb.cardDir != Up && pb.targetPlayerId != r.getAlternativeLocation(targetLocation)) {
            log.error("不能传给那个人: ${pb.targetPlayerId}")
            r.sendErrorMessage("不能传给那个人: ${pb.targetPlayerId}")
            return
        }
        if (messageCard.canLock()) {
            if (pb.lockPlayerIdCount > 1) {
                log.error("最多锁定一个目标")
                r.sendErrorMessage("最多锁定一个目标")
                return
            } else if (pb.lockPlayerIdCount == 1) {
                if (pb.getLockPlayerId(0) < 0 || pb.getLockPlayerId(0) >= r.game!!.players.size) {
                    log.error("锁定目标错误: ${pb.getLockPlayerId(0)}")
                    r.sendErrorMessage("锁定目标错误: ${pb.getLockPlayerId(0)}")
                    return
                } else if (pb.getLockPlayerId(0) == 0) {
                    log.error("不能锁定自己")
                    r.sendErrorMessage("不能锁定自己")
                    return
                }
            }
        } else {
            if (pb.lockPlayerIdCount > 0) {
                log.error("这张情报没有锁定标记")
                r.sendErrorMessage("这张情报没有锁定标记")
                return
            }
        }
        targetLocation = r.getAbstractLocation(pb.targetPlayerId)
        val target = r.game!!.players[targetLocation]!!
        if (!target.alive) {
            log.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        val lockPlayers = pb.lockPlayerIdList.map {
            val lockPlayer = r.game!!.players[r.getAbstractLocation(it)]!!
            if (!lockPlayer.alive) {
                log.error("锁定目标已死亡：$lockPlayer")
                r.sendErrorMessage("锁定目标已死亡：$lockPlayer")
                return
            }
            lockPlayer
        }
        if (card.canUse(r.game!!, r, messageCard, pb.cardDir, target, lockPlayers)) {
            r.incrSeq()
            card.execute(r.game!!, r, messageCard, pb.cardDir, target, lockPlayers)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_yu_qin_gu_zong_tos::class.java)
    }
}