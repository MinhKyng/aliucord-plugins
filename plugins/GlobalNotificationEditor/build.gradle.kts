version = "1.0.0"
description = "Bulk edit notification settings for every server you are in."

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Initial release.
        * Added a settings page for bulk server notification preferences.
        * Added quick commands for muting, unmuting, and mentions-only.
        """.trimIndent(),
    )

    deploy.set(true)
}
