# Email Assistant App

A modern Android application for efficient email management, powered by AI. Built with Jetpack Compose and Firebase, it offers secure authentication, smart email summarization, and a chat-like interface for productivity.

---

## Table of Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgments](#acknowledgments)

---

## Features

- Secure user authentication (Firebase)
- AI-powered email summarization and response suggestions (Gemini 2.0 Flash)
- Gmail connectivity with 2FA and app password support
- Chat-style UI for summaries and replies
- Real-time feedback and error handling
- Responsive Material Design 3 interface

## Screenshots

### 1. Login and Registration

- **Login Screen**

  <img src="app/docs/Login.jpg" width="400"/>

- **New Registration**

  <img src="app/docs/New_Registration.jpg" width="400"/>

---

### 2. Dashboard

- **Connecting Gmail**

  <img src="app/docs/connecting_gmail.jpg" width="400"/>

- **Welcome Page**

  <img src="app/docs/welcome_page.jpg" width="400"/>

- **Sidebar**

  <img src="app/docs/Sidebar.jpg" width="400"/>

---

### 3. AI Summaries of Emails

- **AI Summary Example 1**

  <img src="app/docs/AI_Summary_1.jpg" width="400"/>

- **AI Summary Example 2**

  <img src="app/docs/AI_Summary_2.jpg" width="400"/>

---
### 4. AI Suggested Responses

- **Supporting the Agenda**

  <img src="app/docs/Response_supporting.jpg" width="400"/>

- **Against the Agenda**

  <img src="app/docs/Response_against.jpg" width="400"/>

---

## Project Structure

```
app/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── emailassistantapp/
│       │               ├── components/
│       │               ├── network/
│       │               ├── userinterface/
│       │               ├── viewmodel/
│       │               └── data/
│       └── res/
│           ├── drawable/
│           ├── values/
│           ├── mipmap/
│           └── xml/
├── docs/
│   ├── documentation and screenshots
│   
├── build.gradle.kts
├── proguard-rules.pro
├── google-services.json
```

---

## Getting Started

### Prerequisites

- Android Studio Arctic Fox (2020.3.1) or newer
- Android SDK 23 or higher
- Kotlin 1.8.0 or higher
- Gradle 7.0 or higher

### Setup Instructions

1. **Clone the repository:**
   ```sh
   git clone https://github.com/yourusername/EmailAssistantApp.git
   ```
2. **Open in Android Studio:**
   - Open Android Studio and select "Open an existing project"
   - Navigate to the cloned repository
3. **Configure Firebase:**
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app, download `google-services.json`, and place it in the `app/` directory
4. **Add Gemini API Key:**
   - Add your Gemini 2.0 Flash API key to `local.properties` as `GEMINI_API_KEY=your_api_key_here`
5. **Build and Run:**
   - Sync Gradle, build the project, and run on an emulator or device

---

## Contributing

Contributions are welcome!

- Fork this repository
- Create a new branch for your feature or bugfix
- Commit your changes with clear messages
- Open a pull request

---

## License

This project is licensed under the [MIT License](LICENSE).

---

## Acknowledgments

- Jetpack Compose team
- Firebase team
- Material Design team
- Gemini API team
