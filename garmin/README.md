# Station Pressure (Connect IQ watch app)

A minimal Garmin watch app that sends the barometer's **station pressure** to the Ballistics phone app.
Station pressure is what the watch measures where you stand; it is not reduced to sea level, so it does
not depend on the watch's altitude calibration. Temperature is not sent: a wrist sensor reads body heat.

## How it works

1. On the Conditions screen, tap **Pressure from Garmin watch** (shown when Garmin Connect is installed).
2. The phone asks the watch to open Station Pressure; confirm on the watch.
3. The watch sends its reading, and the phone fills in the pressure and switches it to station pressure.

The watch app also shows the pressure itself and sends it again when you press START. It runs only
while open (no background service) and closes itself 5 s after a reading reaches the phone. Messages go
through Garmin Connect on the phone (Connect IQ Mobile SDK), so the watch must be paired there.

## Languages

The watch shows English, Polish, German, French, Spanish or Italian, following the watch's language
(`resources-<lang>/strings/strings.xml`). The app name stays Station Pressure everywhere, as the phone app refers to it.

## Install (sideload)

GitHub Actions builds one `.prg` per watch model; download the `kgw-pressure-watch-app` artifact from the
workflow run.

| File | Watches |
| --- | --- |
| `kgw-pressure-fenix7x.prg` | tactix 7 (Standard, Pro), fēnix 7X, Enduro 2 |
| `kgw-pressure-fenix7xpro.prg` | fēnix 7X Pro |
| `kgw-pressure-epix2pro51mm.prg` | tactix 7 AMOLED, epix Pro 51 mm |

1. Connect the watch to a computer with its USB cable (on a Mac use Garmin Express or OpenMTP).
2. Copy the `.prg` into `GARMIN/APPS/` on the watch.
3. Unplug; the app appears in the watch's app list as Station Pressure.

If the watch does not list the app, try the file for the other model.

## Install from the Connect IQ store (beta)

Garmin Connect can open only apps it installed itself, so with a sideloaded `.prg` the watch has to be
opened by hand. A beta upload fixes that: a beta app is visible only to the account that uploaded it
and is not reviewed by Garmin.

1. Signing: the store accepts updates only with the same developer key. CI reads it from the
   `CIQ_DEVELOPER_KEY_BASE64` secret (base64 of the DER key); keep the key safe, it cannot be replaced.
2. Download `kgw-pressure.iq` from the `kgw-pressure-watch-app` artifact of a build that used that secret.
3. Sign in at the [Connect IQ developer dashboard](https://apps.garmin.com/developer/) with the Garmin
   account used in Garmin Connect, upload the `.iq`, tick **Beta app**, and fill in the name, a short
   description and the version. The store icons (128 × 128, 64-colour and 24-bit) the 500 × 500 cover image and the screen images are in `store/`.
4. Delete the sideloaded `kgw-pressure-*.prg` from `GARMIN/APPS/` on the watch.
5. Install the beta from the Connect IQ store in Garmin Connect (the app page link from the dashboard,
   with `apps-developer.garmin.com` changed to `apps.garmin.com`, opens it on the phone).

## Build locally

With the Connect IQ SDK and the Monkey C extension for VS Code: open this folder, run
*Monkey C: Build for Device* and pick the watch. Or from a shell:

```sh
monkeyc -f monkey.jungle -d fenix7x -o bin/kgw-pressure.prg -y developer_key.der -r
```

The app id in `manifest.xml` must match `APP_ID` in
`app/src/main/kotlin/pl/ballistics/app/data/GarminPressure.kt`.
