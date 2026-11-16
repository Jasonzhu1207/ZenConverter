# Local-first audio and tools studio

## Status

Accepted

## Context

The conversion engine already exposed a large FFmpeg filter surface, but most of
it was reachable only from the per-file options panel. The roadmap calls for
AudioLab-style editing: a waveform editor, recorder, mixer, tag editor,
ringtone maker, text to speech, transcription, stem separation, recipes and
system tools. Those need their own screens, their own persisted state and their
own place in navigation, while keeping ZenConverter's promise that nothing is
uploaded and no account is required.

Two constraints shaped the design. First, user-facing text must live in
`strings.xml` so the existing translation workflow
(`scripts/check_translations.py`) keeps working. Second, logic that decides what
to do — bitrate maths, effect chains, recipe parsing, transcript cutting — should
be plain Kotlin so it can be unit tested on the JVM without an Android device.

## Decision

Add a studio layer with three parts.

1. A pure-Kotlin core in `org.zenconverter.app.audio`, `recipe`, `studio` and
   `system`: `AudioEffects`, `AudioEffectFilters`, `WaveformAnalyzer`,
   `AudioAnalysis`, `TranscriptEditing`, `SmartPresets`, `BitrateCalculator`,
   `SmartSuggestions`, `ConversionRecipe`/`RecipeLibrary`, `ConversionHistory`
   and `PluginPreset`. These hold no Android or FFmpeg types, so they are
   covered by JVM unit tests under `app/src/test`.
2. A thin device layer: `AudioStudioEngine` runs FFmpeg and MediaCodec-free
   native work, `AudioRecorder` wraps `AudioRecord`, `OfflineTextToSpeech` wraps
   the platform TTS engine, `RingtoneMaker` wraps `RingtoneManager`,
   `AudioTagEditor` wraps the platform media metadata retriever, `StudioOutput`
   writes results through MediaStore, `LocalTransferServer` serves files over
   the local network and `FolderWatcher` polls a Storage Access Framework tree.
3. Compose screens under `ui/studio`, reached from a single studio hub that is
   linked from Settings. Each screen is a `SettingsPage` and reads strings
   through `UiText`.

Effects are modelled once in `AudioEffects` and translated to FFmpeg filters by
`AudioEffectFilters`. The conversion service appends those filters to the same
`AudioAdvancedOptions` chain it already built, so studio edits and the normal
conversion path share one implementation instead of diverging.

On-device models (Whisper, Demucs/Spleeter-class stem separation) are described
by `OfflineModelSpec` and downloaded by `OfflineModelStore` with a SHA-256
check. The screens expose the workflow, and the offline engine slot stays behind
an interface so a build without the native inference library still compiles and
runs.

## Consequences

The studio adds screens, string resources and a small amount of persisted state
(history, custom recipes, plugin presets, accessibility and guard settings), all
in a private `SharedPreferences` file. Because the decision logic is pure Kotlin,
it is unit tested directly; the Android-dependent pieces are kept thin. Every new
string is translatable, and languages that have not caught up fall back to
English, which the translation checker already accepts.

Hardware-accelerated encoding is deliberately not wired: the bundled FFmpeg
build ships no MediaCodec encoders, so a `*_mediacodec` codec name would fail at
runtime. The existing FFmpeg path remains the single, honest encoder.
