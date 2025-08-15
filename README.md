# Android Auto ChatGPT Voice Assistant

A hands-free AI voice assistant for Android Auto that integrates with OpenAI's ChatGPT API. Talk to your AI assistant while driving safely!

## 🚀 Quick Start

**Ready to use!** Pre-built APK files are available for immediate download and installation. No build setup required!

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/llawlor/android-auto-gpt)
[![Android](https://img.shields.io/badge/Android-6.0%2B-green)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-23%2B-orange)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
[![Tested](https://img.shields.io/badge/tested-Android%2010-blue)](https://developer.android.com)

## Features

- 🎤 **Voice Recognition**: Speak naturally to interact with ChatGPT
- 🔊 **Text-to-Speech**: Hear responses spoken back to you
- 🚗 **Android Auto Integration**: Works seamlessly in your car's infotainment system
- 🛡️ **Safety First**: Designed for hands-free operation while driving
- 🔐 **Secure**: Your OpenAI API key is stored securely on your device

## Prerequisites

- **Android Device**: Android 6.0+ (API 23+) - Tested on Android 10
- **Android Auto**: Compatible vehicle or Android Auto app
- **OpenAI API Key**: Get one at [platform.openai.com](https://platform.openai.com/api-keys)
- **Permissions**: Microphone access for voice recognition
- **Storage**: 200+ MB free space for installation

### Compatibility

**Supported Android Versions:**
- ✅ Android 6.0+ (Marshmallow) - Minimum requirement
- ✅ Android 10 - Fully tested and confirmed working
- ✅ Android 11, 12, 13, 14 - Expected to work (built with API 34)

**Android Auto Support:**
- ✅ Wired Android Auto connection
- ✅ Wireless Android Auto (where supported)
- ✅ Android Auto on phone screens
- ✅ Aftermarket and built-in head units

## Installation

### Option 1: Download Pre-built APK (Recommended)

**Quick Install - No Build Required:**

1. **Download APK from GitHub**:
   - Go to [Releases](https://github.com/llawlor/android-auto-gpt/tree/main/app/build/outputs/apk)
   - Download `app-debug.apk` (7.6 MB) for testing
   - Or download `app-release-unsigned.apk` (6.1 MB) for smaller size

2. **Enable Unknown Sources on Android**:
   - **Android 8.0+**: Settings > Apps > Special app access > Install unknown apps > [Your Browser] > Allow from this source
   - **Android 7.1 and below**: Settings > Security > Unknown Sources (enable)

3. **Install the APK**:
   - Tap the downloaded APK file
   - Follow installation prompts
   - Grant required permissions (microphone, etc.)

### Option 2: Build from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/llawlor/android-auto-gpt.git
   cd android-auto-gpt
   ```

2. **Build the project**:
   ```bash
   ./gradlew build
   ```

3. **Install on your device**:
   ```bash
   ./gradlew installDebug
   ```

**Build Requirements:**
- Android SDK with API 23+ and build-tools 33.0.1+
- Java 21 or compatible JDK
- Gradle 8.5+ (included via wrapper)

## Setup

1. **Get OpenAI API Key**:
   - Visit [OpenAI API Keys](https://platform.openai.com/api-keys)
   - Create a new API key
   - Copy the key (starts with `sk-`)

2. **Configure the App**:
   - Open the app on your phone
   - Tap "Settings"
   - Enter your OpenAI API key
   - Tap "Save Settings"

3. **Grant Permissions**:
   - Allow microphone access when prompted
   - This is required for voice recognition

## Usage

### In Android Auto

1. **Connect to Android Auto**:
   - Connect your phone to your car via USB or wireless
   - Launch Android Auto on your car's display

2. **Find the App**:
   - Look for "AI Voice Assistant" in the media apps section
   - Tap to open the app

3. **Start Talking**:
   - Use voice commands to interact with the AI assistant
   - Say "Search [your question] on Auto Voice Assistant"
   - Listen to ChatGPT's response

### Voice Commands Examples

**Natural Voice Commands:**
- "Search what's the weather like today on Auto Voice Assistant"
- "Search tell me a joke on Auto Voice Assistant"
- "Search explain quantum physics in simple terms on Auto Voice Assistant"
- "Search what are some good restaurants nearby on Auto Voice Assistant"
- "Search help me plan my day on Auto Voice Assistant"

**Alternative Pattern (also works):**
- "Play what's the weather like today on Auto Voice Assistant"
- "Play tell me a joke on Auto Voice Assistant"

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/autovoiceassistant/
│   │   ├── MainActivity.kt              # Main app activity
│   │   ├── SettingsActivity.kt          # Settings configuration
│   │   ├── AutoVoiceAssistantService.kt # Android Auto service
│   │   ├── VoiceManager.kt              # Voice recognition & TTS
│   │   └── OpenAIClient.kt              # OpenAI API integration
│   ├── res/
│   │   ├── layout/                      # UI layouts
│   │   ├── values/                      # Strings, colors, themes
│   │   ├── xml/                         # Android Auto configuration
│   │   └── drawable/                    # Icons and graphics
│   └── AndroidManifest.xml              # App permissions & components
└── build.gradle                         # Dependencies & build config
```

## Key Components

### OpenAIClient.kt
Handles communication with OpenAI's ChatGPT API:
- Sends user messages to ChatGPT
- Receives and processes AI responses
- Handles API errors gracefully

### VoiceManager.kt
Manages voice input and output:
- Speech recognition for user input
- Text-to-speech for AI responses
- Audio state management

### AutoVoiceAssistantService.kt
Android Auto integration:
- MediaBrowserService for Android Auto compatibility
- Handles play/pause/stop controls
- Manages voice interaction lifecycle

## Safety Features

- **Hands-free Operation**: Designed for voice-only interaction
- **Concise Responses**: AI responses optimized for audio consumption
- **Error Handling**: Graceful handling of network and API errors
- **Permission Management**: Proper microphone permission handling

## Security

- **Local Storage**: API keys stored securely on device
- **No Data Collection**: No user data sent to third parties
- **Secure Communication**: HTTPS communication with OpenAI
- **Backup Exclusion**: Sensitive data excluded from backups

## Troubleshooting

### APK Installation Issues

1. **"App not installed" error**:
   - Enable "Install unknown apps" for your browser/file manager
   - Clear Downloads cache: Settings > Apps > Downloads > Storage > Clear Cache
   - Ensure sufficient storage space (200+ MB free)
   - Try downloading the smaller release APK instead
   - Restart device and try again

2. **"Unknown sources" not working**:
   - **Android 8.0+**: Enable per-app in Settings > Apps > Special app access > Install unknown apps
   - **Android 7.1 and below**: Enable globally in Settings > Security > Unknown Sources
   - Try using "Files by Google" app to install the APK

### App Usage Issues

1. **"Please set your OpenAI API key"**:
   - Go to Settings and enter a valid OpenAI API key
   - Ensure the key starts with `sk-`

2. **"Microphone permission required"**:
   - Grant microphone permission in Android settings
   - Restart the app after granting permission

3. **"Network error"**:
   - Check your internet connection
   - Ensure your device has data/WiFi access

4. **App not showing in Android Auto**:
   - Ensure Android Auto is properly set up
   - Check that the app is installed and configured
   - Try disconnecting and reconnecting to Android Auto

### Debug Mode

To enable debug logging:
1. Open Android Studio
2. View logs in Logcat with tag "AutoVoiceAssistant"

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/your-username/android-auto-gpt.git
cd android-auto-gpt

# Build debug version
./gradlew assembleDebug

# Build release version
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Dependencies

- **Android Auto**: Media browser service integration
- **Speech Recognition**: Android's built-in speech recognition
- **Text-to-Speech**: Android's TTS engine
- **OkHttp**: HTTP client for API calls
- **Gson**: JSON parsing for API responses
- **Kotlin Coroutines**: Asynchronous operations

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Disclaimer

- This app requires an OpenAI API key and usage will incur costs based on OpenAI's pricing
- Always prioritize safety while driving - pull over if you need to interact with your phone
- The app is designed for hands-free use but should not replace your attention to the road

## Support

If you encounter issues or have questions:
1. Check the troubleshooting section above
2. Review the project's issues on GitHub
3. Create a new issue with detailed information about your problem

---

**Made with ❤️ for safe and intelligent driving**
