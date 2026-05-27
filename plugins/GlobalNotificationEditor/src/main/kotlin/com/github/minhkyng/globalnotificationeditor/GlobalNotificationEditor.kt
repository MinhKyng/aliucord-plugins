package com.github.minhkyng.globalnotificationeditor

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.discord.models.guild.Guild
import com.discord.stores.StoreStream
import java.lang.reflect.Field

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class GlobalNotificationEditor : Plugin() {
    init {
        settingsTab = SettingsTab(GlobalNotificationEditorSettingsPage::class.java, SettingsTab.Type.PAGE).withArgs(this)
    }

    override fun start(context: Context) {
        commands.registerCommand("globalmute", "Mute every server indefinitely.") {
            runCommandPreset(Preset.MUTE_ALL)
        }

        commands.registerCommand("globalmentions", "Set every server to mentions only.") {
            runCommandPreset(Preset.MENTIONS_ONLY)
        }

        commands.registerCommand("globalunmute", "Unmute every server.") {
            runCommandPreset(Preset.UNMUTE_ALL)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }

    internal fun applySavedSettings(callback: (ApplyResult) -> Unit) {
        val options = NotificationPatchOptions.fromSettings(settings)
        apply(options, callback)
    }

    internal fun applyPreset(preset: Preset, callback: (ApplyResult) -> Unit) {
        apply(NotificationPatchOptions.fromPreset(preset), callback)
    }

    private fun runCommandPreset(preset: Preset): CommandsAPI.CommandResult {
        val result = applyBlocking(NotificationPatchOptions.fromPreset(preset))
        return CommandsAPI.CommandResult(result.message)
    }

    private fun apply(options: NotificationPatchOptions, callback: (ApplyResult) -> Unit) {
        Utils.threadPool.execute {
            val result = applyBlocking(options)
            Utils.mainThread.post { callback(result) }
        }
    }

    private fun applyBlocking(options: NotificationPatchOptions): ApplyResult {
        val guilds = StoreStream.getGuilds().guilds.values
            .filter { it.id > 0L }
            .sortedBy(Guild::getName)

        if (guilds.isEmpty()) {
            return ApplyResult(false, "No servers are cached yet. Open your server list and try again.")
        }

        val currentFlags = currentFlagsByGuild()
        val guildPayloads = linkedMapOf<String, Map<String, Any?>>()
        var skippedUnreadFlags = 0

        for (guild in guilds) {
            val flags = currentFlags[guild.id]
            val patch = options.toPayload(flags)
            if (options.unreadBadges != UnreadBadgeMode.KEEP && flags == null) {
                skippedUnreadFlags++
            }
            if (patch.isNotEmpty()) {
                guildPayloads[guild.id.toString()] = patch
            }
        }

        if (guildPayloads.isEmpty()) {
            return ApplyResult(false, "Choose at least one setting before applying.")
        }

        return try {
            val response = Http.Request
                .newDiscordRNRequest("/users/@me/guilds/settings", "PATCH")
                .executeWithJson(mapOf("guilds" to guildPayloads))

            if (!response.ok()) {
                ApplyResult(false, "Discord returned HTTP ${response.statusCode}.")
            } else {
                val suffix = if (skippedUnreadFlags > 0) {
                    " Unread badge flags were skipped for $skippedUnreadFlags servers."
                } else {
                    ""
                }
                ApplyResult(true, "Updated ${guildPayloads.size} servers.$suffix")
            }
        } catch (throwable: Throwable) {
            logger.error(throwable)
            ApplyResult(false, "Failed: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun currentFlagsByGuild(): Map<Long, Int> {
        return try {
            val store = StoreStream.getUserGuildSettings()
            val field = findField(store.javaClass, "guildSettings") ?: return emptyMap()
            field.isAccessible = true

            val guildSettings = field.get(store) as? Map<*, *> ?: return emptyMap()
            guildSettings.mapNotNull { (guildId, notificationSettings) ->
                val id = guildId as? Long ?: return@mapNotNull null
                val flags = readIntField(notificationSettings, "flags") ?: return@mapNotNull null
                id to flags
            }.toMap()
        } catch (throwable: Throwable) {
            logger.warn("Could not read current guild notification flags", throwable)
            emptyMap()
        }
    }

    private fun readIntField(instance: Any?, name: String): Int? {
        if (instance == null) return null
        val field = findField(instance.javaClass, name) ?: return null
        field.isAccessible = true
        return field.get(instance) as? Int
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}

internal data class ApplyResult(
    val success: Boolean,
    val message: String,
)

internal enum class Preset {
    MUTE_ALL,
    MENTIONS_ONLY,
    UNMUTE_ALL,
}

internal enum class MuteMode {
    KEEP,
    MUTE_FOREVER,
    UNMUTE,
}

internal enum class MessageNotificationMode(val apiValue: Int?) {
    KEEP(null),
    ALL_MESSAGES(0),
    ONLY_MENTIONS(1),
    NOTHING(2),
}

internal enum class TriState {
    KEEP,
    ENABLED,
    DISABLED,
}

internal enum class HighlightMode(val apiValue: Int?) {
    KEEP(null),
    DEFAULT(0),
    DISABLED(1),
    ENABLED(2),
}

internal enum class UnreadBadgeMode {
    KEEP,
    FOLLOW_SERVER,
    ALL_MESSAGES,
    ONLY_MENTIONS,
}

internal data class NotificationPatchOptions(
    val muteMode: MuteMode,
    val messageNotifications: MessageNotificationMode,
    val mobilePush: TriState,
    val suppressEveryone: TriState,
    val suppressRoles: TriState,
    val hideMutedChannels: TriState,
    val muteScheduledEvents: TriState,
    val highlights: HighlightMode,
    val unreadBadges: UnreadBadgeMode,
) {
    fun toPayload(currentFlags: Int?): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>()

        when (muteMode) {
            MuteMode.KEEP -> {}
            MuteMode.MUTE_FOREVER -> {
                payload["muted"] = true
                payload["mute_config"] = mapOf<String, Any?>(
                    "selected_time_window" to -1,
                    "end_time" to null,
                )
            }
            MuteMode.UNMUTE -> {
                payload["muted"] = false
                payload["mute_config"] = null
            }
        }

        messageNotifications.apiValue?.let { payload["message_notifications"] = it }
        mobilePush.toBoolean()?.let { payload["mobile_push"] = it }
        suppressEveryone.toBoolean()?.let { payload["suppress_everyone"] = it }
        suppressRoles.toBoolean()?.let { payload["suppress_roles"] = it }
        hideMutedChannels.toBoolean()?.let { payload["hide_muted_channels"] = it }
        muteScheduledEvents.toBoolean()?.let { payload["mute_scheduled_events"] = it }
        highlights.apiValue?.let { payload["notify_highlights"] = it }

        val updatedFlags = unreadBadges.toFlags(currentFlags)
        if (updatedFlags != null) payload["flags"] = updatedFlags

        return payload
    }

    companion object {
        fun fromSettings(settings: SettingsAPI) = NotificationPatchOptions(
            muteMode = settings.getEnumSetting("muteMode", MuteMode.KEEP),
            messageNotifications = settings.getEnumSetting("messageNotifications", MessageNotificationMode.KEEP),
            mobilePush = settings.getEnumSetting("mobilePush", TriState.KEEP),
            suppressEveryone = settings.getEnumSetting("suppressEveryone", TriState.KEEP),
            suppressRoles = settings.getEnumSetting("suppressRoles", TriState.KEEP),
            hideMutedChannels = settings.getEnumSetting("hideMutedChannels", TriState.KEEP),
            muteScheduledEvents = settings.getEnumSetting("muteScheduledEvents", TriState.KEEP),
            highlights = settings.getEnumSetting("highlights", HighlightMode.KEEP),
            unreadBadges = settings.getEnumSetting("unreadBadges", UnreadBadgeMode.KEEP),
        )

        fun fromPreset(preset: Preset) = when (preset) {
            Preset.MUTE_ALL -> NotificationPatchOptions(
                muteMode = MuteMode.MUTE_FOREVER,
                messageNotifications = MessageNotificationMode.NOTHING,
                mobilePush = TriState.DISABLED,
                suppressEveryone = TriState.ENABLED,
                suppressRoles = TriState.ENABLED,
                hideMutedChannels = TriState.KEEP,
                muteScheduledEvents = TriState.ENABLED,
                highlights = HighlightMode.DISABLED,
                unreadBadges = UnreadBadgeMode.ONLY_MENTIONS,
            )
            Preset.MENTIONS_ONLY -> NotificationPatchOptions(
                muteMode = MuteMode.UNMUTE,
                messageNotifications = MessageNotificationMode.ONLY_MENTIONS,
                mobilePush = TriState.ENABLED,
                suppressEveryone = TriState.ENABLED,
                suppressRoles = TriState.ENABLED,
                hideMutedChannels = TriState.KEEP,
                muteScheduledEvents = TriState.DISABLED,
                highlights = HighlightMode.DISABLED,
                unreadBadges = UnreadBadgeMode.ONLY_MENTIONS,
            )
            Preset.UNMUTE_ALL -> NotificationPatchOptions(
                muteMode = MuteMode.UNMUTE,
                messageNotifications = MessageNotificationMode.KEEP,
                mobilePush = TriState.KEEP,
                suppressEveryone = TriState.KEEP,
                suppressRoles = TriState.KEEP,
                hideMutedChannels = TriState.KEEP,
                muteScheduledEvents = TriState.KEEP,
                highlights = HighlightMode.KEEP,
                unreadBadges = UnreadBadgeMode.KEEP,
            )
        }
    }
}

private fun TriState.toBoolean() = when (this) {
    TriState.KEEP -> null
    TriState.ENABLED -> true
    TriState.DISABLED -> false
}

private const val UNREADS_ALL_MESSAGES = 1 shl 11
private const val UNREADS_ONLY_MENTIONS = 1 shl 12
private const val UNREAD_MASK = UNREADS_ALL_MESSAGES or UNREADS_ONLY_MENTIONS

private fun UnreadBadgeMode.toFlags(currentFlags: Int?): Int? {
    if (this == UnreadBadgeMode.KEEP || currentFlags == null) return null

    val flagsWithoutUnreadBits = currentFlags and UNREAD_MASK.inv()
    return when (this) {
        UnreadBadgeMode.KEEP -> currentFlags
        UnreadBadgeMode.FOLLOW_SERVER -> flagsWithoutUnreadBits
        UnreadBadgeMode.ALL_MESSAGES -> flagsWithoutUnreadBits or UNREADS_ALL_MESSAGES
        UnreadBadgeMode.ONLY_MENTIONS -> flagsWithoutUnreadBits or UNREADS_ONLY_MENTIONS
    }
}

private inline fun <reified T : Enum<T>> SettingsAPI.getEnumSetting(key: String, defaultValue: T): T {
    return try {
        enumValueOf(getString(key, defaultValue.name))
    } catch (_: IllegalArgumentException) {
        setString(key, defaultValue.name)
        defaultValue
    }
}
