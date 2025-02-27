package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_zuo_you_feng_yuan_tos : AbstractProtoHandler<Role.skill_zuo_you_feng_yuan_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_zuo_you_feng_yuan_tos) {
        val skill = r.findSkill(SkillId.ZUO_YOU_FENG_YUAN) as? ActiveSkill
        if (skill == null) {
            log.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (pb.targetPlayerIdsList.toSet().size != pb.targetPlayerIdsCount) {
            log.error("选择的两个目标不能相同")
            r.sendErrorMessage("选择的两个目标不能相同")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_zuo_you_feng_yuan_tos::class.java)
    }
}