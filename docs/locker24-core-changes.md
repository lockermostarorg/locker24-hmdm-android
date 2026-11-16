# Locker24 changes to Headwind core files

Our code lives in `com.hmdm.launcher.locker24` and `app/src/locker24/`. This page lists every change to a file that comes from upstream (Headwind MDM launcher 6.40, base `75eff13` on `master`), so an upstream merge can be reviewed file by file.

The fork is ours. Upstream bugs are fixed in core files even when the fix changes the `opensource` flavor too; those fixes are marked **both flavors** below.

| File | Change | Why | Flavors |
|---|---|---|---|
| `.gitignore` | Ignores `/locker24.properties` and `*.jks`. | The signing config and keystore stay out of git. | both |
| `app/build.gradle` | `locker24` product flavor: package `app.locker24.mdm`, server URLs, MQTT port, version (currently `6.40-l24.5`, versionCode `1540005`), release signing from `locker24.properties` (the build fails without it). | Own package, server and signing. | locker24 |
| `locker24.properties.example` | Template for the signing properties. | Documents the required keys without secrets. | locker24 |
| `pro/ProUtils.java` | Upstream ships this file as stubs. Ours implements the lock-task kiosk (`startCosuKioskMode`, kiosk options, unlock) for both flavors. Only `applyConfiguredOrientation`, the kiosk app update tracking (forced fresh start, 3 s retry, settled check, joining a fresh start still coming up) and the `Locker24Hooks` calls check the flavor. A forced or retried start that throws restores the previous fresh-start time. Decisions live in `locker24/KioskPolicy`, `KioskStartPolicy`, `OrientationPolicy`. | ProUtils is the upstream extension point for the kiosk. | **both flavors** (orientation and update tracking: locker24 only) |
| `util/Utils.java` | `orientationDiag()` and an orientation log line in `setOrientation`. | Remote diagnosis of orientation problems. | both (logging only) |
| `ui/MainActivity.java` `onCreate`, `lockOrientation` | Orientation log lines (requested orientation in `onCreate`, the locked orientation in `lockOrientation`). | Remote diagnosis of orientation problems. | **both flavors** (logging only) |
| `ui/MainActivity.java` `onCreate`, `lockOrientation` | Requests the configured orientation in `onCreate`; `lockOrientation` uses the configured orientation when there is one instead of locking the current one. | The kiosk app came up in the wrong orientation after updates. | locker24 (`applyConfiguredOrientation` returns false in opensource) |
| `ui/MainActivity.java` `onCreate`, Initializer completion | Returns when the instance is already destroyed. | Initialization finishes asynchronously; a recreated instance registered its receivers after `onDestroy`, never unregistered them, and ran a second config update on every push. | **both flavors** |
| `ui/MainActivity.java` UINotifier callbacks | On a destroyed instance: no error dialogs (download/install errors take the non-interactive skip/repeat path), `onConfigUpdateComplete` and `onAllAppInstallComplete` are handed to the current instance (`liveInstance()`). | A flow started by a destroyed instance must not show dialogs or start the kiosk from the dead instance. | **both flavors** |
| `helper/ConfigUpdater.java` | `updateConfig` passes `locker24/ConfigFlows` (process-wide `locker24/ConfigFlowGate`) before `runConfigUpdate`; internal retries (network retry, server migration) call `runConfigUpdate`; `skipConfigLoad` passes the gate as a skip-load request. Flows end the gate on the terminal paths below. A superseded flow stops (and unregisters its install receiver) before applying the configuration, updating files, updating applications and each install. Without a listener, a download error skips the app and a silent-install error goes on with the next app. `pendingInstallations` is shared by all flows, so a flow that takes over skips a package still being installed. | Each ConfigUpdater guarded only itself, so two flows ran side by side and downloaded and installed the same APK twice (ENOENT on the second install). One flow runs at a time; requests made meanwhile run once more afterwards; a flow running for 10 min or owned by a destroyed launcher instance is taken over; a stalled flow is released after 10 min and the queue runs. | **both flavors** |
| `helper/Initializer.java` | The boot-time flow's listener takes the non-interactive path on errors (files: next file; apps: skip on download error, next app on install error). | Its empty error callbacks stalled the flow, which now holds the gate. | **both flavors** |
| `service/PluginApiService.java` | `CallbackUINotifier` reports errors to the plugin and then continues the flow the same way. New override `publishHealth(Bundle)` returns `HealthValues.publish(this, Binder.getCallingUid(), values)`. `getVersion()` stays 119. | Same: a plugin-started flow stalled on the first error. The kiosk app hands its health values to the agent, which only accepts them from the configured main app. | **both flavors** |
| `app/src/main/aidl/com/hmdm/IMdmApi.aidl`, `lib/src/main/aidl/com/hmdm/IMdmApi.aidl` | Appended `boolean publishHealth(in Bundle values);` after `forceConfigUpdateWithCallback`. Both copies are identical. | Transport for the kiosk app's health values. A client on an older agent gets `false` or an exception and treats it as not delivered. | **both flavors** |
| `util/PushNotificationMqttWrapper.java` | Adds `isConnected()`. | The health report includes the MQTT connection state. | **both flavors** |
| `worker/RemoteLogWorker.java` | `uploadScheduled` is volatile. `doWork` drains through `locker24/LogDrain`: one drain at a time (a second worker returns at once), a failed delete ends the drain, and rows written after the last empty select schedule another upload. | `scheduleUpload` uses `REPLACE` and `MainActivity.startServices` resets the scheduled flag on every resume; a replaced worker keeps running, so two workers uploaded the same rows (2-3 copies on the server). A blocking lock would tie up WorkManager threads. | **both flavors** |
| `db/LogTable.java` | `delete` returns whether the transaction succeeded; `deleteOldItems` uses `DELETE_OLD_LOGS`. | The drain must stop when uploaded rows cannot be deleted. Old logs were never purged because the purge ran the delete-by-id statement with a timestamp. | **both flavors** |
| `json/PushMessage.java` | Adds `TYPE_FORCE_APP_UPDATE = "forceAppUpdate"`. | The server sends it after moving configurations to a new app version with mode "immediate". | **both flavors** |
| `worker/PushNotificationProcessor.java` | Routes `forceAppUpdate` to `locker24/ForceAppUpdate`: a fresh payload starts `ConfigUpdater.forceAppUpdate(context, apps)`; a stale, missing or malformed payload starts the normal `notifyConfigUpdate`. | Installs the apps named in the push at once, ignoring the update window and the Wi-Fi-only rule for those apps only. No notifier is passed, so nothing shows UI on the kiosk. `userInteraction` is not used. | **both flavors** |

## AIDL method order

Binder transaction IDs follow declaration order in `IMdmApi.aidl`, so order is part of the contract with the kiosk app. Our methods keep their position, last of our additions (`publishHealth` is currently last). On an upstream merge, upstream's new methods go after ours, and the kiosk app's copy of the AIDL is updated to the same order. The `app` and `lib` copies must stay identical. On an upstream merge, keep `publishHealth` at its current position, append upstream's new methods after it, and update the kiosk app's AIDL copy to match; never move or remove an existing method. A plugin built against upstream's lib that hits `publishHealth`'s transaction ID gets a harmless `false` from the caller check.

## How a configuration update flow ends

A flow holds the process-wide gate from `ConfigFlows.begin` until `ConfigFlows.end`. A new early return in `ConfigUpdater` must either call `ConfigFlows.end(this)`, continue the flow, or be one of the stalls released by the 10-minute expiry. Otherwise pushed changes wait 10 minutes.

| Path | Ends with |
|---|---|
| `setActions` done ("Update flow completed") | `end()` |
| Config fetch: server error | `end()` |
| Config fetch: network error, retries left | continues (`runConfigUpdate` after the retry delay) |
| Config fetch: network error, no retries, dialog or no config | `end()` |
| Config fetch: network error, no retries, config kept | continues with the stored configuration |
| Load-only flow (`InitialSetupActivity`) after the logging config | `end()` |
| Server migration succeeded | continues (`runConfigUpdate`) |
| File download or install error | continues: no listener goes to the next file; MainActivity in kiosk mode or with content shown skips; Initializer and plugin listeners go to the next file |
| App download error (including "Skip download due to previous install failure") | continues: `skipDownloadApps()` without a listener; MainActivity in kiosk mode or with content shown, Initializer and plugin listeners skip too |
| Silent install error before commit | continues: next app without a listener; MainActivity, Initializer and plugin listeners repeat |
| Install, uninstall or user-confirmed install waiting for `ACTION_INSTALL_COMPLETE` | continues when the broadcast arrives; never arriving is a stall |
| Market install (asks the user, no completion broadcast) | stall |
| MainActivity dialogs without kiosk mode (file/app error, install error), including dialogs dismissed in `onPause` | continues when the user answers; otherwise a stall |
| Superseded flow at a stop point | stops; it no longer owns the gate |
| Stall of any kind | released by the expiry 10 min after `begin`, then the next queued request runs; a later request also takes over a stale flow or one whose launcher instance is destroyed |

## What `forceAppUpdate` changes

Push payload (a string for polled pushes, or a raw JSON object over MQTT; `ForceAppUpdate.payloadText` accepts both): `{"apps":[{"pkg":"...","version":"..."}],"issuedAt":<epoch ms>}`. `ForcedApps.parse` returns an empty set (and the agent runs a normal `configUpdated` flow) when the payload is missing, malformed, has no valid app entry, or `issuedAt` is older than 60 minutes by the device clock; a device clock before 2020 counts the payload as fresh.

The set travels with the request through `ConfigUpdater.updateConfig(..., forcedApps)` and `ConfigFlows`; the gate merges requests by union (a plain `configUpdated` merges in an empty set). `userInteraction` stays `false`, so its other side effects (network-error dialog and flow end, file Wi-Fi rule) do not apply.

| Place | Behaviour |
|---|---|
| `loadAndInstallApplications` | Per app, `ForcedApps.mayRun`: an app installs now when the window and Wi-Fi rules pass, or when it is not a removal and its package and configured version (from the freshly fetched configuration) match an entry. Every other install, update or removal waits for the window and Wi-Fi exactly as before. The next app is chosen by `AppInstallOrder.nextIndex` and moved to the head of the list; the agent's own package goes last among the runnable apps (installing it restarts the process). If none is runnable the step ends as before. |
| Agent self-update | Goes through the same list, so it is forced only when the agent's own package and version are in the set. |
| `loadAndInstallFiles` | Unchanged: files obey the Wi-Fi rule. |
| `canDownload` (apps) | A forced app (package and version match) skips the failed-install block and the limited-download rule, once per forced push: a failed forced install is skipped for the rest of the flow, and the next forced push retries it. A normal flow still honours the block. Files: unchanged. |

With the launcher not running, `configUpdated` and the force push share the BACKGROUND gate key and merge into one flow. With `MainActivity` running, `configUpdated` runs under the activity's key and the force flow is queued under BACKGROUND and runs after it (a redundant config fetch, and no completion refresh of `MainActivity`, as with `PluginApiService.forceConfigUpdate`).
