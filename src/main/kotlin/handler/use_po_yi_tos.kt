package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class use_po_yi_tos : AbstractProtoHandler<Fengsheng.use_po_yi_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_po_yi_tos) {
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
        if (card.type != card_type.Po_Yi) {
            log.error("这张牌不是破译，而是$card")
            r.sendErrorMessage("这张牌不是破译，而是$card")
            return
        }
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            card.execute(r.game!!, r)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_po_yi_tos::class.java)
    }
}