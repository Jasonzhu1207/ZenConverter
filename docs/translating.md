# Translating ZenConverter

You can contribute translations without knowing Kotlin or installing Android
Studio. Translations ship inside the app and work offline.

## Files and contribution steps

The complete English source is `app/src/main/res/values/strings.xml`.
Simplified Chinese uses `values-b+zh+Hans/strings.xml` and Traditional Chinese
uses `values-b+zh+Hant/strings.xml` under the same resource directory.

1. Fork the repository. For French, create
   `app/src/main/res/values-fr/strings.xml` with a `<resources>` root.
2. Copy and translate the English entries you want to work on. Preserve their
   `name` identifiers. Do not copy entries marked `translatable="false"`.
3. Add `<locale android:name="fr" />` to
   `app/src/main/res/xml/locales_config.xml`. This one declaration supplies both
   the app picker and Android's per-app language settings; no Kotlin enum needs
   editing. Language names are displayed in their own language.
4. If Python 3.8 or newer is available, run
   `python scripts/check_translations.py`. No SDK or third-party Python package is
   required. Otherwise, open a PR and read its **Translation resources** check.
5. Describe your language, completed sections, and any context questions in the
   PR. Screenshots or native-speaker review are welcome but not required to start.

Partial translations are welcome. Omit unfinished entries rather than copying
English to inflate completion: missing entries fall back to English. The checker
reports missing entries without failing. Maintainers should describe partial
support honestly when accepting it. This migration does not itself ship French.

Use BCP 47 tags in the declaration. Examples: `fr` with `values-fr`, `pt-BR` with
`values-pt-rBR`, or `sr-Latn` with `values-b+sr+Latn`. Each language has one primary
translation directory. Do not add empty language placeholders or production
pseudo-locales.

## Arguments, plurals, and escaping

- Preserve numbered arguments such as `%1$s`, `%2$s`, `%1$d`, and `%1$.1f`.
  You may reorder them, but do not change their indexes or types. `%s` inserts a
  supplied value, `%d` an integer, and `%f` a decimal. `%%` is a literal percent
  in a formatted string. Translate whole sentences, not fragments of word order.
- Every `<plurals>` needs `other`. Use whichever Android plural categories your
  language requires: `zero`, `one`, `two`, `few`, `many`, `other`. They need not
  match the English categories. Preserve arguments from the corresponding
  English form, or from `other` when English lacks that category.
- Android strings use `\'` for an apostrophe, `\"` for a double quote, and `\n`
  for a line break. XML uses `&amp;` and `&lt;`. Keep intentional leading/trailing
  whitespace inside Android double-quoted values. Do not use undefined HTML
  entities. CI checks XML structure, identifiers, arguments, and plural forms.
- Keep technical names such as FFmpeg, NCNN, RIFE, MP4, and SHA-256 unchanged;
  translate their surrounding explanations. Filenames and external diagnostic
  details remain as provided, with a translated app-authored explanation.

## Context and terminology

| Prefix or term | Meaning |
| --- | --- |
| `ui_` | General interface, settings, actions, and descriptions |
| `text_` | Feature-specific option labels and explanations |
| `message_`, `task_` | Errors, validation, and task status |
| `count_` | Quantity messages; translated using plural resources |
| `notification_` | Android notification actions and channel settings |
| `a11y_` | Screen-reader descriptions; describe the action, not the icon |
| `font_`, `contact_sheet_` | Optional font downloads and text drawn into video overview images |
| `privacy_`, `help_` | In-app privacy policy and capability guide |
| Compatibility | Local FFmpeg processing, not a remote service |
| Contact sheet | One overview image containing sampled video frames |
| Follow system | No explicit app-language override |

Preserve privacy and format limitations. AI upscale and visually lossless
compression are not mathematically lossless. Do not make stronger promises than
the English source. Include resource keys in questions about unclear context.

## Maintainer review

Check long text, large fonts, narrow buttons, notifications, and accessibility
on a physical device. See `docs/testing-localization.md`. Neither an emulator nor
an NDK installation is required to submit a translation.

New visible application text belongs in resources. Keep `LocalizedText` or a
domain error enum in task state and resolve at presentation time. `UiText` is a
resource adapter, not a language table. Route by `TargetId` or its stable key,
never translated labels. Do not introduce JSON language files, generated-resource
edits, or English-message lookup tables.
