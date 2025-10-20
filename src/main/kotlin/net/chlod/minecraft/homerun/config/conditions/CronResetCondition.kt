package net.chlod.minecraft.homerun.config.conditions

import com.cronutils.descriptor.CronDescriptor
import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import net.chlod.minecraft.homerun.Homerun
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.jvm.optionals.getOrNull

class CronResetCondition : ResetCondition {

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): CronResetCondition {
            val expression = args["cron"] as String
            return CronResetCondition(expression)
        }

        @JvmStatic
       val CRON_TYPE = CronType.UNIX
    }

    val cron: Cron
    val nextReset: ZonedDateTime?

    constructor(expression: String) {
        cron = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CRON_TYPE)).parse(expression)
        nextReset = ExecutionTime.forCron(cron).nextExecution(ZonedDateTime.now()).getOrNull()
    }

    override fun getHumanReadableDescription(): String {
        return CronDescriptor.instance(Locale.US).describe(cron)
    }

    override fun getTimeUntilNextReset(plugin: Homerun): Long? {
        return nextReset?.let {
            val now = ZonedDateTime.now()
            val duration = java.time.Duration.between(now, it)
            duration.toMillis()
        }
    }

    override fun isSatisfied(plugin: Homerun): Boolean {
        return nextReset?.let {
            val now = ZonedDateTime.now()
            !now.isBefore(it)
        } ?: false
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "cron" to cron.asString()
        )
    }


}