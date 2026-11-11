# Locker24 MDM: hpmo-qa pilot configuration

Record of every value to set in the panel at https://mdm.locker24.app, so the configuration can be rebuilt.

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

## 2. Configuration `hpmo-qa`

- MDM application: `app.locker24.mdm`
- Admin receiver: `com.hmdm.launcher.AdminReceiver`
- Kiosk mode: ON, content app `com.hpmoqa.tablet_client`
- Kiosk exit button: OFF. The on-screen unlock is not implemented. Admin exit goes through the panel's `exitKiosk` push.
- Home, recents, notifications, status bar info, screen lock: all OFF
- Lock power button: ON
- Restrictions: `no_factory_reset,no_add_user,no_safe_boot,no_usb_file_transfer,no_physical_media`
- System update: scheduled window 02:00 to 05:00
- App update: immediate (scheduled app update OFF for the pilot)
- Wi-Fi: set the enrolment network
- "Show Wi-Fi settings on network error" (or similar): OFF. Otherwise a dialog appears over the kiosk.

### Kiosk app entry (`com.hpmoqa.tablet_client`)

"Run after installation" and "Run at boot" must both be OFF. They create an unlocked task that defeats lock task. The agent starts the kiosk app itself.

## 3. App settings (package `app.locker24.mdm`)

| Name | Value | Notes |
|---|---|---|
| `kiosk_watchdog_seconds` | `30` | clamped to 10..240, default 30 |
| `reboot_time` | `04:00` | use 04:00 or later; a time between 02:00 and 02:59 is skipped once a year by the spring DST change |
| `stay_on_plugged` | `1` | `0` turns stay-on off, anything else turns it on |

Battery-optimisation status shows in the device log as `battery_opt agent=... kiosk=...`.

## 4. Device group and QR code

- Device group: `hpmo-qa`
- The QR code is generated from configuration `hpmo-qa`, with the custom extra `com.hmdm.GROUP=hpmo-qa`.

## 5. Verify (read-only)

Download the QR code's JSON from the panel. Expected:
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` is `app.locker24.mdm/com.hmdm.launcher.AdminReceiver`
- the APK URL is on `mdm.locker24.app`
- the checksum matches the `apksigner` SHA-256 of the uploaded agent

Secrets (`requestSignature`, keystore password) are in the team password manager and never in git.
