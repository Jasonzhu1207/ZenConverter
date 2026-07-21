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
    FFmpeg["FFmpeg Compatibility Engine"]
    Native["Native Image/PDF Engine"]
    Office["Office2PDF Engine"]
    Files["SAF File Access"]
    Cache["Cache Fallback"]

    UI --> VM
    VM --> Queue
    Queue --> Service
    Service --> Registry
    Registry --> FFmpeg
    Registry --> Native
    Registry --> Office
    Service --> Files
    Files --> Cache
```

## Core Ideas

- UI is not the conversion engine. It only describes jobs and shows state.
- The registry chooses an engine based on input, output, and mode.
- FFmpeg handles connected video/audio conversion and advanced processing.
- Native Android APIs handle bitmap image conversion, image/PDF paths, and
  metadata work where they are reliable enough.
- The experimental Office path is isolated behind its JNI renderer.
- The app must stream or pass file descriptors whenever possible.
- Copying large files to cache is a fallback, not the default path.
