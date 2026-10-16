## [1.9.1](https://github.com/hxreborn/PixelInjector/compare/v1.9.0...v1.9.1) (2026-10-16)

### Bug Fixes

* **app:** ask for root only when su was denied ([61ee5d6](https://github.com/hxreborn/PixelInjector/commit/61ee5d6dedbd9f93a48c355d1a972a76a7d83a31))
* **app:** ignore error lines from processes that died ([a213289](https://github.com/hxreborn/PixelInjector/commit/a213289c9a87ab6bb4f8cb1e83b69b1001ade1d4))
* **app:** label scrubbed log data as redacted ([c1ffced](https://github.com/hxreborn/PixelInjector/commit/c1ffcedc55ad4164da0b6043dfc4015b68064214))
* **app:** note that double tap to sleep needs root for the home app ([ce6f74a](https://github.com/hxreborn/PixelInjector/commit/ce6f74ab4514890a5a0ea8dfdd749531429db5b5))
* **app:** redact phone numbers and advertising IDs from reports ([d9efeeb](https://github.com/hxreborn/PixelInjector/commit/d9efeebf2ffee726cee4c24663966e794d88b471))
* **app:** redact phone numbers without a plus prefix ([e6b8ce9](https://github.com/hxreborn/PixelInjector/commit/e6b8ce96b4542cb78b898e34055e36346c9bbb1e))
* **app:** remove the update check and module log row summaries ([c1d765a](https://github.com/hxreborn/PixelInjector/commit/c1d765a0d128e553fd944e2144623bc5f994820e))
* **app:** report tweaks that log runtime errors as error ([bd73690](https://github.com/hxreborn/PixelInjector/commit/bd73690be9a2ed21cef9c282a036761243d1c583))
* **app:** scope the redaction disclosure to the module log ([c80f698](https://github.com/hxreborn/PixelInjector/commit/c80f698d321635b90209a27f88dbabff1aab6620))
* **app:** show a tweak's runtime error on its tile ([b8a18de](https://github.com/hxreborn/PixelInjector/commit/b8a18de37104383b594e31742aecfd741b186e87))
* **app:** show the update snackbar once ([dfc2801](https://github.com/hxreborn/PixelInjector/commit/dfc2801172da97f0d41595838ed9b867af2d7af2))
* **app:** time out a hot reload that never answers ([0fd14c7](https://github.com/hxreborn/PixelInjector/commit/0fd14c73b552641238f0f905df8d5e2b8c37c5e8))
* **hook:** log why a root sleep failed ([9ea6b4f](https://github.com/hxreborn/PixelInjector/commit/9ea6b4f125e018cdb48ef7e2b613889d866f66ac))
* **hook:** resolve the notifyUpdateLayoutParams overload ([c016d3e](https://github.com/hxreborn/PixelInjector/commit/c016d3eed213c0f59ebbfdf1af578915757da071)), closes [#21](https://github.com/hxreborn/PixelInjector/issues/21)

## [1.9.0](https://github.com/hxreborn/PixelInjector/compare/v1.8.0...v1.9.0) (2026-10-14)

### Features

* **app:** add a switch for automatic update checks ([657ba68](https://github.com/hxreborn/PixelInjector/commit/657ba68fd28bfcca86cfc1e15b07f75c9e0c10f0))
* **app:** add an option to hide unavailable tweaks ([6a635c3](https://github.com/hxreborn/PixelInjector/commit/6a635c3f2ab5d96621665b06052bd586fc33fa93))
* **app:** encrypt developer notes end to end ([af18fce](https://github.com/hxreborn/PixelInjector/commit/af18fce386eb9118ea46649c4325975e97973a59))
* **app:** group settings into updates support privacy and about ([018ef42](https://github.com/hxreborn/PixelInjector/commit/018ef42941cac5395cd6a82e99b5c44af8d6296b))
* **app:** hide package names in section headers unless enabled ([0839355](https://github.com/hxreborn/PixelInjector/commit/083935557c03e752444a76f10cf0ca252e2f45e0))
* **app:** list encryption specs with spec links in the report sheet ([5c08175](https://github.com/hxreborn/PixelInjector/commit/5c08175303d6a9f9a2fb1dd2d54a4a6e0ac76cc9))
* **app:** mark the release note line that fixes your report ([8a872d4](https://github.com/hxreborn/PixelInjector/commit/8a872d4148639f9a76ce3e3e45dbd39fe70f44cf))
* **app:** nest option rows inside their tweak card ([86eda61](https://github.com/hxreborn/PixelInjector/commit/86eda61b2cef26745a1806e68f059ab622609937))
* **app:** show app and tweak icons on dashboard and settings rows ([5559248](https://github.com/hxreborn/PixelInjector/commit/5559248b6eb9c7e39dff92f4be95d6397dfa2fbd))
* **app:** show app icon and label on hot reload rows ([23e5c1b](https://github.com/hxreborn/PixelInjector/commit/23e5c1b97d80895dbe938f8b79d5c06e476b3a67))
* **app:** show each report as status, latest note and issue link ([9e95a8e](https://github.com/hxreborn/PixelInjector/commit/9e95a8e4ea76447bfc589ddbf1b026a7f4c37d4a))
* **app:** stack screens with shared axis and scaled predictive back ([ca20e8c](https://github.com/hxreborn/PixelInjector/commit/ca20e8c57ae72acc44586e7405c931871a56a999))

### Bug Fixes

* **app:** fit settings and report sheets to landscape ([7deb381](https://github.com/hxreborn/PixelInjector/commit/7deb3812bea3f47b12402922191ee7f64ef5bab4))
* **app:** keep sheets below the status bar ([766c617](https://github.com/hxreborn/PixelInjector/commit/766c61746369bad5546ce3c963f0ef6d0022a29a))
* **app:** make sharing hook status opt-in ([77e8b3a](https://github.com/hxreborn/PixelInjector/commit/77e8b3a889f1e71cd7386f17fa03bb9a33208000))
* **app:** open the update from anywhere on the version row ([aed24d4](https://github.com/hxreborn/PixelInjector/commit/aed24d4acda00e9b6d49b1bcfb2bcd2c3263ee4f))
* **app:** rename Send a report to Report a bug ([9174801](https://github.com/hxreborn/PixelInjector/commit/9174801969da38b620c687a174842b708e8e12aa))
* **app:** rewrite report and update copy to say what each screen does ([58abd1b](https://github.com/hxreborn/PixelInjector/commit/58abd1b9f3d8d1591ab53f30a303b3835e54bbe9))
* **app:** scroll the targets list under a transparent action bar ([cc22240](https://github.com/hxreborn/PixelInjector/commit/cc22240589192a4f5ac80b050b7439b5d015f87b))
* **app:** tighten hierarchy of settings reports and update sheets ([8d08017](https://github.com/hxreborn/PixelInjector/commit/8d08017fbe498cae927ca91a8d7eee42ab0255c0))

### Refactor

* **app:** remove the module log button from the toolbar ([58b0aaf](https://github.com/hxreborn/PixelInjector/commit/58b0aaf014df2807a38ff35c63c3e65507e1bc7d))

## [1.8.0](https://github.com/hxreborn/PixelInjector/compare/v1.7.1...v1.8.0) (2026-10-13)

### Features

* **app:** send private reports and install updates in the app ([c111617](https://github.com/hxreborn/PixelInjector/commit/c111617df9a1be7bc57a859dacf354d8038f0bd7))
* **app:** show the conversation on each report ([18ccff4](https://github.com/hxreborn/PixelInjector/commit/18ccff43b7e7fd2ea78dd28ae85c57382d320302))

## [1.7.1](https://github.com/hxreborn/PixelInjector/compare/v1.7.0...v1.7.1) (2026-10-12)

### Bug Fixes

* **hook:** hook the View empty shade when the Compose one is missing ([d806dff](https://github.com/hxreborn/PixelInjector/commit/d806dff5e8360bb96eec89868511cdce98097315))

## [1.7.0](https://github.com/hxreborn/PixelInjector/compare/v1.6.0...v1.7.0) (2026-10-05)

### Features

* **app:** draw a scrollbar on the dashboard list ([b6b82f7](https://github.com/hxreborn/PixelInjector/commit/b6b82f7b5de2de47df312de04e2e48e8e2e5d84a))
* **hook:** add GH FastPass closing the approved 2FA dialog in the GitHub app ([b936f16](https://github.com/hxreborn/PixelInjector/commit/b936f16385b5dadef6466fd07c2d73581337b25e))

### Bug Fixes

* **app:** register the prefs listener before sending the first snapshot ([3801d51](https://github.com/hxreborn/PixelInjector/commit/3801d51af747b246df5eed9e4faf4a45680970ee))
* **hook:** catch failures inside the posted confirm retries ([043d61f](https://github.com/hxreborn/PixelInjector/commit/043d61f797d3c0d1e0d932e104511821c710dbc6))
* **hook:** drop the DexKit bridge reference before closing it ([d1e9d65](https://github.com/hxreborn/PixelInjector/commit/d1e9d65303363e637780194ee6fe6758e4507cd2))
* **hook:** keep the unlock sound while Biometric Bypass is off ([ef4d909](https://github.com/hxreborn/PixelInjector/commit/ef4d909d304dfa36269f21b40760552aa0d261c2))

### Refactor

* **app:** collapse single line lambdas in the prefs sync and log parser ([82f2c45](https://github.com/hxreborn/PixelInjector/commit/82f2c450e7e13a5961fdd85d9fe3bf865eeb2b8b))
* **hook:** name every bindings value and pending state explicitly ([3dfe6d9](https://github.com/hxreborn/PixelInjector/commit/3dfe6d9efc4a335785af700869c48187743b2d32))
* **util:** catch only missing members in the reflection helpers ([c28c464](https://github.com/hxreborn/PixelInjector/commit/c28c4645813f2c5872bff4ae06b193e89d460eae))

## [1.6.0](https://github.com/hxreborn/PixelInjector/compare/v1.5.0...v1.6.0) (2026-10-05)

### Features

* **app:** exit the toolbar on scroll and morph buttons, toggles, checkboxes and menu items ([c76340b](https://github.com/hxreborn/PixelInjector/commit/c76340b50f87ca149abe82f934465adffae6123d))

### Bug Fixes

* **app:** shrink the collapsed dashboard title and right align link tile chevrons ([2710bc0](https://github.com/hxreborn/PixelInjector/commit/2710bc03673334f190da1240e0eb8b1c26309d4e))

### Refactor

* **app:** replace the scrollbar gist with the material3 scrollbar modifier ([ab0a501](https://github.com/hxreborn/PixelInjector/commit/ab0a501e327edad124ab5f589e6cdaa74d2cfe95))

## [1.5.0](https://github.com/hxreborn/PixelInjector/compare/v1.4.2...v1.5.0) (2026-10-04)

### Features

* **app:** list every hooked process in the hot reload sheet ([c603a2b](https://github.com/hxreborn/PixelInjector/commit/c603a2b665dca20a698ab487a6c14133ca9f96d7))

### Bug Fixes

* **app:** decode the loaded version chip as major.minor.patch ([54c9f9f](https://github.com/hxreborn/PixelInjector/commit/54c9f9fc5485cbd9d0f2fd347045021b37d63160))

## [1.4.2](https://github.com/hxreborn/PixelInjector/compare/v1.4.1...v1.4.2) (2026-10-02)

### Bug Fixes

* **module:** accept hot reload in processes without a target ([1302ed4](https://github.com/hxreborn/PixelInjector/commit/1302ed4f4f0f7f8f453e34c07f7971cbbb78374d))

## [1.4.1](https://github.com/hxreborn/PixelInjector/compare/v1.4.0...v1.4.1) (2026-10-02)

### Bug Fixes

* **hook:** lock the phone before double tap to sleep turns the screen off ([aa1725b](https://github.com/hxreborn/PixelInjector/commit/aa1725b3274d8d5acf87b72dda96e2fe924f4f03))

## [1.4.0](https://github.com/hxreborn/PixelInjector/compare/v1.3.0...v1.4.0) (2026-10-01)

### Features

* **hook:** add Empty Shade to replace the empty shade text and icon ([767f39f](https://github.com/hxreborn/PixelInjector/commit/767f39fdfb45ac05f8755cc1bc040d0b93151264))

## [1.3.0](https://github.com/hxreborn/PixelInjector/compare/v1.2.0...v1.3.0) (2026-09-28)

### Features

* **hook:** add a skip or only-selected app filter to PillShot ([d85eed1](https://github.com/hxreborn/PixelInjector/commit/d85eed197297c1babb6bc25dd6a75e185d397f26)), closes [#10](https://github.com/hxreborn/PixelInjector/issues/10)

### Refactor

* **app:** put Copy after Close in the hot reload sheet ([18eb7db](https://github.com/hxreborn/PixelInjector/commit/18eb7db883343567e88d3570ff92708522e8a967))

## [1.2.0](https://github.com/hxreborn/PixelInjector/compare/v1.1.0...v1.2.0) (2026-09-24)

### Features

* **hook:** add KDE Connect Quiet to block the Wi-Fi multicast lock ([cac024b](https://github.com/hxreborn/PixelInjector/commit/cac024b468aab1d3e873c4bd55699a0cb9cba7dd))

## [1.1.0](https://github.com/hxreborn/PixelInjector/compare/v1.0.2...v1.1.0) (2026-09-07)

### Features

* **app:** add a confirm sound picker for Biometric Bypass ([c51ee18](https://github.com/hxreborn/PixelInjector/commit/c51ee188b01bd64b65e5deb7b4f42207a73b4ce3))

### Bug Fixes

* **hook:** drop the click sound when BiometricBypass taps confirm ([2928fd0](https://github.com/hxreborn/PixelInjector/commit/2928fd05ba141d6142a8463bd64a52ca6d653155))
* **hook:** press the power key instead of goToSleep in SleepSignal ([3dd59c2](https://github.com/hxreborn/PixelInjector/commit/3dd59c2766155b4853a0638f4f8d95e3b72e444f))

## [1.0.2](https://github.com/hxreborn/PixelInjector/compare/v1.0.1...v1.0.2) (2026-09-06)

### Bug Fixes

* **hook:** hook ImageCaptureImpl in PillShot for Android 14 and 15 ([8495322](https://github.com/hxreborn/PixelInjector/commit/8495322b8744c66b73579914a00012eca6909f73))

## [1.0.1](https://github.com/hxreborn/PixelInjector/compare/v1.0.0...v1.0.1) (2026-09-06)

### Bug Fixes

* **app:** open the dashboard at the top on cold start ([e41f73a](https://github.com/hxreborn/PixelInjector/commit/e41f73a62a00918357046d7370d22a70e194881d))

## 1.0.0 (2026-09-06)

### Features

* **app:** add companion app ([96cce4a](https://github.com/hxreborn/PixelInjector/commit/96cce4ad24012300b6669fdd4cbc13cc9c8064ff))
* **hook:** add Dialer tweak ([5aa5d35](https://github.com/hxreborn/PixelInjector/commit/5aa5d357c4c89a74b7778679a38385a4580d90ef))
* **hook:** add Files tweak ([c842dbd](https://github.com/hxreborn/PixelInjector/commit/c842dbda0a0868a0181163f0e53c6af9cc1e8101))
* **hook:** add Gboard tweak ([e7e49a3](https://github.com/hxreborn/PixelInjector/commit/e7e49a3dc154ae3fd4a9d4f7157b9181d80cd573))
* **hook:** add launcher tweaks ([18e1a9d](https://github.com/hxreborn/PixelInjector/commit/18e1a9ddddcbd1d24ba8ca460905e2ce6ccee0d5))
* **hook:** add module entry with tweak registry and hot reload ([98ee2b7](https://github.com/hxreborn/PixelInjector/commit/98ee2b71ed3138aa7cae34dc086c7f767149c103))
* **hook:** add System UI tweaks ([be103fb](https://github.com/hxreborn/PixelInjector/commit/be103fb020d9ecaa028c4691613bae33d9bde890))
* **hook:** add system_server tweaks ([6a581e3](https://github.com/hxreborn/PixelInjector/commit/6a581e33f3181ece51c3c324971309e3dcf933ea))
