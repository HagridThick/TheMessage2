package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import org.apache.log4j.Logger
import java.util.*

/**
 * 判断是否需要濒死求澄清
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       接收第三张黑色情报的顺序，也就是后续结算濒死的顺序
 * @param afterDieResolve 濒死结算后的下一个动作
 */
data class StartWaitForChengQing(
    val whoseTurn: Player,
    val dyingQueue: Queue<Player>,
    val diedQueue: ArrayList<Player>,
    val afterDieResolve: Fsm
) : Fsm {
    constructor(whoseTurn: Player, dyingQueue: LinkedList<Player>, afterDieResolve: Fsm) : this(
        whoseTurn,
        dyingQueue,
        ArrayList<Player>(),
        afterDieResolve
    )

    override fun resolve(): ResolveResult {
        if (dyingQueue.isEmpty()) {
            for (whoDie in diedQueue) {
                whoDie.alive = false
                whoDie.dieJustNow = true
                for (p in whoseTurn.game!!.players) p!!.notifyDying(whoDie.location, false)
            }
            return ResolveResult(CheckKillerWin(whoseTurn, diedQueue, afterDieResolve), true)
        }
        val whoDie = dyingQueue.poll()
        log.info("${whoDie}濒死")
        val next = WaitForChengQing(whoseTurn, whoDie, whoseTurn, dyingQueue, diedQueue, afterDieResolve)
        val askWhomAlive = next.askWhom.let { it.alive && it !== it.game!!.jinBiPlayer }
        return ResolveResult(if (askWhomAlive) next else WaitNextForChengQing(next), true)
    }

    companion object {
        private val log = Logger.getLogger(StartWaitForChengQing::class.java)
    }
}