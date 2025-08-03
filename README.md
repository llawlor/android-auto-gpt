# Android Auto ChatGPT Voice Assistant

A hands-free AI voice assistant for Android Auto that integrates with OpenAI's ChatGPT API. Talk to your AI assistant while driving safely!

## Features

- 🎤 **Voice Recognition**: Speak naturally to interact with ChatGPT
- 🔊 **Text-to-Speech**: Hear responses spoken back to you
- 🚗 **Android Auto Integration**: Works seamlessly in your car's infotainment system
- 🛡️ **Safety First**: Designed for hands-free operation while driving
- 🔐 **Secure**: Your OpenAI API key is stored securely on your device

## Prerequisites

- Android device with Android 6.0 (API level 23) or higher
- Android Auto compatible vehicle or Android Auto app
- OpenAI API key (get one at [platform.openai.com](https://platform.openai.com/api-keys))
- Microphone permission for voice recognition

## Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/android-auto-gpt.git
   cd android-auto-gpt
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build the project**:
   ```bash
   ./gradlew build
   ```

4. **Install on your device**:
   ```bash
   ./gradlew installDebug
   ```

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
   - Press the "Play" button or use voice commands
   - Speak your question or request
   - Listen to ChatGPT's response

### Voice Commands Examples

- "What's the weather like today?"
- "Tell me a joke"
- "Explain quantum physics in simple terms"
- "What are some good restaurants nearby?"
- "Help me plan my day"

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

### Common Issues

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
