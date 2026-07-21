# ViH Messenger SDK

Drop-in messaging SDK for VIH Messenger — sign-in, the channel's enterprise list, and
real-time two-way chat as a self-contained module. This repository ships **both** platform
SDKs:

| Platform | Module | Distribution |
|----------|--------|--------------|
| Android  | [`vihChatBot/`](vihChatBot/) (Gradle) | JitPack |
| iOS      | [`Sources/VihChatBotSDK/`](Sources/VihChatBotSDK/) (Swift Package) | Swift Package Manager |

All backends target `https://api.platform.vihresearchlabs.ai/`. You supply a **channel
hashcode** (from the VIH team) and the user's **phone number**; the SDK handles the rest.

---

## Android (JitPack)

**1. Repositories** — in your root `settings.gradle(.kts)`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**2. Dependency** — in your module `build.gradle(.kts)` (replace `<tag>` with the released
git tag, e.g. `1.0.1`):

```groovy
implementation "com.github.ViH-Metaverse:ViH_SDK:<tag>"
```

> The exact coordinate is shown on the JitPack build page for this repo. The API base URL is
> compiled into the artifact (`https://api.platform.vihresearchlabs.ai/`) — there is no
> runtime override.

**3. Launch** — from any click listener:

```kotlin
import com.vihmessenger.vihchatbot.utils.FloatingButtonView

FloatingButtonView.startSdk(
    context  = this,
    phone    = "919876543210",          // country code + number, no '+'
    hashcode = "your-channel-hashcode"
)
```

## iOS (Swift Package Manager)

In Xcode: **File ▸ Add Package Dependencies…**, paste this repo's URL, and add the
**VihChatBotSDK** product to your app target.

```
https://github.com/ViH-Metaverse/ViH_SDK.git
```

Configure once at launch, then present the SDK:

```swift
import VihChatBotSDK

VihChatBotSDK.shared.configure(
    VihSDKConfig(
        apiBaseURL: URL(string: "https://api.platform.vihresearchlabs.ai/")!,
        hashcode: "your-channel-hashcode"   // same hashcode as Android
    )
)
```

---

## Release (maintainers)

Android artifacts are built by JitPack from a pushed git tag — no Sonatype account or signing
key required. To cut a release:

```bash
git tag 1.0.1 && git push origin 1.0.1
```

Then open the tag on [jitpack.io](https://jitpack.io) to trigger the build. Version numbers
are set in [`vihChatBot/build.gradle`](vihChatBot/build.gradle) (`stagingRelease` / `prodRelease`
publications) and the iOS package version tracks the git tag.

Need a channel hashcode or help integrating? Contact the VIH team.
