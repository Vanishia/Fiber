# Fiber

> A lightweight Markdown note-taking app focused on quick capture and local-first storage

[中文](./README.md) | English

[![Android](https://img.shields.io/badge/Android-8.1%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.7+-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

---

## 📱 Introduction

Fiber is a lightweight Markdown note-taking app focused on the **quick capture** experience. Inspired by Flomo and Obsidian, it aims to deliver the snappy feel of a native Android app while staying file-compatible with Obsidian.

### Key Features

- ✍️ **Quick Capture**: a bottom input box — open the app and start writing, save with one tap
- 📁 **Multiple Libraries**: manage multiple folders as separate note libraries
- 🔍 **Full-Text Search**: matches titles, content, and paths, across libraries if you like, with hit snippets and keyword highlighting
- 🔥 **Activity Heatmap**: a heatmap in the sidebar plus a full-year heatmap page, filterable by month/year; tap a cell to view that day's notes
- 🎲 **Random Walk**: randomly revisit a note from the current library or all libraries, read in a full-text dialog
- 📥 **External Share & Import**: receive Markdown files and images shared from other apps, choose a library, and save them into it
- 🖼️ **Images & Attachments**: insert images from both quick capture and the editor, with Markdown image preview
  - 🧹 **Attachment Manager**: browse each library's images with large preview and jump-to-linked-notes; filter linked/unlinked attachments and clean up in batches

- 🎨 **Personalized Themes**: Material 3 Expressive, dynamic color, preset/custom theme colors, and light/dark modes
- 👁️ **Markdown Rendering**: tables, task lists, strikethrough, and images, with edit/preview mode switching
- 💾 **Local-First**: files are stored in a local SAF directory, compatible with Obsidian and more

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│   (Screens, ViewModels, UiState, Components)                │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                           │
│          (UseCases, SyncManager)                            │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                            │
│   (Repository, Database/DAO, FileIndexer, EventBus)         │
├─────────────────────────────────────────────────────────────┤
│                    Framework Layer                          │
│       (SAF, Room, Hilt, Compose, Navigation)                │
└─────────────────────────────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology | Purpose |
|------|------|------|
| **UI** | Jetpack Compose 1.7+ | Declarative UI |
| **UI** | Material 3 Expressive | Design system and dynamic theming |
| **UI** | Navigation Compose 2.8+ | Screen navigation |
| **Data** | Room 2.6+ | Local database (libraries + file metadata) |
| **Data** | Paging 3 | Paged loading (database-level paging) |
| **Data** | SAF | File system access |
| **Architecture** | Hilt | Dependency injection |
| **Architecture** | StateFlow | Reactive state management |
| **Utility** | Timber | Logging |
| **Utility** | Coil | Image loading |

### Module Structure

```
app/src/main/java/com/bird/fiber/
├── MainActivity.kt              # Navigation host + SAF permission handling
├── FiberApplication.kt          # App entry point (HiltAndroidApp)
├── di/
│   └── AppModule.kt             # Hilt module (database, repository bindings)
├── data/
│   ├── local/                   # Local data sources
│   │   ├── library/             # Room database (Entity + DAO)
│   │   ├── AttachmentRepositoryImpl.kt
│   │   ├── FileIndexer.kt       # File indexer
│   │   ├── FileRepositoryImpl.kt
│   │   └── PreviewCache.kt
│   ├── model/                   # Data models (sealed classes)
│   ├── event/                   # Event bus
│   └── repository/              # Repository interfaces
├── domain/
│   ├── usecase/                 # Business use cases
│   └── sync/
│       └── LibrarySyncManager.kt
└── ui/
    ├── navigation/              # Navigation graph
    ├── screens/                 # Feature screens
    │   ├── main/                # Main screen (sidebar + file list + quick note)
    │   ├── filelist/            # File list (Paging 3)
    │   ├── editor/              # Editor (Markwon rendering)
    │   ├── attachments/         # Attachment browsing, link status, cleanup
    │   ├── heatmap/             # Activity heatmap page (month/year filters)
    │   ├── importing/           # External share & file import
    │   ├── notelist/            # All notes / today's notes browsing
    │   ├── sidebar/             # Sidebar (library management, heatmap)
    │   ├── search/              # Full-text search, scope and sorting
    │   ├── settings/            # Settings (font, theme, colors)
    │   └── quicknote/           # Quick note (bottom input box)
    └── theme/                   # Material 3 theme
```

---

## 🚀 Getting Started

### Requirements

- Android Studio (stable)
- JDK 17+
- Android SDK 27+ (Android 8.1+)

### Build the Project

```bash
# Clone the repository
git clone https://github.com/Vanishia/Fiber.git
cd Fiber

# Build the debug APK
./gradlew assembleDebug

# Install on a device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build a Signed Release APK

The release signing config stays on your local machine. Place your keystore file in
`release/` and create `release/keystore.properties` (which is not committed to Git):

```properties
storeFile=fiber-release.jks
storePassword=your-keystore-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Then run:

```bash
./gradlew assembleRelease
```

With this configuration in place, Gradle automatically handles minification, resource
shrinking, and signing. The output is located at
`app/build/outputs/apk/release/app-release.apk`. Without a local signing config the
project still compiles, but produces an unsigned APK that cannot be released directly.

---

## 📖 Usage

### 1. Add a Note Library

On first use, add a note library (folder):
1. Tap "Select Folder"
2. Choose a folder (e.g. `Documents/Notes`)
3. Grant access permission

### 2. Quick Capture

**Option 1: Quick capture**

- Type directly into the bottom input box, then tap the arrow icon at the lower right to save
- Files are auto-named: `YY-MM-DD_HH-mm-ss.md`

**Option 2: Custom file name**

- Tap the edit icon at the top right to enter a file name, create the note, and jump into the full-screen editor

### 3. View/Edit Notes (Editor View)

- Tap any note to enter reading mode
- Tap the edit icon 🖊 at the top right to switch to edit mode, and the eye icon 👁 for preview mode
- Tap the title at the top to rename the file

### 4. Search Notes

Search the current library or all libraries; results can be sorted by relevance or last modified

### 5. Activity Heatmap

- Tap a heatmap cell to view notes from that date
- Tap an empty area of the heatmap to open the past-year or single-month heatmap

### 6. Random Walk

On the search page, tap "Random Walk" to draw a random note to revisit

- Follows the search scope: draw from all libraries or the current one

### 7. Insert Images

- On a new line, type `@` and choose "Image" from the association menu
- The image is copied into the current library's attachment directory and referenced with a relative path in Markdown

### 8. Import from Other Apps

- In other apps, "Open with Fiber" or share Markdown files to Fiber
- You can also share images directly to Fiber
- Choose the target library before saving; the editor opens automatically after a successful save

### 9. Customize Appearance

- In Settings, choose follow-system, light, or dark mode
- On Android 12+, enable system dynamic color, or customize the theme color with the color wheel
- Font size settings apply to the entire interface

---

## 📄 License

This project is open-sourced under the MIT License. See [LICENSE](./LICENSE) for details.

The app icon was drawn by the project author.

## 🙏 Acknowledgements

- [Obsidian](https://obsidian.md/)

- [Flomo](https://flomoapp.com/)

- [Markor](https://github.com/gsantner/markor)

  These three note-taking apps greatly influenced this project's direction and interaction design.

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI framework
