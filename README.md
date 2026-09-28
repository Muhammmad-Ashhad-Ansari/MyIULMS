# MyIULMS

<p align="center">
  <strong>A modern, unofficial Android client for Iqra University IULMS.</strong>
</p>

<p align="center">
  Kotlin • Jetpack Compose • Material 3 • OkHttp • Jsoup
</p>

<p align="center">
  <a href="https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/releases/latest">
    <img alt="Latest Release" src="https://img.shields.io/github/v/release/Muhammmad-Ashhad-Ansari/MyIULMS?display_name=tag&style=for-the-badge">
  </a>
  <a href="https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/blob/main/LICENSE">
    <img alt="MIT License" src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge">
  </a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/releases/latest"><strong>Download latest APK</strong></a>
  ·
  <a href="https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/issues">Report an issue</a>
</p>

---

## Overview

**MyIULMS** is a native Android client built around the existing Iqra University Learning Management System at **iulms.edu.pk**.

It signs in through the student's existing IULMS account, keeps the authenticated session, fetches academic information from the portal, parses the returned HTML/JSON, and presents it through a cleaner mobile-first interface.

> [!IMPORTANT]
> MyIULMS is an **independent student project**. It is not an official Iqra University application and is not affiliated with, maintained by, sponsored by, or endorsed by Iqra University.

## Current Release

**v0.2.1**

The current release includes:

- Logged-in student name in the app header
- Working light/dark theme toggle on both login and authenticated screens
- Persistent theme preference
- Encrypted Remember Me support
- Exam results, fee vouchers, and transcript views
- Academic summary metrics and fee due-state calculations

> The APK currently published in GitHub Releases is a **debug build intended for testing**. A properly signed production release is planned for a later milestone.

## Features

| Area | What MyIULMS currently provides |
|---|---|
| **Authentication** | Login with existing IULMS credentials, encrypted Remember Me, automatic session recovery |
| **Student identity** | Displays the authenticated student's name after login |
| **Exam results** | Marks breakdown, total, grade, grade points, semester GPA |
| **Result insights** | Average marks, highest score, A-grade count where supported |
| **Fee vouchers** | Voucher number, semester, due date, description, amount |
| **Fee insights** | Total outstanding amount and derived due/overdue status |
| **Transcript** | CGPA, courses, credit hours, grades, grade points |
| **Transcript insights** | Completed credits, course count, A-grade count |
| **UI** | Jetpack Compose + Material 3, Iqra-inspired palette, light/dark themes |
| **Reliability** | Cookie-based session handling, automatic re-login and retry |

## How It Works

MyIULMS currently uses **no custom backend**. The app communicates directly with the existing IULMS website.

```text
Student
   │
   ▼
MyIULMS Android App
   │
   ├── Login + session cookies
   ├── OkHttp requests
   ├── Jsoup HTML parsing
   └── JSON transcript parsing
   │
   ▼
iulms.edu.pk
```

Typical flow:

1. Start an IULMS web session.
2. Submit the student's registration number and password through the existing login flow.
3. Maintain authenticated cookies in memory.
4. Fetch the required IULMS pages using OkHttp.
5. Parse HTML using Jsoup and transcript JSON using `org.json`.
6. Convert the returned data into Kotlin models.
7. Render the information with Jetpack Compose.
8. Re-authenticate and retry automatically when the IULMS session expires.

Because MyIULMS depends on the current behavior and structure of the university portal, changes to **iulms.edu.pk** may require parser updates.

## Security & Privacy

MyIULMS is designed to avoid unnecessary handling of student credentials:

- Credentials are **not hard-coded** into the repository.
- Remembered login details are stored locally using **EncryptedSharedPreferences**.
- Android Keystore-backed encryption is used by the secure preference layer.
- Session cookies are held at runtime by the app's HTTP client.
- The app communicates directly with IULMS instead of sending credentials through a separate third-party backend.
- Common secret files, signing files, local SDK configuration, and build output are excluded through `.gitignore`.

> [!CAUTION]
> This is still an experimental student project. Review the source before using it with your own university account.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design system | Material 3 |
| State | Android ViewModel + Compose state |
| Networking | OkHttp 4.12 |
| HTML parsing | Jsoup 1.17 |
| Async work | Kotlin Coroutines |
| Local credential storage | EncryptedSharedPreferences |
| JSON parsing | `org.json` |
| Minimum Android version | Android 8.0 / API 26 |
| Target SDK | Android API 37 |

## Project Structure

```text
MyIULMS/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/myiulms/
│   │   │   ├── Iulms.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── MainViewModel.kt
│   │   │   └── ui/theme/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE
└── README.md
```

### Core Files

- **`Iulms.kt`** — networking, session cookies, data models, parsers, and encrypted credential storage.
- **`MainViewModel.kt`** — authentication state, student identity, loading/error state, and data-loading operations.
- **`MainActivity.kt`** — Compose screens, navigation, theme controls, and the main UI.
- **`ui/theme/`** — Iqra-inspired light/dark color schemes and typography.

## Build & Run

### Requirements

- Android Studio
- Android SDK
- Internet connection
- An existing IULMS student account

### Clone

```bash
git clone https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS.git
cd MyIULMS
```

Open the project in Android Studio, allow Gradle to sync, and run it on a physical Android device.

You can also build a debug APK from the project root:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew assembleDebug
```

The generated APK is placed under:

```text
app/build/outputs/apk/debug/
```

## Download

Testing builds are published through **GitHub Releases**:

**[https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/releases/latest](https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/releases/latest)**

The latest published build at the time of this README update is **v0.2.1**.

## Current Limitations

- Fee voucher download/printing is not connected yet.
- Attendance is not implemented yet.
- Timetable / class schedule is not implemented yet.
- Semester-wise transcript grouping is not implemented yet.
- The app depends on the current IULMS HTML/JSON structure.
- It has not been tested against every degree program, student account type, or historical data variation.
- The current downloadable APK is debug-signed rather than a production-signed release.

## Roadmap

- [ ] Authenticated fee voucher download / print
- [ ] Attendance section
- [ ] Class schedule / timetable
- [ ] GPA / CGPA calculator and target-GPA simulation
- [ ] Semester-wise transcript grouping
- [ ] Academic dashboard and progress insights
- [ ] Better resilience to IULMS layout changes
- [ ] Optional local caching
- [ ] Tablet / large-screen improvements
- [ ] Properly signed release APK

## Contributing

Issues, bug reports, and pull requests are welcome.

If IULMS changes its page structure and a parser stops working, please include enough non-sensitive detail to reproduce the issue. **Do not post passwords, active session cookies, authentication tokens, or other private account data.**

## Disclaimer

MyIULMS is an independent, unofficial student project.

- It is not developed, published, maintained, sponsored, or endorsed by Iqra University.
- Iqra University, IULMS, their names, logos, and related marks belong to their respective owners.
- The application depends on **iulms.edu.pk** and may stop working when the upstream website changes.
- Users are responsible for protecting their own IULMS credentials and account access.

## License

This project's source code is available under the **MIT License**. See [`LICENSE`](LICENSE).

The MIT License permits use, copying, modification, distribution, sublicensing, and commercial use subject to retaining the copyright and permission notice. It is provided without warranty.

University names, logos, and other third-party marks/assets are **not granted additional rights by this repository's MIT License** and remain the property of their respective owners.

## Author

**Muhammad Ashhad**  
App signature: **Not_Einstein**

GitHub: [@Muhammmad-Ashhad-Ansari](https://github.com/Muhammmad-Ashhad-Ansari)

---

<p align="center">
  Built as a student-driven Android project to make everyday IULMS access cleaner on mobile.
</p>
