# Draft Reply To Issue 18

Draft only: post after the migration is merged and these files are visible in
the default branch. No comment has been posted by this change.

> Thanks for offering to help! You were right: our translations used to be
> embedded in Kotlin code, so there was no separate translation file to find.
>
> We have moved the text into standard Android string resources. The English
> source is `app/src/main/res/values/strings.xml`, and the guide is
> `docs/translating.md`. For French, add
> `app/src/main/res/values-fr/strings.xml` and register `fr` in
> `app/src/main/res/xml/locales_config.xml`. No Kotlin changes are needed.
>
> Partial translations are welcome; missing entries fall back to English.
> You do not need Android Studio to contribute, and our PR check validates the
> files. We'd love your help with French and are happy to answer context or
> wording questions. Thank you!
