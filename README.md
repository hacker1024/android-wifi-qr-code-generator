# Wifi QR Code Generator
An android app that generates QR codes from your saved wifi networks.

## Usage
The app needs root access to read your saved wifi information.<br>
Tap a wifi entry to display its QR code.<br>
Hold a wifi entry to display more details.

## What I'm working on next (from highest to lowest significance)
- Bug fixes (always at the top of the list)
- ~~Sort networks alphabetically~~ Done.
- ~~An xposed component that adds an option in the stock wifi settings to display the QR code/password~~ Done.
- ~~Backport to older android versions~~ Done.
- An about page
- ~~Setting to choose the quality of the QR code generated~~ [Done.](https://github.com/hacker1024/android-wifi-qr-code-generator/commit/22a23887bc334000e5c71f66fcbbfda0197d7348)
- ~~A dark theme~~ Done.
- Make new bugs

## Possible issues
I need feedback to see if these are only happening to some people or if they're happening to everyone.
- ~~WEP networks may cause an error.~~ [Fixed.](https://github.com/hacker1024/android-wifi-qr-code-generator/commit/613c555453f9944d8d772faaa2c6d8c508deca76)

## Third-party libraries/code
- [Parsing](https://github.com/David-Mawer/OreoWifiPasswords/blob/0d146fd34ce424b8a500a441ff2a1293c3355a33/app/src/main/java/com/pithsoftware/wifipasswords/task/TaskLoadWifiEntries.java) and [data management code](https://github.com/David-Mawer/OreoWifiPasswords/blob/ae0d7e7f290345bdf1a2d0742b8da5d25a76807b/app/src/main/java/com/pithsoftware/wifipasswords/pojo/WifiEntry.java) by [David-Mawer](https://github.com/David-Mawer/) has been used, adapted, and converted to Kotlin.
- [QRGen: a simple QRCode generation api for java built on top ZXING](https://github.com/kenglxn/QRGen)

## License
Copyright (C) 2018 hacker1024 (on github.com)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
(at your option) any later version.

This program is distributed in the hope that it will be useful,
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
