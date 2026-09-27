BlockText
An educational Android SMS filter demonstrating Kotlin, Jetpack Compose, and Android’s SMS APIs. It is not a complete or carrier-tested messaging app and should not be used as your daily SMS client.

What it does
Receives and stores SMS messages.
Blocks or always allows specific senders.
Filters incoming SMS by custom word/phrase rules.
Shows why a message was classified as Spam.
Recovers Spam with a sender exception.
Sends short, single-part replies.
Updates folders and conversations automatically.
Includes 27 passing unit tests.

### Example

![Screenshot of BlockText showing a message classified as Spam](BlockText-spam-filter-example)


What it does not do
MMS (picture or group messages) is not handled.
Multipart SMS sending is not supported.
No new conversations, only replies.
No carrier network reliability testing.
Not a full replacement for your phone’s default messaging app.
Setup
Android Studio: recent version with a Pixel API 35 emulator.
Run: select the emulator and click ▶ Run.
Permissions: set BlockText as the default SMS app when prompted.
Test: simulate incoming SMS using the emulator’s Extended Controls.
Important limitations
Emulator-only project: safe for learning and demonstration, not for real carrier use.
No MMS support: picture and group messages are not handled.
Single-part replies: longer outgoing messages are rejected.
No carrier testing: behavior on real cellular networks is unknown.
Do not use as your primary messaging app.

## License

This project's original code is licensed under the MIT License; see LICENSE.
Third-party components retain their respective licenses.

Credits
This project uses:

Android SDK and Jetpack Compose
libphonenumber (Google) for sender normalization
Kotlin standard library
