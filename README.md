
# 🚆 TrainApp

**An Android application to browse train routes, set reminders, and stay updated with schedule changes using Jetpack Compose.**

![Active users](badges/mau.svg?raw=1)
![Total users](badges/total.svg?raw=1)


## Disclaimer

This application is **not** the official timetable or service app of **ŽPCG AD Podgorica** (Željeznički prevoz Crne Gore).  
For official train schedules, ticketing, and company information, please refer to the official ŽPCG website:

👉 [https://www.zpcg.me](https://www.zpcg.me)


---

## 📖 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [Project Structure](#-project-structure)
- [Architecture & Libraries](#️-architecture--libraries)
- [Screens & Workflows](#-screens--workflows)
- [Installation](#-installation)
- [Usage](#-usage)
- [Reminders & Permissions](#-reminders--permissions)
- [Settings & Customization](#️-settings--customization)
- [Contributions](#-contributions)
- [License](#-license)

---



## 🚀 Overview

**TrainApp** is a Kotlin-based Android app built using **Jetpack Compose** to easily search, view, and manage train schedules.

You can:
- ✅ **Select Origin/Destination stations.**
- 📅 **Choose a date to search routes.**
- 💾 **Save your favorite routes.**
- 🔔 **Set push or calendar reminders for departures.**
- 🌐 **Choose station name language (English, Montenegrin Latin/Cyrillic).**
- 🔄 **Check app updates seamlessly.**

**Why Compose?**  
Jetpack Compose provides a declarative, modern UI with minimal XML, simplifying development and enhancing maintainability.

---

## 🌟 Key Features

- 🎨 **Material 3 Design** (Dynamic themes, Light/Dark mode)
- ✏️ **Station Autocomplete**
- 💼 **Saved Routes**
- 🛤️ **Direct & Connected routes**
- ⏳ **Live Time-to-Departure countdown**
- 🔄 **In-App Update Check (GitHub)**
- 📅 **Notifications & Calendar integration**
- 🗄️ **Room Database (Offline Support)**

---

## 📂 Project Structure

```
train/
├─ MainActivity.kt
├─ SettingsActivity.kt
├─ data/
│  ├─ api/ (Retrofit)
│  ├─ db/ (Room DB)
│  ├─ model/ (Data models)
│  └─ repository/ (Data layer)
├─ ui/ (Compose UI)
│  ├─ AutoCompleteTextField.kt
│  ├─ MainScreen.kt
│  ├─ SearchPanel.kt
│  ├─ RouteCard.kt
│  ├─ FullRouteDialog.kt
│  ├─ ReminderChoiceDialog.kt
│  ├─ SettingsScreen.kt
│  ├─ SavedRoutesBlock.kt
│  ├─ theme/ (M3 Design)
│  └─ TrainViewModel.kt
└─ util/ (Helpers & Notifications)
```

---

## 🛠️ Architecture & Libraries

- **MVVM Pattern**
  - **Model:** Data classes, Room entities
  - **ViewModel:** Business logic & data handling
  - **View:** Jetpack Compose UI
- **Networking:** Retrofit
- **Database:** Room
- **Notifications:** AlarmManager & Broadcast Receivers
- **UI:** Material 3 (Compose)

---

## 📱 Screens & Workflows

### 🏠 **Main Screen**
- Search "From/To" stations
- Direct & Connected routes displayed
- Quick-add reminders

### ⚙️ **Settings Screen**
- Language selection
- Default reminder preferences
- Auto-refresh departures

---

## 💻 Installation

**Clone repository**
```bash
git clone https://github.com/<YourUsername>/TrainApp.git
cd TrainApp
```

**Open in Android Studio**
- File → Open → Select project folder.
- Let Gradle sync.

**Build/Run**
- Connect Android device/emulator.
- Click ▶ (Run).

**Requirements:**
- Min SDK: 21+
- Target SDK: 33 (Android 13)
- Kotlin: 1.8.x
- Gradle: 7.x

---

## 📌 Usage

1. Enter **"From"** and **"To"** stations.
2. Pick a date or use today.
3. Tap 🔍 to search routes.
4. Set reminders (bell icon), save routes, or view intermediate stops.

---

## 🔔 Reminders & Permissions

- **Exact Alarms:** Permission needed (Android 12+)
- **Notifications:** `POST_NOTIFICATIONS` permission (Android 13+)
- **Calendar:** User confirmation required (no explicit runtime permission)

---

## ⚙️ Settings & Customization

- **Language settings** (English, Montenegrin Latin/Cyrillic)
- **Reminder options** (push/calendar/both/none)
- **Auto Refresh**
- **Test Push Notifications**

---

## 🤝 Contributions

Contributions, bug reports, and feature requests are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -m 'Add new feature'`)
4. Push your changes (`git push origin feature/new-feature`)
5. Open a Pull Request

Thanks for improving TrainApp!

---

## 📄 License

Distributed under **MIT License**.

Enjoy your journey with **TrainApp**! If you like the project, don't forget to ⭐ it on GitHub.
