package com.github.minhkyng.globalnotificationeditor

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.views.Button
import com.aliucord.views.Divider
import com.discord.stores.StoreStream
import com.discord.views.CheckedSetting
import com.discord.views.RadioManager
import com.lytefast.flexinput.R

internal class GlobalNotificationEditorSettingsPage(
    private val plugin: GlobalNotificationEditor,
) : SettingsPage() {
    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View) {
        super.onViewBound(view)

        setActionBarTitle("Global Notification Editor")
        setActionBarSubtitle(null)

        val context = requireContext()
        val guildCount = StoreStream.getGuilds().guilds.size
        val status = TextView(context, null, 0, R.i.UiKit_TextView).apply {
            text = "$guildCount servers cached"
            gravity = Gravity.CENTER_HORIZONTAL
        }

        addSectionHeader("Mute")
        addEnumRadios(
            "muteMode",
            MuteMode.values(),
            mapOf(
                MuteMode.KEEP to "Keep existing",
                MuteMode.MUTE_FOREVER to "Mute indefinitely",
                MuteMode.UNMUTE to "Unmute",
            ),
            MuteMode.KEEP,
        )

        addSectionHeader("Message Notifications")
        addEnumRadios(
            "messageNotifications",
            MessageNotificationMode.values(),
            mapOf(
                MessageNotificationMode.KEEP to "Keep existing",
                MessageNotificationMode.ALL_MESSAGES to "All messages",
                MessageNotificationMode.ONLY_MENTIONS to "Only mentions",
                MessageNotificationMode.NOTHING to "Nothing",
            ),
            MessageNotificationMode.KEEP,
        )

        addSectionHeader("Mentions")
        addTriStateRadios("suppressEveryone", "Suppress @everyone and @here", TriState.KEEP)
        addTriStateRadios("suppressRoles", "Suppress role mentions", TriState.KEEP)

        addSectionHeader("Push")
        addTriStateRadios("mobilePush", "Mobile push notifications", TriState.KEEP)
        addTriStateRadios("muteScheduledEvents", "Mute scheduled event notifications", TriState.KEEP)

        addSectionHeader("Badges and Highlights")
        addEnumRadios(
            "highlights",
            HighlightMode.values(),
            mapOf(
                HighlightMode.KEEP to "Keep existing highlights",
                HighlightMode.DEFAULT to "Default highlights",
                HighlightMode.DISABLED to "Disable highlights",
                HighlightMode.ENABLED to "Enable highlights",
            ),
            HighlightMode.KEEP,
        )
        addEnumRadios(
            "unreadBadges",
            UnreadBadgeMode.values(),
            mapOf(
                UnreadBadgeMode.KEEP to "Keep existing unread badges",
                UnreadBadgeMode.FOLLOW_SERVER to "Follow server notifications",
                UnreadBadgeMode.ALL_MESSAGES to "Unread on all messages",
                UnreadBadgeMode.ONLY_MENTIONS to "Unread on mentions only",
            ),
            UnreadBadgeMode.KEEP,
        )

        addSectionHeader("Channel List")
        addTriStateRadios("hideMutedChannels", "Hide muted channels", TriState.KEEP)

        addView(Divider(context))
        addView(status)

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        buttons.addView(Button(context).apply {
            text = "Apply selected settings"
            setOnClickListener {
                confirmAndApply(
                    button = this,
                    status = status,
                    title = "Apply to all servers?",
                    message = "This will update server notification settings for $guildCount cached servers. Per-channel overrides are left unchanged.",
                ) { callback ->
                    plugin.applySavedSettings(callback)
                }
            }
        })

        buttons.addView(Button(context).apply {
            text = "Preset: mute everything"
            setOnClickListener {
                confirmAndApply(
                    button = this,
                    status = status,
                    title = "Mute all servers?",
                    message = "This will mute every cached server indefinitely and disable push notifications.",
                ) { callback ->
                    plugin.applyPreset(Preset.MUTE_ALL, callback)
                }
            }
        })

        buttons.addView(Button(context).apply {
            text = "Preset: mentions only"
            setOnClickListener {
                confirmAndApply(
                    button = this,
                    status = status,
                    title = "Set mentions only?",
                    message = "This will unmute every cached server and set notifications to only mentions.",
                ) { callback ->
                    plugin.applyPreset(Preset.MENTIONS_ONLY, callback)
                }
            }
        })

        buttons.addView(Button(context).apply {
            text = "Preset: unmute servers"
            setOnClickListener {
                confirmAndApply(
                    button = this,
                    status = status,
                    title = "Unmute all servers?",
                    message = "This will unmute every cached server and leave other notification settings unchanged.",
                ) { callback ->
                    plugin.applyPreset(Preset.UNMUTE_ALL, callback)
                }
            }
        })

        addView(buttons)
    }

    private fun confirmAndApply(
        button: Button,
        status: TextView,
        title: String,
        message: String,
        apply: ((ApplyResult) -> Unit) -> Unit,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Apply") { _, _ ->
                button.isEnabled = false
                status.text = "Applying..."

                apply { result ->
                    button.isEnabled = true
                    status.text = result.message
                    Utils.showToast(result.message, !result.success)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addSectionHeader(title: String) {
        addView(
            TextView(requireContext(), null, 0, R.i.UiKit_Settings_Item_Header).apply {
                text = title
                gravity = Gravity.START
            },
        )
    }

    private fun addTriStateRadios(
        key: String,
        label: String,
        defaultValue: TriState,
    ) {
        addEnumRadios(
            key,
            TriState.values(),
            mapOf(
                TriState.KEEP to "$label: keep existing",
                TriState.ENABLED to "$label: enabled",
                TriState.DISABLED to "$label: disabled",
            ),
            defaultValue,
        )
    }

    private fun <T : Enum<T>> addEnumRadios(
        key: String,
        values: Array<T>,
        labels: Map<T, String>,
        defaultValue: T,
    ) {
        val context = requireContext()
        val current = readEnum(key, values, defaultValue)
        val radios = values.map { value ->
            Utils.createCheckedSetting(
                context,
                CheckedSetting.ViewType.RADIO,
                labels[value] ?: value.name,
                null,
            )
        }
        val manager = RadioManager(radios)

        for ((index, radio) in radios.withIndex()) {
            val value = values[index]
            radio.e {
                plugin.settings.setString(key, value.name)
                manager.a(radio)
            }
            addView(radio)
            if (value == current) manager.a(radio)
        }
    }

    private fun <T : Enum<T>> readEnum(
        key: String,
        values: Array<T>,
        defaultValue: T,
    ): T {
        val stored = plugin.settings.getString(key, defaultValue.name)
        return values.firstOrNull { it.name == stored } ?: defaultValue.also {
            plugin.settings.setString(key, it.name)
        }
    }
}
