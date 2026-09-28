# MyIULMS

<p align="center">
  <strong>A modern Android client for the Iqra University Learning Management System (IULMS).</strong>
</p>

<p align="center">
  Built with Kotlin, Jetpack Compose, Material 3, OkHttp and Jsoup.
</p>

---

## About

**MyIULMS** is an unofficial Android application built around the existing Iqra University student portal:

**https://iulms.edu.pk/**

The purpose of the project is to give students a cleaner, faster and more mobile-friendly way to access information that is normally viewed through the IULMS website.

The app does not replace the university system. It acts as a native Android client for the existing portal: it signs in using the student's own IULMS credentials, maintains the authenticated session, fetches the relevant student pages, parses the returned data, and presents it through a modern Android interface.

> **MyIULMS is an independent student project. It is not an official Iqra University application and is not affiliated with or endorsed by Iqra University.**

---

## Features

### Authentication
- Sign in using an existing IULMS registration number and password.
- Encrypted **Remember Me** support.
- Automatic session handling and re-login when an IULMS session expires.
- Password show/hide control.
- Credentials are never hard-coded into the repository.

### Exam Results
- Midterm, quiz, project/class-performance and final exam marks
- Total marks
- Grade and grade points
- Semester GPA
- Calculated average marks, highest score and A-grade count when supported by the returned data

### Fee Vouchers
- Voucher description
- Semester
- Voucher number
- Due date
- Amount
- Total outstanding amount
- Calculated due states such as upcoming, due soon, due today and overdue

> Direct voucher download / print support is planned, but is not enabled yet because the exact authenticated IULMS voucher download request still needs to be mapped safely.

### Transcript
- CGPA
- Course code and title
- Credit hours
- Grade and grade points
- Calculated completed credits, total completed courses and A-grade count

### User Interface
- Native **Jetpack Compose** UI
- Material 3
- Iqra-inspired blue and white visual identity
- Light and dark themes
- Persistent theme preference
- Bottom navigation
- Purpose-built loading, empty and error states

---

## How It Works

MyIULMS currently does **not** use a separate custom backend.

The Android app communicates directly with the existing IULMS website:

1. The app starts an IULMS web session.
2. The user's credentials are submitted through the existing login flow.
3. Session cookies are maintained by the app.
4. Browser-like request headers are attached where required.
5. Authenticated IULMS pages are fetched using **OkHttp**.
6. HTML pages are parsed using **Jsoup**.
7. Transcript data is retrieved from the IULMS transcript data service and parsed from JSON.
8. Parsed data is converted into Kotlin models and rendered with Jetpack Compose.
9. If the IULMS session expires, the client can re-authenticate and retry the request.

Because this application depends on the current structure and behavior of **iulms.edu.pk**, changes made to the university website may require corresponding parser updates in MyIULMS.

---

## Security & Privacy

- Passwords are not committed to the repository.
- Remembered credentials are stored locally using **EncryptedSharedPreferences**.
- Android Keystore-backed encryption is used by the secure preference layer.
- Session cookies are managed at runtime.
- Local SDK paths, build outputs, signing files and common secret files are excluded through `.gitignore`.
- The app communicates directly with IULMS rather than routing credentials through a separate third-party application server.

This is still an experimental student project, so users should review the source before using it with their own university account.

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design System | Material 3 |
| State Management | ViewModel + Compose State |
| Networking | OkHttp |
| HTML Parsing | Jsoup |
| Async Work | Kotlin Coroutines |
| Credential Storage | EncryptedSharedPreferences |
| JSON Parsing | `org.json` |
| Minimum Android Version | Android 8.0 / API 26 |

---

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
└── README.md
```

### Main Files

- **`Iulms.kt`** — networking, cookies, sessions, parsers, data models and encrypted credential storage.
- **`MainViewModel.kt`** — login state, loading/error state and data-loading operations.
- **`MainActivity.kt`** — Jetpack Compose screens and main application UI.
- **`ui/theme/`** — light/dark color schemes and typography.

---

## Build & Run

### Requirements
- Android Studio
- Android SDK
- Internet connection
- An existing IULMS student account

### Clone

```bash
git clone https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS.git
```

Open the project in Android Studio, allow Gradle to sync, then run it on a physical Android device or Android emulator.

---

## Current Limitations

- The app depends on the current HTML/JSON structure of IULMS.
- Changes to the IULMS website may break individual parsers.
- Fee voucher download/printing is not connected yet.
- Attendance is not implemented yet.
- Timetable / schedule is not implemented yet.
- Semester-wise transcript grouping is not yet implemented.
- The app has not been tested against every degree program, account type or historical IULMS data variation.

---

## Planned Improvements

- Working fee voucher download / print support
- Attendance section
- Class schedule / timetable
- Semester-wise transcript grouping
- GPA / CGPA simulator
- Academic progress analytics
- Better resilience to IULMS layout changes
- Optional caching for previously loaded academic data
- Improved tablet and large-screen layouts

---

## Why This Project Exists

IULMS already contains the academic information students need, but a native Android interface can make common tasks faster and easier to read on a phone.

MyIULMS explores how the existing portal can be presented as a focused Android experience while keeping the university website as the source of truth.

The project also demonstrates:
- Android development with Kotlin
- Jetpack Compose
- Authenticated HTTP sessions
- HTML and JSON parsing
- State management
- Secure local storage
- Web-backed mobile application design

---

## Disclaimer

**MyIULMS is an independent, unofficial student project.**

- It is not developed, published, maintained, sponsored or endorsed by Iqra University.
- Iqra University, IULMS, their names, logos and related marks belong to their respective owners.
- The application depends on the behavior of **https://iulms.edu.pk/** and may stop working if that website changes.
- Users are responsible for protecting their own IULMS credentials.
- This repository is intended for educational and personal project use.

---

## Author

**Muhammad Ashhad**  
App signature: **Not_Einstein**

GitHub: **[@Muhammmad-Ashhad-Ansari](https://github.com/Muhammmad-Ashhad-Ansari)**
