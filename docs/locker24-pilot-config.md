# Locker24 MDM: hpmo-qa pilot configuration

Record of every value to set in the panel at https://mdm.locker24.app, so the configuration can be rebuilt.

## 0. Server prerequisite

Before any tablet is enrolled, the server must have its own MQTT admin password (`mqtt.admin.password` in `ROOT.xml`). Without it the server uses Headwind's public default, and anyone who reaches port 31000 can send pushes (`exitKiosk`, `adminPanel`, `intent`, `runCommand`) that the agent executes. Steps and the check are in the server repo: `deploy/server-setup.md`, section 4 (Hardening), step 5. The same fix applies to the old server `hwmdm.locker24.app`.

## 1. APKs

| APK | Package | Notes |
|---|---|---|
| Agent `app-locker24-release.apk` | `app.locker24.mdm` | version `6.40-l24.6` (versionCode `1540006`, flavor `locker24`), built from `master` with the REAL signing key and the real `requestSignature`. It adds the `forceAppUpdate` push (install the named apps at once) on top of the health reports of `6.40-l24.5`. Upload it only after the server release that knows release modes is deployed (server repo `deploy/README.md`, "Rollout order") |
| Kiosk app `hpmoqa-1.2.0+15-mdm-release.apk` | `com.hpmoqa.tablet_client` | version 1.2.0+15 from branch `hpmostar-qa-mdm`; points at https://api.hpmostar-qa.locker24.app |

Before uploading the agent, check the signing certificate:

```bash
apksigner verify --print-certs app-locker24-release.apk
```

The SHA-256 must be the real release key. The TEST key must never be uploaded.

Upload both in the panel under Applications. When a new version is uploaded the panel asks how to apply it (on schedule, immediately or hold); use on schedule for the agent. Uploading a version name that exists with a different file is refused, so every rebuild needs a new version name; this also keeps the QR code checksum (cached per version) valid.

Agent `6.40-l24.5` and later send health reports. Do not push them to the fleet until the server's health endpoint answers: `POST /rest/public/plugin-locker24-health/report/{number}` must return JSON with `status` `OK` (or `ERROR` with a message), not 404. Until then every tablet retries its upload, queues up to 288 reports and keeps probing the server.

## 2. Configuration `hpmo-qa`

- MDM application: `app.locker24.mdm`
- Admin receiver: `com.hmdm.launcher.AdminReceiver`
- Kiosk mode: ON, content app `com.hpmoqa.tablet_client`
- Kiosk exit button: OFF. The on-screen unlock is not implemented. Admin exit goes through the panel's `exitKiosk` push.
- Home, recents, notifications, status bar info, screen lock: all OFF
- Lock power button: ON
- Keep screen on (`kioskScreenOn`): ON. Without it a short press of the power button blanks the locker screen. With it, the agent turns the screen straight back on while lock task runs. See "Setting keep screen on" below: the v7 panel has no checkbox for it.
- Restrictions: `no_factory_reset,no_add_user,no_safe_boot,no_usb_file_transfer,no_physical_media`
- System update: scheduled window 02:00 to 05:00
- App update: immediate (scheduled app update OFF for the pilot)
- Wi-Fi: set the enrolment network
- Common settings, "Manage time zone": `Manual`, "Time zone": `Europe/Sarajevo`. `reboot_time` is device local time, so the device time zone must be right.
- "Show Wi-Fi settings on network error" (or similar): OFF. Otherwise a dialog appears over the kiosk.

### Setting keep screen on

The v7 panel stores `kioskScreenOn` and sends it to devices, but its kiosk section has no checkbox for it (the "Screen always on" label exists only in the translations). Set it once in the database. The user runs this on the server:

```bash
cd /tmp
sudo -u postgres psql hmdm -c "UPDATE configurations SET kioskscreenon = true WHERE name = 'hpmo-qa';"
sudo -u postgres psql hmdm -c "SELECT name, kioskscreenon FROM configurations WHERE name = 'hpmo-qa';"
```

Expected: `UPDATE 1`, then `hpmo-qa | t`. Then open the configuration in the panel and click Save once, so the change is pushed to the devices. The form loads the stored value and saves it back, so later saves keep it on. Run the `SELECT` again after any later save to be sure.

### Kiosk app entry (`com.hpmoqa.tablet_client`)

"Run after installation" and "Run at boot" must both be OFF. They create an unlocked task that defeats lock task. The agent starts the kiosk app itself.

## 3. App settings (package `app.locker24.mdm`)

| Name | Value | Notes |
|---|---|---|
| `kiosk_watchdog_seconds` | `30` | clamped to 10..240, default 30 |
| `reboot_time` | `04:00` | use 04:00 or later; a time between 02:00 and 02:59 is skipped once a year by the spring DST change |
| `stay_on_plugged` | `1` | `0` turns stay-on off, anything else turns it on |
| `health_interval_minutes` | `5` | health report interval, clamped to 1..8, default 5. The cap is 8 because the server marks a tablet offline after 10 min without a report. A charging or network change also reports after 30 s, a kiosk change after 60 s. When the watchdog service is not running, only the 15-min WorkManager safety net reports, so that tablet shows red offline between reports. |

Battery-optimisation status shows in the device log as `battery_opt agent=... kiosk=...`.

## 4. Device group and QR code

- Device group: `hpmo-qa`
- The QR code is generated from configuration `hpmo-qa`, with the custom extra `com.hmdm.GROUP=hpmo-qa`.

## 5. Verify (read-only)

Download the QR code's JSON from the panel. Expected:
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` is `app.locker24.mdm/com.hmdm.launcher.AdminReceiver`
- the APK URL is on `mdm.locker24.app`
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM` matches the SHA-256 of the APK file itself, base64url-encoded:

```bash
openssl dgst -binary -sha256 app-locker24-release.apk | openssl base64 | tr '+/' '-_' | tr -d '='
```

This is a hash of the file, not the signing certificate digest that `apksigner` prints. The panel's value ends with one `=` (base64 padding); compare the 43 characters before it.

The server computes this hash the first time a QR code is made for an app version and then caches it for that version. Never upload a different file under the same version number: the QR code would keep the old checksum and enrolment fails. Upload a changed APK as a new version.

Secrets (`requestSignature`, keystore password) are in the team password manager and never in git.

## 6. Device checklist

The pilot tablet must pass the checklist from the plan (Task 9), with these additions:

1. QR enrolment from factory reset.
2. Kiosk on first start, with no Accessibility prompt.
3. Home, back, recents, status bar and power menu are all blocked.
   - 3b. A short press of the power button does not leave the screen off: the screen comes straight back on, still in kiosk.
4. Upload `hpmoqa-1.2.1+16-update-test.apk` as a new version: the tablet updates and is back in kiosk within 60 s.
5. Build agent `6.40-l24.2` / `1540002`, upload it: the agent updates and is back in kiosk within 60 s.
6. Reboot: back in kiosk.
7. Power cut: back in kiosk.
8. Wi-Fi off 15 min, then on: the device syncs again (the health queue comes in Plan 2).
9. Set `reboot_time` 3 min ahead: the tablet reboots once, and not again that day. The tablet must have been up for at least 1 hour: the agent never does a scheduled reboot in the first hour after boot.
10. On the tablet, Settings > General management > Date and time shows `Europe/Sarajevo` (Central European time), and the scheduled reboot in step 9 happens at the configured local time, not shifted by an hour or more.

### Health checks (agent 6.40-l24.5 and later)

These mirror the canonical H1-H6 in the server repo's `docs/locker24-health.md`; change them there first. Read the results on Functions -> Fleet health.

- **H1. Report arrives.** Within 5 minutes of the agent update the tablet shows on Fleet health with model SM-X110, battery, temperature and both versions (agent `6.40-l24.5`, kiosk app). After a few hours the 24 h history charts render battery, temperature, RSSI and offline periods.
- **H2. Offline turns red and recovers.** Wi-Fi off for 15 minutes. The tablet turns red `offline` 10-11 minutes after its last report and a pop-up alert appears (the webhook receives `raised` if one is set). Wi-Fi on: the queued reports arrive, the history shows the gap as an offline period with points inside it, and the status turns green after a real report.
- **H3. Kiosk-app keys.** The kiosk-app values are not shown in the panel, so verify them in the server database. After PostBox publishes its values, `locker_short_uid` equals the locker set in the kiosk app, and after a PIN unlock attempt `last_api_ok_at` moves:
  ```bash
  sudo -u postgres psql hmdm -x -c "SELECT l.locker_short_uid, l.kiosk_app_version, l.kiosk_running, l.kiosk_admin_unlocked, l.report_json::json->'app' AS app FROM locker24_device_health_latest l JOIN devices d ON d.id=l.device_id WHERE d.number='M-01'"
  ```
  `app` holds `last_api_ok_at`, `last_api_error_at`, `last_api_error` and `app_errors_24h` (epoch milliseconds). Run it before and after the PIN attempt, with a report cycle in between.
- **H4. Alert raised and cleared.** The offline alert of H2 clears about 5 minutes after the first real report. Unplug the charger: yellow `not_charging` within about 2 minutes and an alert after 30 minutes non-green; replug: green, and the alert clears about 5 minutes later.
- **H5. Admin exit of the kiosk.** Push `exitKiosk`: yellow "Kiosk off (admin)" within about 3 minutes (30 s tick, 60 s debounce, 1 min evaluation) and NO alert. Start the kiosk again: green. An accidental loss of lock task (not admin) still turns red `kiosk_not_running` (after 2 consecutive fresh reports) and alerts.
- **H6. Update gap does not go red.** Publish a kiosk app update (or reinstall the same version) while watching: the 5-10 second gap while the app is replaced must NOT turn the tablet red or raise an alert, and a scheduled update window must not leave it yellow or alerting before the window has had its chance.

### Release flow checks (agent 6.40-l24.6)

R1 to R5 prove the release flow on the pilot tablet M-01. The server operator guide is `docs/locker24-release-flow.md` in the server repo.

**Warning.** An upload or release on schedule or Immediately moves EVERY configuration that follows that package, in every customer, including production `hpmo`. Never upload a throwaway build of a package that a production configuration uses unless it is held and pinned. These checks therefore use Hold plus pins for the kiosk app, and a harmless test app linked to `hpmo-qa` only.

Needed:
- M-01 enrolled on agent `6.40-l24.6` in configuration `hpmo-qa`, and nothing else assigned to that configuration. The pilot normally runs with "Schedule app update" OFF (section 2): switch it on for these checks and set the "App update time" window (From / To) as each check says. Without a window an on-schedule release installs at once.
- Kiosk APKs of `com.hpmoqa.tablet_client` with rising version numbers (for example `1.2.1+16`, `1.2.2+17`, `1.2.3+18`, `1.2.4+19`), signed like the real build. They are test uploads of a package used only by `hpmo-qa` and `hpmo-qa`-like configurations: check in the panel (application, Configurations) that no production configuration uses it before you start. The LAST released version must be a real build when you finish (see the cleanup).
- A harmless small test app, for example a public app such as `com.android.calculator2` from an APK, or a tiny APK you build (package `app.locker24.releasetest`, versions `1.0` and `2.0`). Link it to `hpmo-qa` only (Applications, the app, Configurations dialog) with the install action.
- A throwaway configuration with no tablets assigned, created for R4 (copy `hpmo-qa` and rename it `r4-throwaway`).

Read results on Functions -> Fleet health (app versions), the panel's version list and upload summary, and on the server `grep 'Release app=' /var/lib/tomcat10/logs/catalina.out | tail`.

- **R1. Schedule, window OPEN (now inside it).** Set the window From 1 hour ago, To 2 hours ahead. Upload kiosk `1.2.1+16` with "On each configuration's schedule". The summary says "Released to N configurations"; the versions list shows Released with your name. Pass: M-01 installs and is back in kiosk within 60 s, Fleet health shows the new kiosk version, the log line has `mode=schedule`. Also pass with no window at all: it installs at once.
- **R2. Schedule, window CLOSED (starts 2 hours from now).** Set From 2 hours ahead, To 4 hours ahead. Upload kiosk `1.2.2+17` on schedule. Pass: the summary shows the release, but after 10 minutes M-01 still reports `1.2.1+16`; no install, no kiosk exit. Then move the window so that now is inside it and save the configuration: M-01 installs `1.2.2+17` within about 60 s and returns to kiosk.
- **R3. Immediately, window CLOSED: only the named app installs.** Keep the window closed (From 2 hours ahead). Upload test app `2.0` on schedule (it waits for the window), then upload kiosk `1.2.3+18` with "Immediately". Pass: M-01 installs the kiosk app within a minute (back in kiosk, Fleet health shows `1.2.3+18`) while the test app is still on `1.0` (check Settings > Apps on the tablet, or `adb shell dumpsys package app.locker24.releasetest | grep versionName`). Then open the window (move it over now and save): the test app updates to `2.0`. That "the agent installs its own update last" is not checked on a device: it is covered by the unit test `AppInstallOrderTest`. An older agent (`6.40-l24.5` or earlier, needs a second tablet, optional) gets only the ordinary update and installs the kiosk in its window, never at once. No agent build is uploaded in these checks.
- **R4. Hold, pin the pilot, release.** Window OPEN (From 1 hour ago, To 2 hours ahead) in `hpmo-qa`. `r4-throwaway` (no tablets) also uses the kiosk app, following latest. Upload kiosk `1.2.4+19` with Hold: the summary says "Held; not applied" and M-01 does not change. In `hpmo-qa`, Applications, pick `1.2.4+19` for the kiosk row (it shows "Pinned to 1.2.4+19") and save: M-01 installs it within a minute while `r4-throwaway` stays on `1.2.3+18`. Release the held version Immediately; remember that this moves EVERY following configuration of that package (so only configurations you created for the test or know about may use it). Pass: `r4-throwaway` moves to `1.2.4+19`, in `hpmo-qa` the row reads "Follows latest" again (the pilot rejoined), the versions list shows `1.2.4+19` Released with your name, and the log line reads `mode=immediate`.
- **R5. A pinned configuration is untouched by an upload.** Window OPEN. Pin the kiosk row of `hpmo-qa` to `1.2.4+19` (toggle "Follows latest" off, save). Upload kiosk `1.2.5+20` Immediately. Pass: the summary and the log line count only the following configurations (not `hpmo-qa`), M-01 stays on `1.2.4+19`, and the row still reads "Pinned to 1.2.4+19". Switch the toggle back on and save: the row moves to `1.2.5+20` and M-01 installs. Finally upload `1.2.5+20` again unchanged: nothing happens and the summary reports the stored state ("Released to 0 configurations").

#### Cleanup after R1 to R5

1. Restore "Schedule app update" and the "App update time" values of `hpmo-qa` to what section 2 says (scheduled update OFF for the pilot), and save.
2. Make the last released kiosk version a real build: upload the real kiosk version (for example the one from section 1) last, on schedule with the window open (or Immediately), and check that every following configuration and M-01 are on it. Test versions cannot be downgraded on the tablet, so the real build must have the highest version code.
3. In `hpmo-qa` set the kiosk row back to "Follows latest" (R5 leaves it that way if you finished it).
4. Unlink and delete the test app (application, Configurations dialog to unlink, then delete the app) and delete the configuration `r4-throwaway`. Delete the test kiosk versions that no configuration uses, if wanted.
5. Confirm M-01 is back in landscape kiosk on the real build and shows green on Fleet health.

Any failure goes back to the owning task as a bug, with the logcat lines.
