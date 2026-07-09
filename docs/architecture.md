# Architecture

ZenConverter is native Android first. The app should remain useful without a
server, account, or network permission.

```mermaid
flowchart TD
    UI["Jetpack Compose UI"]
    VM["ViewModel + StateFlow"]
    Queue["Task Queue"]
    Service["Foreground Service"]
    Registry["Conversion Registry"]
    Media3["Media3 Hardware Engine"]
    FFmpeg["FFmpeg Compatibility Engine"]
    Files["SAF File Access"]
    Cache["Cache Fallback"]

    UI --> VM
    VM --> Queue
    Queue --> Service
    Service --> Registry
    Registry --> Media3
    Registry --> FFmpeg
    Service --> Files
    Files --> Cache
```

## Core Ideas

- UI is not the conversion engine. It only describes jobs and shows state.
- The registry chooses an engine based on input, output, and mode.
- Media3 handles common hardware-accelerated video work.
- FFmpeg handles compatibility tasks that Android APIs cannot cover.
- The app must stream or pass file descriptors whenever possible.
- Copying large files to cache is a fallback, not the default path.
