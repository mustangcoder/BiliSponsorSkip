# Recommend Ad Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Filter structurally marked ads from Bilibili 9.15.0 Story feeds and video-detail related recommendations behind two independent switches.

**Architecture:** Pure Kotlin policies decide whether Story and detail-related entries are ads. Two narrowly scoped Xposed hooks apply those policies before Story items reach the pager and before detail-related `CM` entries become UI components; settings stay independent and default on.

**Tech Stack:** Kotlin, Android API 24+, Xposed API 82, JUnit 4, LSPatch 1.2-487, Bilibili 9.15.0.

**Spec:** `docs/superpowers/specs/2026-10-20-recommend-ad-filter-design.md`

## Global Constraints

- Only structurally marked ads are filtered; visible words such as “广告” or “推广” are never sufficient.
- Story filtering uses `StoryDetail.isAd()`, `isNaturalAd()`, `adInfo`, and the exact ad `goto` values `vertical_ad_av`, `vertical_ad_live`, and `vertical_ad_picture`.
- Detail-related filtering removes only `RelateCardType.CM`.
- Unknown/missing fields and reflection failures preserve the item.
- Hooks are installed once and read current settings at each filtering boundary.
- Both switches default to enabled and remain independent of the existing home-feed switch.
- Preserve list order, pagination state and the host's existing empty-list behavior.
- Build the final patched client with `injectDex=false` and signature-bypass level 2.
- Do not commit or push without explicit user authorization.

## Review Focus

- A normal video whose title contains “广告” must remain; Task 1 pins this through signal-only tests.
- A `StoryDetail` with `adInfo` but an unexpected `goto` must still be removed; Task 1 tests independent signals.
- Repeated calls to `StoryFeedResponse.getItems()` must be idempotent and must not mutate the host list; Task 2 tests this policy boundary.
- A detail entry whose type cannot be read must continue through the original component method; Task 3 tests fail-open behavior.
- Disabling one switch must not disable or enable the other filter or the existing home-feed filter; Task 4 tests all combinations.

---

## File Structure

- `StoryAdFilter.kt`: pure Story signal model, predicates and ordered filtering.
- `StoryAdFilterHook.kt`: Story response reflection and Xposed return-value hook.
- `DetailRelateAdFilter.kt`: pure related-card type policy.
- `DetailRelateAdFilterHook.kt`: signature-based component-method discovery and pre-call suppression.
- Existing settings files carry the two independent booleans.
- `XposedInit.kt` installs both hooks without making either failure fatal to other module features.

### Task 1: Pure Story and Detail Filtering Policies

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/StoryAdFilter.kt`
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/DetailRelateAdFilter.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/StoryAdFilterTest.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/DetailRelateAdFilterTest.kt`

**Interfaces:**
- Produces: `StoryAdSignals(isAd, isNaturalAd, hasAdInfo, goto)`.
- Produces: `isMarkedStoryAd(signals: StoryAdSignals): Boolean`.
- Produces: `filterMarkedStoryAds(items, signals): List<T>`.
- Produces: `isDetailRelateAd(cardTypeName: String?): Boolean`.

- [ ] **Step 1: Write failing Story policy tests**

Assert each explicit signal independently matches, known ad goto values match, an ordinary item does not match, visible title text is not part of the API, mixed lists preserve order, consecutive ads are removed, all-ad lists become empty, and the input list is unchanged.

- [ ] **Step 2: Run Story tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.StoryAdFilterTest`

Expected: compilation failure because the policy types do not exist.

- [ ] **Step 3: Implement the minimal Story policy**

Recognize `vertical_ad_av`, `vertical_ad_live`, and `vertical_ad_picture` plus the boolean/object signals. Return a new filtered list and never mutate the input.

- [ ] **Step 4: Write failing detail policy tests**

Assert exact enum name `CM` matches; AV, BANGUMI, LIVE, COURSE, unknown, blank and null do not match.

- [ ] **Step 5: Run detail tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.DetailRelateAdFilterTest`

Expected: compilation failure because `isDetailRelateAd` does not exist.

- [ ] **Step 6: Implement the minimal detail policy**

Use exact enum identity/name, not localized text, title, description or URI keywords.

- [ ] **Step 7: Run both focused suites**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.StoryAdFilterTest' --tests 'com.retrsoft.bilisponsorskip.DetailRelateAdFilterTest'`

Expected: PASS.

- [ ] **Step 8: Review checkpoint**

Mentally mutate each signal and enum branch; at least one test must fail for every accidental broadening or narrowing. Do not commit without explicit permission.

### Task 2: Story Response Hook

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/StoryAdFilterHook.kt`
- Create or extend: `app/src/main/java/com/retrsoft/bilisponsorskip/Reflect.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/StoryAdSignalReaderTest.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/StoryAdHookPolicyTest.kt`

**Interfaces:**
- Consumes: Task 1 Story policy.
- Produces: `StoryAdFilterHook(settings, classLoader).install()`.
- Produces: `readStoryAdSignals(item: Any, reader: ReflectiveValueReader): StoryAdSignals`.
- Produces: `effectiveSkipStoryFeedAds(moduleEnabled, switchEnabled): Boolean`.

- [ ] **Step 1: Write failing signal-reader tests**

Use test fixtures with public methods and fallback fields. Assert public methods win, `adInfo` presence is enough, getter/field exceptions produce non-ad defaults instead of deletion, and no title/text method is queried.

- [ ] **Step 2: Run signal-reader tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.StoryAdSignalReaderTest`

Expected: compilation failure for the missing reader.

- [ ] **Step 3: Implement the reflective signal reader**

Read `isAd()`, `isNaturalAd()`, `getAdInfo()`, and `getGoto()` first. Fall back only to verified 9.15.0 field names. Catch reflection failures per signal and preserve the entry.

- [ ] **Step 4: Write failing hook-policy tests**

Assert filtering only when both module and Story switch are enabled, repeated filtering is idempotent, null/non-list results are untouched, empty result remains a valid list, and the original returned list is not mutated.

- [ ] **Step 5: Run hook-policy tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.StoryAdHookPolicyTest`

Expected: FAIL because the effective policy/hook boundary is absent.

- [ ] **Step 6: Implement the Story hook**

Find `com.bilibili.video.story.api.StoryFeedResponse`, hook zero-argument `getItems`, and replace only list results with the filtered copy. Log one installation line and one aggregate removal line per changed result.

- [ ] **Step 7: Run focused tests and compile**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.StoryAd*Test' assembleDebug`

Expected: PASS and successful Debug APK build.

- [ ] **Step 8: Review checkpoint**

Verify the hook is limited to Story responses, performs no network call or page action, and leaves current host objects untouched. Do not commit without explicit permission.

### Task 3: Detail Related-Component Hook

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/DetailRelateAdFilterHook.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/DetailRelateHookSelectorTest.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/DetailRelateHookPolicyTest.kt`

**Interfaces:**
- Consumes: Task 1 detail policy.
- Produces: `DetailRelateAdFilterHook(settings, classLoader).install()`.
- Produces: `selectDetailComponentMethods(methods, itemClassName, resultClassName): List<Method>`.
- Produces: `readDetailRelateTypeName(item: Any): String?`.
- Produces: `effectiveHideDetailRelateAds(moduleEnabled, switchEnabled): Boolean`.

- [ ] **Step 1: Write failing method-selector tests**

Build fixture methods and assert selection requires exactly one `B0` parameter and `RunningUIComponent` return type; reject overloads, void/other returns and similarly named methods. Do not assert the obfuscated method name `d`.

- [ ] **Step 2: Run selector tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.DetailRelateHookSelectorTest`

Expected: compilation failure for the missing selector.

- [ ] **Step 3: Implement signature-based selection and type reading**

Locate the parameter's declared enum field whose type is the verified `RelateCardType`; read its enum name. If the field is absent, ambiguous or inaccessible, return null and preserve the component.

- [ ] **Step 4: Write failing hook-policy tests**

Assert CM returns the suppression decision only when module and detail switch are enabled; all other/unknown types continue; a null item continues; selector zero/multiple-match states produce installation failure rather than hooking an arbitrary method.

- [ ] **Step 5: Run policy tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.DetailRelateHookPolicyTest`

Expected: FAIL because hook policy does not exist.

- [ ] **Step 6: Implement the component hook**

Find stable `DetailRelateService`, `B0`, `RelateCardType`, and `RunningUIComponent` classes. Hook the unique selected method before execution; set result to null only for exact CM. Log installation and aggregate suppression counts without ad metadata.

- [ ] **Step 7: Run focused tests and compile**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.DetailRelate*Test' assembleDebug`

Expected: PASS and successful Debug build.

- [ ] **Step 8: Review checkpoint**

Trace the host's component loop and confirm a null component is skipped without reserving layout space. Confirm initial and paginated `B0` entries share this conversion method. Do not commit without explicit permission.

### Task 4: Independent Settings and Hook Installation

**Files:**
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/Settings.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsActivity.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsSyncReceiver.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BiliSettingsEntryInjector.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/XposedInit.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/RecommendAdSettingsTest.kt`

**Interfaces:**
- Consumes: Tasks 2–3 hook installers.
- Produces: `SettingsSnapshot.skipStoryFeedAds: Boolean = true`.
- Produces: `SettingsSnapshot.hideDetailRelateAds: Boolean = true`.
- Produces: `KEY_SKIP_STORY_FEED_ADS` and `KEY_HIDE_DETAIL_RELATE_ADS` in every settings transport.

- [ ] **Step 1: Write failing settings-contract tests**

Assert both defaults true; exact persisted keys; membership in `BOOLEAN_SETTING_KEYS`; round-trip through snapshot reader and `settingsBundle`; embedded and standalone save paths keep both values; all four combinations remain independent of `hideHomeFeedAds`.

- [ ] **Step 2: Run settings tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.RecommendAdSettingsTest`

Expected: compilation failure for missing fields and keys.

- [ ] **Step 3: Add the two settings end to end**

Add switches with the approved Chinese titles and summaries. Ensure `SettingsActivity.settingsBundle()` includes all existing and new filtering keys; fix any discovered omission in that same serialization boundary rather than adding another partial copy.

- [ ] **Step 4: Install both hooks independently**

In `XposedInit`, wrap each installer in its own `runCatching`; one missing hook must not prevent player, SponsorBlock or the other recommendation filter from loading.

- [ ] **Step 5: Run focused and full unit tests**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.RecommendAdSettingsTest && ./gradlew testDebugUnitTest`

Expected: PASS.

- [ ] **Step 6: Review checkpoint**

Trace module preferences, mirror preferences, embedded UI, standalone UI, settings Bundle and both runtime guards. Do not commit without explicit permission.

### Task 5: Documentation, Patched APK and Runtime Verification

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: tests only if runtime evidence exposes a missed boundary.
- Output only: `/Users/lzm/workspace/github/block_bili_ad/BiliSponsorSkip/output/9.15.0/bili-9.15.0-487-lspatched.apk`

**Interfaces:**
- Consumes: completed Story/detail hooks and settings.
- Produces: verified Bilibili 9.15.0 patched APK and user-facing documentation.

- [ ] **Step 1: Update README and changelog**

Document both independent switches, structural matching rules, 9.15.0 compatibility and the limitation that unmarked soft promotions are not filtered.

- [ ] **Step 2: Run full source verification**

Run: `./gradlew testDebugUnitTest assembleDebug`

Run: `git diff --check`

Expected: all commands succeed.

- [ ] **Step 3: Build the versioned module and LSPatch APK**

Use the next module version/code, embed the Debug module into the original 9.15.0 APK with LSPatch 1.2-487, signature-bypass level 2, and no `--injectdex`. Replace only the known final output path.

- [ ] **Step 4: Verify the immutable artifact**

Run ZIP integrity, apksig verification, embedded module version extraction, LSPatch config inspection and SHA-256 hashing. Expected: V2 signature valid, zero verification errors/warnings, `injectDex=false`, `sigBypassLevel=2`.

- [ ] **Step 5: Run Story regression in BlueStacks**

Exercise initial and subsequent Story pages with each switch state. Use module logs to prove marked items were removed before pager consumption; confirm no flash, blank page, index error, ANR or process death.

- [ ] **Step 6: Run detail-related regression in BlueStacks**

Check first-screen recommendations and load-more results with each switch state. Confirm CM cards disappear without gaps, ordinary cards remain clickable, and repeated navigation is stable.

- [ ] **Step 7: Run existing feature regression**

Verify home-feed ads, detail banner, splash ads, bottom tabs, game-center card and SponsorBlock still behave according to their switches.

- [ ] **Step 8: Launch one read-only bypass/regression review**

Provide the approved spec, original 9.15.0 evidence and current diff. Address only confirmed Story/detail filtering bypasses or regressions, then rerun affected tests and artifact verification.

- [ ] **Step 9: Commit gate**

Stop and report changed files, test/build/runtime evidence, APK path/hash and remaining version-specific uncertainty. Commit and push only after explicit user authorization.
