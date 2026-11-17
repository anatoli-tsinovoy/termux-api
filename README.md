# Termux API

[![Build status](https://github.com/termux/termux-api/workflows/Build/badge.svg)](https://github.com/termux/termux-api/actions)
[![Join the chat at https://gitter.im/termux/termux](https://badges.gitter.im/termux/termux.svg)](https://gitter.im/termux/termux)

This is an app exposing Android API to command line usage and scripts or programs.

When developing or packaging, note that this app needs to be signed with the same
key as the main Termux app for permissions to work (only the main Termux app are
allowed to call the API methods in this app).

## Accessibility fork

The `accessibility` branch builds on upstream PR #840 and adds swipe gestures.
Pair it with the [`accessibility` CLI branch](https://github.com/anatoli-tsinovoy/termux-api-package/tree/accessibility).
Build using JDK 17 and Android SDK 35 with `./gradlew assembleDebug`, or download
the artifact from this fork's GitHub Action Build workflow. The debug APK uses
the public GitHub test key; update only an installation with a matching signer.
Do not uninstall Termux to bypass a signing mismatch without an external backup.

Enable Termux:API manually in Android Settings → Accessibility. This grants broad
cross-app access to window text, screenshots, text entry, gestures, and system
actions. Disable the service in the same settings when not needed. Android 11+
is the practical requirement for the current display/screenshot implementation.

```sh
termux-accessibility list-displays
termux-accessibility dump [display-id]
termux-accessibility click x y [display-id] [duration-ms]
termux-accessibility swipe x1 y1 x2 y2 [display-id] [duration-ms]
```

Display defaults to 0; click duration defaults to 1 ms and swipe to 300 ms.
Click/swipe return JSON with `success: true` only after Android reports completion;
disabled service, invalid input, rejected/cancelled gestures, and timeouts return
`success: false` with an `error`. Completion confirms Android's gesture callback,
not that a particular UI control acted: inspect the UI afterward.

## Installation

Latest version is `v0.53.0`.

Termux:API application can be obtained from [F-Droid](https://f-droid.org/en/packages/com.termux.api/).

Additionally we provide per-commit debug builds for those who want to try
out the latest features or test their pull request. This build can be obtained
from one of the workflow runs listed on [Github Actions](https://github.com/termux/termux-api/actions/workflows/github_action_build.yml?query=branch%3Amaster+event%3Apush)
page.

Signature keys of all offered builds are different. Before you switch the
installation source, you will have to uninstall the Termux application and
all currently installed plugins. Check https://github.com/termux/termux-app#Installation for more info.

## License

Released under the [GPLv3 license](http://www.gnu.org/licenses/gpl-3.0.en.html).

## How API calls are made through the termux-api helper binary

The [termux-api](https://github.com/termux/termux-api-package/blob/master/termux-api.c)
client binary in the `termux-api` package generates two linux anonymous namespace
sockets, and passes their address to the [TermuxApiReceiver broadcast receiver](https://github.com/termux/termux-api/blob/master/app/src/main/java/com/termux/api/TermuxApiReceiver.java)
as in:

```
/system/bin/am broadcast ${BROADCAST_RECEIVER} --es socket_input ${INPUT_SOCKET} --es socket_output ${OUTPUT_SOCKET}
```

The two sockets are used to forward stdin from `termux-api` to the relevant API
class and output from the API class to the stdout of `termux-api`.

## Client scripts

Client scripts which processes command line arguments before calling the
`termux-api` helper binary are available in the [termux-api package](https://github.com/termux/termux-api-package).

## Ideas

- Wifi network search and connect.
- Add extra permissions to the app to (un)install apps, stop processes etc.
