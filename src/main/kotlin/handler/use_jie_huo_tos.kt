package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.RuBiZhiShi.excuteRuBiZhiShi
import com.fengsheng.skill.canUseCardTypes
import org.apache.log4j.Logger

class use_jie_huo_tos : AbstractProtoHandler<Fengsheng.use_jie_huo_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_jie_huo_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (r.game!!.fsm is excuteRuBiZhiShi) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        var card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        val (ok, convertCardSkill) = r.canUseCardTypes(card_type.Jie_Huo, card.type)
        if (!ok) {
            log.error("这张${card}不能当作截获使用")
            r.sendErrorMessage("这张${card}不能当作截获使用")
            return
        }
        if (card.type != card_type.Jie_Huo) card = Card.falseCard(card_type.Jie_Huo, card)
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            convertCardSkill?.onConvert(r)
            card.execute(r.game!!, r)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_jie_huo_tos::class.java)
    }
}