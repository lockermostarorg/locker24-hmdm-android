# Locker24 MDM: supported devices

A tablet model is supported once it has passed the device checklist in `locker24-pilot-config.md` (section 6). Record each model here with the date, the agent and kiosk app versions, and every checklist result.

## Samsung Galaxy Tab A9 (SM-X110), Android 15

- Status: **pilot checklist passed** (power cut and Wi-Fi outage deferred to field testing)
- Tested: 2026-11-16, device M-01, configuration "Kiosk - HPMO QA" on mdm.locker24.app
- Agent: 6.40-l24.1 at enrolment, then 6.40-l24.2 and 6.40-l24.3 by self-update
- Kiosk app: PostBox Client QA (`com.hpmoqa.tablet_client`) 1.2.0 at enrolment, updated silently up to 1.2.6

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | QR enrolment from factory reset | PASS | Wi-Fi joined on the welcome screen, tapped 6 times, scanned the QR. The device is pre-created in the panel as M-01. The agent was downloaded, the config was synced and the kiosk app was installed within about 2 minutes. |
| 2 | Kiosk on first start, no Accessibility prompt | PASS | The overlay permission is reported as not granted. Kiosk works without it. |
| 3 | Home, back, recents, status bar and power menu blocked | PASS | The Samsung keyboard's settings gear and microphone also do nothing inside lock task. |
| 3b | A short press of the power button re-wakes the screen | PASS | Covered by "all blocked". Not tested separately. |
| 4 | Kiosk app update, back in kiosk within 60 s | PASS (after fixes) | Final run (agent l24.3, app 1.2.6): from install to locked landscape kiosk in about 5 s, with no black screen. Two bugs were found and fixed, see below. |
| 5 | Agent self-update | PASS | l24.1 → l24.2 → l24.3. Each restarted itself in about 5 s and came back to the kiosk. |
| 6 | Reboot | PASS | Back in landscape kiosk about 6 s after the agent starts. Samsung delivers BOOT_COMPLETED about 10 s after the launcher is already up. |
| 7 | Power cut | DEFERRED | To be tested in the field. |
| 8 | Wi-Fi off for 15 min | DEFERRED | To be tested in the field. |
| 9 | Scheduled reboot fires once | PASS | `reboot_time` 12:35 fired at 12:35:05 CET; back in landscape kiosk at 12:35:47; no second reboot. It never fires within the first hour of uptime (by design, to prevent loops): an attempt at 11:34 with 5 min uptime was correctly skipped. |
| 10 | Time zone Europe/Sarajevo | PASS | The scheduled reboot fired at the configured local minute, with no hour shift. |

### Bugs found and fixed during the pilot

1. **Black screen for about 50 s after a kiosk app update.** Replacing the locked kiosk package ends lock task, but `getLockTaskModeState()` still reported LOCKED for about a second. So the agent brought the dead task to the front instead of starting the app fresh, and the screen stayed black until the watchdog relaunched it.
   - Fixed in agent 6.40-l24.3. Each kiosk start compares the kiosk app's `PackageInfo.lastUpdateTime` with the last handled update. This forces exactly one fresh start (CLEAR_TASK) per update within 30 s, followed by one retry at 3 s if lock task has not engaged. An update is closed once lock task has held for 5 s, and boot only closes installs that predate the boot.
2. **The kiosk app came back in portrait after an update.** The app set landscape only at runtime (`SystemChrome.setPreferredOrientations` in Dart `main()`), so a freshly started activity began in the launcher's or the device's orientation. The launcher also locked itself to a stale portrait (`MainActivity.lockOrientation()`).
   - Fixed in two places:
     - **Agent 6.40-l24.3:** the launcher requests the configured orientation from `onCreate` on and never locks to the "current" one.
     - **Kiosk app:** declares `android:screenOrientation="sensorLandscape"` on its MainActivity. This is committed on `hpmostar-qa-mdm` and still has to be ported to the other tenant branches.

### Known follow-ups

- The kiosk app cold-starts twice after an update: the watchdog's immediate relaunch, when lock task is already gone, does not respect the 5 s fresh-start guard. This is harmless but worth tuning.
- The device-log uploader sends each entry 2 to 3 times (same `createtime`).
- Bottom navigation bar: hidden since PostBox QA 1.2.7 / PostBox 1.3.1 (insets controller, immersive). A swipe shows Back briefly. Main, post and demo still have the old code.
- Two config-update flows sometimes run for one push since the agent self-updates: the second re-downloads the APK and then fails to install it with ENOENT. This is harmless but should be fixed.
- The kiosk app targets SDK 36. On Android 16, large screens may ignore app orientation requests, so re-run this checklist when the tablet gets Android 16.
