# Balistyka: kalkulator balistyczny na Androida

Aplikacja studia **KGW Labs** (pakiet `pl.kgwlabs.balistyka`).

Kalkulator trajektorii pocisku oparty na ogólnodostępnych, uznanych modelach fizycznych.
Aplikacja w Kotlinie i Jetpack Compose, solver jako osobny moduł w czystym Kotlinie.

## Moduły

| Moduł | Zawartość |
|---|---|
| `core/` | Solver balistyczny (czysta JVM, bez zależności od Androida), testy |
| `app/` | Aplikacja Android: profil broni i amunicji, warunki, tabela poprawek |

## Modele fizyczne

- **Punkt materialny 3DOF** całkowany metodą Rungego–Kutty 4. rzędu (krok 0,5 ms) w układzie
  związanym z Ziemią. To ten sam model, którego używają JBM, Applied Ballistics i Hornady 3DOF.
- **Funkcje oporu G1, G2, G5, G6, G7, G8, GI, GS, RA4** (dane BRL), interpolowane monotonicznie (PCHIP).
- **BC w przedziałach prędkości** (styl Sierra) oraz **własna krzywa Cd(Mach)** z pomiarów Dopplera
  (Lapua, Hornady), czyli najdokładniejsza dostępna opcja.
- **Atmosfera**: gęstość powietrza wilgotnego (ciśnienie pary nasyconej wg Bucka), prędkość dźwięku
  z temperatury wirtualnej, gradient ICAO wzdłuż toru lotu, ciśnienie stacji lub QNH, wysokość gęstościowa.
- **Wiatr** 3D (także pionowy), w strefach odległości.
- **Coriolis i Eötvös**: pełne przyspieszenie −2Ω×v, grawitacja normalna WGS‑84 zależna od szerokości i wysokości.
- **Stabilność żyroskopowa Millera** z poprawkami na prędkość i gęstość powietrza.
- **Znos żyroskopowy (spin drift)** i **skok aerodynamiczny** od wiatru bocznego wg Litza.
- **Strzał pod kątem i przechył broni**, **czułość prochu na temperaturę**, osobne warunki przystrzelania.

## Aplikacja

- **Profile broni**: karabin, luneta (jednostki i wartość kliku), wysokość montażu, zero i przypisana amunicja.
  Pomocnik wysokości montażu liczy ją z pomiaru suwmiarką albo szacuje z obiektywu, pierścieni i szyny.
- **Profile amunicji**: z biblioteki danych katalogowych (pociski Hornady, Sierra, Berger i amunicja fabryczna)
  albo własne, w tym krzywa Cd(Mach).
- **Siatki celownicze**: punkty trzymania dla każdej odległości narysowane na siatce lunety (MRAD 0,2, drzewko MRAD,
  Mil-Dot, MOA, drzewko MOA, Duplex), z obsługą pierwszej i drugiej płaszczyzny (dla SFP suwak powiększenia)
  oraz podglądem tych samych poprawek na innej siatce.
- **Synchronizacja**: eksport/import wszystkich profili do pliku JSON (np. na Dysk Google) oraz automatyczna
  kopia zapasowa Androida na koncie Google.

## Weryfikacja

`ReferenceTrajectoryTest` porównuje wyniki z niezależną implementacją
[py-ballisticcalc](https://github.com/o-murphy/py_ballisticcalc) dla G1 i G7 z wiatrem do 1000 m
(tolerancja 0,1 MOA). `PhysicsTest` sprawdza atmosferę ICAO, kierunki efektów Coriolisa,
Eötvösa, znosu i skoku aerodynamicznego oraz wartość Sg policzoną ręcznie.

## Budowanie

```bash
./gradlew :core:test           # solver, działa bez Android SDK
./gradlew :app:assembleRelease # APK, wymaga Android SDK
```

Każdy push buduje APK w GitHub Actions: zakładka **Actions** → ostatni przebieg → artefakt `balistyka-apk`.
