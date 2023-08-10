package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_chi_zi_zhi_xin_tos : AbstractProtoHandler<Role.skill_chi_zi_zhi_xin_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_chi_zi_zhi_xin_tos) {
        val skill = r.findSkill(SkillId.CHI_ZI_ZHI_XIN)
        if (skill == null) {
            log.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_chi_zi_zhi_xin_tos::class.java)
    }
}