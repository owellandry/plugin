package com.evidex.detection

import com.evidex.config.ConfigManager

class ViolationBuffer(private val config: ConfigManager) {

    fun shouldFlag(checkName: String, vlTotal: Int): Boolean {
        val flagVl = config.getCheckFlagVl(checkName)
        return vlTotal >= flagVl
    }

    fun shouldAutoRecord(checkName: String, vlTotal: Int): Boolean {
        if (!config.isAutoRecordOnFlag()) return false
        if (config.isAutoRecordOnFirstFlag()) return true
        return vlTotal >= config.getAutoRecordMinVl()
    }

    fun maxVl(checkName: String): Int = config.getCheckMaxVl(checkName)

    fun severity(checkName: String, vlTotal: Int): ViolationSeverity {
        val max = maxVl(checkName).coerceAtLeast(1)
        return ViolationSeverity.fromVlRatio(vlTotal.toDouble() / max)
    }
}