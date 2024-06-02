# Getting Started

HeliBoard project is based on Gradle and Android Gradle Plugin. To get started, you can install [Android Studio](https://developer.android.com/studio), and import project 'from Version Control / Git / Github' by providing this git repository [URL](https://github.com/Helium314/HeliBoard) (or git SSH [URL](git@github.com:Helium314/heliboard.git)).
Of course you can also use any other compatible IDE, or work with text editor and command line.

Once everything is up correctly, you're ready to go!

# Guidelines

HeliBoard is a complex application, when contributing, you must take a step back and make sure your contribution:
- **Is actually wanted**. Best check related open issues before you start working on a PR. Issues with "PR" and "contributor needed" labels are accepted, but still it would be good if you announced that you are working on it, so we can discuss how changes are best implemented.
  If there is no issue related to your intended contribution, it's a good idea to open a new one to avoid disappointment of the contribution not being accepted. For small changes or fixing obvious bugs this step is not necessary.
- **Is only about a single thing**. Mixing unrelated or semi-related contributions into a single PR is hard to review and can get messy.
- **Is finished or a draft**. When you keep changing the PR without reviewer's feedback, any attempt to review it is doomed and a waste of time. Better mark it as a draft in this case.
- **Has a proper description**. What your contribution does is usually less obvious to reviewers than for yourself. A good description helps _a lot_ for understanding what is going on, and for separating wanted from unintended changes in behavior. Therefore the changes should be as described, not more and not less.
- **Uses already in-place mechanism and take advantage of them**. In other terms, does not reinvent the wheel or uses shortcuts that could alter the consistency of the existing code. The contribution should only add as little complexity as necessary, the code is overly complicated already 😶.
- **Has a low footprint**. Some parts of the code are executed very frequently, and the keyboard should stay responsive even on older devices.
- **Does not bring any non-free code or proprietary binary blobs**. This also applies to code/binaries with unknown licenses. Make sure you do not introduce any closed-source library from Google.
  If your contribution contains code that is not your own, provide a link to the source.
- **Complies with the user privacy principle HeliBoard follows**.

(and please leave dependency upgrades to the maintainers, unless it's an actual security issue)
In addition to previous elements, HeliBoard must stick to [F-Droid inclusion guidelines](https://f-droid.org/docs/Inclusion_Policy/).

# Adding Layouts

See [layouts.md](layouts.md#adding-new-layouts--languages) for how to add new layouts to the app.

# Update Emojis

See make-emoji-keys tool [README](tools/make-emoji-keys/README.md).

# Translations
Translations can be added using [Weblate](https://translate.codeberg.org/projects/heliboard/). You will need an account to update translations and add languages. Add the language you want to translate to in Languages -> Manage translated languages in the top menu bar.
Updating translations in a PR will not be accepted, as it may cause conflicts with Weblate translations.
