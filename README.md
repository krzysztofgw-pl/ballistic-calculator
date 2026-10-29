# Ballistics

An Android ballistic calculator by **KGW Labs** (`com.kgwlabs.ballistics`).

Ballistics computes bullet trajectories with a 3DOF point-mass solver built on established, publicly documented
models: standard drag functions, Doppler-derived drag curves, a humid-air atmosphere, Coriolis, spin drift and
aerodynamic jump. It keeps rifle and load profiles, gives a dope table with turret clicks, draws the holds on
your scope's reticle, and syncs everything to the cloud.

> **Safety note.** A calculator only predicts. Always confirm the dope with live fire before relying on it,
> and check library bullet data against the manufacturer's current figures.

## Features

### Profiles
- **Rifles**: name, barrel length, scope (turret unit MRAD or MOA, click value, reticle and its scale step when the scope's differs
  from the design (e.g. 0.1 MRAD), first or second focal plane,
  calibration and maximum magnification), sight height, zero range and the load it is zeroed with.
- **Sight height helper**: compute it from a caliper measurement, or estimate it from the objective diameter,
  ring height and rail.
- **Loads**: reference muzzle velocity with the barrel length it was measured in, powder temperature sensitivity, bullet weight, diameter, length and twist,
  drag model and BC. Start from the built-in library or enter custom data, including a full Cd(Mach) curve.
- **Velocity per rifle**: each rifle stores the muzzle velocity measured with it for each load, which the
  solver uses instead of the load's reference velocity. The app shows the load's test barrel length and how
  it differs from your barrel; it does not guess a velocity for a different barrel.
- **Calibers**: rifles and loads name their cartridge, picked from a list of common ones or typed. Loads and
  library bullets are grouped by caliber name (".30 / 7.62 mm", "6.5 mm"), and a rifle offers only the loads that fit it (same cartridge, or same bullet diameter
  when a load names none), with a switch to show the rest.
- **Bullet library**: over 600 rifle bullets from Barnes, Berger, Hornady, Lapua, Sierra and Speer with
  published G1 and G7 BCs (Berger with length and twist), searchable by maker, name and caliber, plus common
  factory loads with their test barrel lengths. See [tools/bullet-library](tools/bullet-library/README.md) for sources and how to update it.

Muzzle velocity follows the air temperature (taken as the powder temperature) with the load's sensitivity, or a
standard 1.0 m/s per °C when none is entered; factory velocities are referenced to 21 °C (SAAMI, CIP).

### Conditions
Wind first (it changes most), then temperature, pressure, altitude and humidity, look angle and cant, and
Coriolis (latitude), spin drift and aerodynamic jump. Each tab keeps its scroll position.

- **Wind and azimuth dial**: one 360° dial for both, with a switch for which one a tap or drag sets.
  North up it reads like a map: the shot goes out along the azimuth and the wind comes in from its true
  bearing. Otherwise the line of fire points up, the wind reads as a clock and a north marker shows the
  azimuth. Moving the azimuth keeps the wind where it is in the frame shown.
- **Phone compass** for the azimuth, corrected for magnetic declination when the location is known. Each
  reading is checked: the phone's own calibration status and heading accuracy, and the measured field
  against the Earth's field at the location. A warning suggests a figure-eight calibration away from steel.
- **Shot angle from the camera**: aim the crosshair at the target (the camera starts at 10× zoom, which
  picks the telephoto lens where there is one) and the gravity sensor gives the angle, in portrait or
  landscape. A two-aim calibration (the second with the phone turned over) removes the camera's tilt in
  the body; the sensor's own error, typically 0.5–1°, remains.
- **Latitude** with an N/S button for the hemisphere, or from the phone's location with one tap (its accuracy shows under the pin).
- **Pressure from a Garmin watch**: the barometer's station pressure, sent by a small Connect IQ watch
  app through Garmin Connect. See [garmin](garmin/README.md) for installing it.
- **Wind from the forecast**: the hourly forecast for the phone's location
  ([Open-Meteo](https://open-meteo.com), free for non-commercial use) averaged over a chosen window: mean
  speed, vector-mean direction, gusts and how steady the direction is. The 10 m forecast can be scaled to a
  ~2 m line of fire, and the direction is converted to the line of fire with the shot azimuth. Temperature,
  humidity and pressure can be filled in from the same forecast. Settings decide whether a location fix
  also sets the latitude and whether the forecast sets the altitude (terrain height at the location).

### Table
For each range: drop, elevation and clicks, wind drift and windage correction, velocity, energy, time of
flight and Mach. The range column stays in place while the other columns scroll sideways. The table's
range and step are set below it.

### Target
Pick a distance to the metre with the slider, the arrows or by typing it (all in one row), and get the
hold and the dial correction for it. Per reticle design (MRAD and MOA versions together), Settings chooses whether elevation and windage are dialed
or held. Dial corrections are whole clicks and holds are to 0.05; the reticle shows the holds left after dialing for the chosen distance.
- Moving target, switched on with a checkbox: its speed and its course on a small dial (up = away,
  right = to the right). The lead uses the time of
  flight to where the target will be, and its hold point is drawn on the reticle in blue.
- Impact points drawn on the scope's reticle, with a magnification slider; the same holds can be
  previewed on any other reticle.
- Charts at that distance: windage correction by wind speed (one line per wind direction) and by wind
  direction (one line per wind speed), each solved with the full model, so spin drift and aerodynamic
  jump are included.
- Spin drift correction by distance.
- Range card: targets saved as sub-tabs (distance, look angle, azimuth, motion and size). Changes made
  while a target is open are saved to it.
- Wind bracket: corrections for the lightest and the strongest expected wind; the forecast fills both in.
- Range error: vertical miss when the distance is off by up to ±50 m, and the error that still hits a
  target of the given height.
- Temperature: muzzle velocity and elevation 10 °C colder and warmer.
- Hit probability: chance of a first-round hit on the target (its width and height are set here) from the velocity SD (load), the 5-shot
  group (rifle) and how well the wind and the distance are known. Each source's spread comes from the
  full solution; a 5-shot group is taken as three standard deviations.
- Ranging with the reticle: distance from the target size and its reading in the reticle.

### Settings
The gear in the top bar: light, dark or system theme; keep the screen on; tabs and sub-tabs at the top
or the bottom; QNH (default) or station pressure; external devices (Garmin watch for pressure); whether location sets the latitude and the altitude;
dial or hold for elevation and windage, per reticle; sync and backup.

### Sync and backup
- Google sign-in with live sync through Cloud Firestore (newer change wins).
- JSON export and import of all profiles.
- Android Auto Backup.

## Physics

- **3DOF point-mass model** integrated with 4th-order Runge–Kutta (0.5 ms step) in an Earth-fixed frame,
  the model class used by JBM and most modern ballistic calculators.
- **Drag functions** G1, G2, G5, G6, G7, G8, GI, GS and RA4 (BRL data) with monotone cubic (PCHIP)
  interpolation.
- **Velocity-banded BCs** (Sierra style) and **custom Cd(Mach) curves** from Doppler radar (Lapua, Hornady),
  the most accurate option available.
- **Atmosphere**: humid-air density (Buck vapour pressure), speed of sound from virtual temperature,
  ICAO lapse rate along the flight path, station pressure or QNH, density altitude.
- **Wind**: the solver supports 3D wind (including vertical) by range zones; the app currently uses a single
  horizontal wind.
- **Coriolis and Eötvös**: the full −2Ω×v acceleration, with WGS-84 normal gravity by latitude and height.
- **Gyroscopic stability**: Miller formula corrected for velocity and air density.
- **Spin drift** and **aerodynamic jump** from crosswind (Litz).
- **Inclined fire and cant**, **powder temperature sensitivity**, separate zeroing conditions.

### Verification
`ReferenceTrajectoryTest` compares results with the independent
[py-ballisticcalc](https://github.com/o-murphy/py_ballisticcalc) implementation for G1 and G7 with wind out to
1000 m (0.1 MOA tolerance). `PhysicsTest` checks the ICAO atmosphere, the directions of Coriolis, Eötvös,
spin drift and aerodynamic jump, and a hand-computed stability factor. `ScopeMountTest` covers the
sight height helper.

## Installing

Every build of `main` is published as the latest release with the signed APK attached. The newest APK
is always at <https://github.com/krzysztofgw-pl/ballistics-calculator/releases/latest/download/ballistics.apk>:
open it in the phone's browser (a home-screen shortcut makes it one tap) and install it, allowing installs
from unknown sources when asked.

After that the app updates itself: **Settings → About → Check for update** looks at the latest release and
offers to install a newer build, and **Check for update on start** (off by default) does the same at launch. Android asks once to allow installs
from the app.

Pull request builds are not released; their APK is the `ballistics-apk` artifact of the run in the **Actions** tab.

Requires Android 8.0 (API 26) or newer. Builds signed with the release key update each other in place.
Coming from a build signed with a different key or an older package name, export your profiles first,
uninstall the old app, then import them.

### Web app (iPhone and other phones)

Every build of `main` also publishes a web version to <https://krzysztofgw-pl.github.io/ballistics-calculator/>.
It runs the same solver, compiled to JavaScript, and covers profiles, conditions, the table and the target
screen, with the shot angle measured through the camera. On an iPhone open the link in Safari and tap
**Share → Add to Home Screen**: it then opens full screen like an app and works offline. Profiles are kept
in the browser; **Settings → Export / Import** uses the same file as the Android app, so profiles move
between the two. Garmin pressure, the forecast, reticles and cloud sync are Android only for now.

GitHub Pages has to be enabled once: repository **Settings → Pages → Source: GitHub Actions**.

## Project layout

| Path | Contents |
|---|---|
| `core/` | Ballistic solver in Kotlin Multiplatform (JVM for Android, JS for the web), and its tests |
| `app/` | Android app in Jetpack Compose (Material 3) |
| `app/.../data/` | Profiles, bullet library, local storage, Firebase sync |
| `app/.../reticle/` | Reticle definitions |
| `app/.../ui/` | Screens: Profiles, Conditions, Table, Target, editors |
| `web/` | Web app (PWA): Kotlin/JS on the DOM, manifest and service worker in `src/jsMain/resources` |
| `.github/workflows/build.yml` | CI: solver tests, signed release APK, web app on GitHub Pages |

All UI text lives in `app/src/main/res/values/strings.xml`; translations are added as `values-xx` folders.
The web app keeps its text in `web/src/jsMain/kotlin/pl/ballistics/web/Strings.kt`.

## Building

Requires JDK 17.

```bash
./gradlew :core:allTests             # solver tests on the JVM and Node.js, no Android SDK needed
./gradlew :app:assembleRelease       # APK, needs the Android SDK
./gradlew :web:jsBrowserDistribution # web app in web/build/dist/js/productionExecutable
./gradlew :web:jsBrowserDevelopmentRun --continuous # web app at http://localhost:8080 with reload
```

The `app` module is included only when an Android SDK is configured (`local.properties` or `ANDROID_HOME`),
so the solver can be built and tested anywhere.

### Release signing

CI signs the APK with the key from the `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD` repository secrets
(alias `kgwlabs-ballistics`). Without them it falls back to the debug key, and such APKs cannot update an
installed release build. Keep the keystore safe: without it you cannot ship updates.

### Firebase

Cloud sync is enabled when `app/google-services.json` is present; without it the app works offline only.
The Firebase project needs Google sign-in enabled, the release SHA-1 and SHA-256 fingerprints registered for
the Android app, and these Firestore rules, which let each user read and write only their own document:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```
