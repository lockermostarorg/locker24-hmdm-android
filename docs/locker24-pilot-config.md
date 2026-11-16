# Locker24 MDM: hpmo-qa pilot configuration

Record of every value to set in the panel at https://mdm.locker24.app, so the configuration can be rebuilt.

## 0. Server prerequisite

Before any tablet is enrolled, the server must have its own MQTT admin password (`mqtt.admin.password` in `ROOT.xml`). Without it the server uses Headwind's public default, and anyone who reaches port 31000 can send pushes (`exitKiosk`, `adminPanel`, `intent`, `runCommand`) that the agent executes. Steps and the check are in the server repo: `deploy/server-setup.md`, section 4 (Hardening), step 5. The same fix applies to the old server `hwmdm.locker24.app`.

## 1. APKs

| APK | Package | Notes |
|---|---|---|
| Agent `app-locker24-release.apk` | `app.locker24.mdm` | version `6.40-l24.1`, built from branch `feat/locker24-kiosk` with the REAL signing key and the real `requestSignature` |
| Kiosk app `hpmoqa-1.2.0+15-mdm-release.apk` | `com.hpmoqa.tablet_client` | version 1.2.0+15 from branch `hpmostar-qa-mdm`; points at https://api.hpmostar-qa.locker24.app |

Before uploading the agent, check the signing certificate:

```bash
apksigner verify --print-certs app-locker24-release.apk
```

The SHA-256 must be the real release key. The TEST key must never be uploaded.

Upload both in the panel under Applications.

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
| `health_interval_minutes` | `5` | health report interval, clamped to 1..60, default 5; a charging or network change also reports after 30 s, a kiosk change after 60 s |

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

Any failure goes back to the owning task as a bug, with the logcat lines.
