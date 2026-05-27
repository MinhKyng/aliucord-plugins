# MinhKyng's Aliucord Plugins

Download a plugin zip and place it in `/sdcard/Aliucord/plugins`.

| Plugin | Description |
| --- | --- |
| [GlobalNotificationEditor](https://github.com/MinhKyng/global-notification-editor/raw/refs/heads/builds/GlobalNotificationEditor.zip) | Bulk edit server notification settings across every server cached in the client. |

## Repository Layout

Each directory under `plugins/` with a `build.gradle.kts` file is treated as a separate Aliucord plugin.
Add future plugins as sibling folders next to `plugins/GlobalNotificationEditor`.

## Build

Requires JDK 21 and an Android SDK with platform 36 installed.

On Windows:

```powershell
.\gradlew.bat :plugins:GlobalNotificationEditor:make
```

The plugin zip is written to `plugins/GlobalNotificationEditor/build/outputs/GlobalNotificationEditor.zip`.
