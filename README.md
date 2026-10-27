# Ballistics: an Android ballistic calculator

An app by **KGW Labs** (package `com.kgwlabs.ballistics`).

A trajectory calculator built on established, publicly documented physical models.
The app is written in Kotlin with Jetpack Compose; the solver is a separate pure-Kotlin module.

## Modules

| Module | Contents |
|---|---|
| `core/` | Ballistic solver (pure JVM, no Android dependencies) and its tests |
| `app/` | Android app: rifle and load profiles, conditions, dope table, reticle holds, sync |

## Physics

- **3DOF point-mass model** integrated with 4th-order Runge–Kutta (0.5 ms step) in an Earth-fixed frame.
  This is the model behind JBM, Applied Ballistics and Hornady 3DOF.
- **Drag functions G1, G2, G5, G6, G7, G8, GI, GS, RA4** (BRL data) with monotone (PCHIP) interpolation.
- **Velocity-banded BCs** (Sierra style) and **custom Cd(Mach) curves** from Doppler radar
  (Lapua, Hornady), the most accurate option available.
- **Atmosphere**: humid-air density (Buck vapour pressure), speed of sound from virtual temperature,
  ICAO lapse rate along the flight path, station pressure or QNH, density altitude.
- **3D wind** (including vertical), by range zones.
- **Coriolis and Eötvös**: the full −2Ω×v acceleration, WGS-84 normal gravity by latitude and height.
- **Miller gyroscopic stability** corrected for velocity and air density.
- **Spin drift** and **aerodynamic jump** from crosswind (Litz).
- **Inclined fire and cant**, **powder temperature sensitivity**, separate zeroing conditions.

## App

- **Rifle profiles**: rifle, scope (turret units and click value, reticle, focal plane), sight height,
  zero and the load it is zeroed with. A helper computes the sight height from a caliper measurement or
  estimates it from the objective, rings and rail.
- **Load profiles**: from a library of catalogue data (Hornady, Sierra and Berger bullets and factory loads)
  or custom, including a Cd(Mach) curve.
- **Reticle holds**: impact points for every range drawn on the scope's reticle (MRAD 0.2 hash, MRAD tree,
  Mil-Dot, MOA hash, MOA tree, Duplex), first and second focal plane (with a magnification slider for SFP),
  and a preview of the same holds on any other reticle.
- **Sync**: Google sign-in with live sync through Firestore, JSON export/import of all profiles, and Android
  Auto Backup.

All UI text lives in `app/src/main/res/values/strings.xml`, so translations are added as `values-xx` folders.

## Verification

`ReferenceTrajectoryTest` compares results with the independent
[py-ballisticcalc](https://github.com/o-murphy/py_ballisticcalc) implementation for G1 and G7 with wind out to
1000 m (0.1 MOA tolerance). `PhysicsTest` checks the ICAO atmosphere, the directions of Coriolis, Eötvös,
spin drift and aerodynamic jump, and a hand-computed Sg.

## Building

```bash
./gradlew :core:test           # solver, works without the Android SDK
./gradlew :app:assembleRelease # APK, needs the Android SDK
```

Every push builds an APK in GitHub Actions: **Actions** tab → latest run → `ballistics-apk` artifact.

### Release signing

CI signs the APK with the key from the `SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD` repository secrets
(alias `kgwlabs-ballistics`). Without them it falls back to the debug key, and such APKs cannot update an installed
release build.

### Firebase

Cloud sync is enabled when `app/google-services.json` is present. The Firestore rules expected by the app:

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
