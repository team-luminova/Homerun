package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.Homerun
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import java.time.ZoneId
import java.time.ZonedDateTime

class Messages(val plugin: Homerun) {

    private val mm = MiniMessage.miniMessage()
    private val messagesFile: File = plugin.dataFolder.resolve("messages.yml")
    private val messagesExampleFile: File = plugin.dataFolder.resolve("messages.example.yml")

    val messageCache = mutableMapOf<String, String>()
    val defaultMessageCache = mutableMapOf<String, String>()

    private val timezone: ZoneId

    init {
        // Save default messages to `messages.example.yml`.
        writeDefaultMessages()

        loadDefaultMessages()
        loadCustomMessages()

        timezone = ZoneId.of(getRaw("timezone"))
    }

    private fun writeDefaultMessages() {
        val inputStream = Homerun::class.java.getResourceAsStream("/messages.yml")
        messagesExampleFile.parentFile.mkdirs()
        messagesExampleFile.createNewFile()
        val outputStream = messagesExampleFile.outputStream()
        if (inputStream != null) {
            inputStream.copyTo(outputStream)
        } else {
            throw IllegalStateException("No messages.yml found?!")
        }
    }

    private fun loadDefaultMessages() {
        val defaultMessagesFile = Homerun::class.java.getResourceAsStream("/messages.yml")
        val defaultMessagesScanner = InputStreamReader(defaultMessagesFile!!)
        val defaultMessages = YamlConfiguration.loadConfiguration(defaultMessagesScanner)

        val keys = defaultMessages.getKeys(false)
        for (key in keys) {
            val value = defaultMessages.getString(key)
            if (value != null) {
                defaultMessageCache[key] = value
            }
        }
    }

    private fun loadCustomMessages() {
        if (!messagesFile.exists()) {
            // No custom messages to load.
            return
        }

        val customMessages = YamlConfiguration.loadConfiguration(messagesFile)

        val keys = customMessages.getKeys(false)
        for (key in keys) {
            val value = customMessages.getString(key)
            if (value != null) {
                messageCache[key] = value
            }
        }
    }

    fun reload() {
        messageCache.clear()
        defaultMessageCache.clear()
        loadDefaultMessages()
        loadCustomMessages()
    }

    fun get(key: String, vararg tagResolvers: TagResolver): Component {
        if (key === "version" || key === "timezone") {
            throw IllegalArgumentException("Invalid key '$key' as a message")
        }
        return mm.deserialize(
            getRaw(key).trim(),
            *tagResolvers
        )
    }

    fun getRaw(key: String): String {
        return messageCache[key] ?: defaultMessageCache[key]
        ?: throw IllegalArgumentException("Message key '$key' not defined as a message")
    }

    fun withTimezone(dt: ZonedDateTime): ZonedDateTime {
        return dt.withZoneSameInstant(this.timezone)!!
    }

}