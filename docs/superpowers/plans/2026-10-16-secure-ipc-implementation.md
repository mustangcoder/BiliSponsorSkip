# Secure Android IPC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace unauthenticated settings/statistics broadcasts with a UID-authenticated, on-demand AIDL bridge that preserves LSPosed and LSPatch behavior without persistent background work.

**Architecture:** The installed module exposes one short-lived bound Service. Every AIDL transaction authenticates the Binder calling UID against the four Bilibili packages; LSPosed clients pull/push through the Service, while LSPatch without an installed module stays entirely in-process. Local skip statistics remain cumulative and are flushed at most once per 60-second window into four fixed module-side slots.

**Tech Stack:** Kotlin, Android API 24+, AIDL, Parcelable, Xposed/LSPosed, LSPatch, JUnit 4, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-10-16-secure-ipc-and-release-design.md`

## Global Constraints

- Android 7.0（API 24）及以上。
- Support LSPosed standalone-module and LSPatch/embedded modes.
- No foreground service, polling, AlarmManager, WorkManager, or persistent Binder connection.
- Settings refresh on initialization, foreground return, and settings-entry activation; de-duplicate in-flight calls and enforce a minimum interval.
- Statistics flush at most once every 60 seconds and retain cumulative snapshots across process death.
- Reject root, system, isolated, unmapped, non-target, and mixed-whitelist UIDs.
- Do not trust caller-supplied package names, paths, or source extras as identity.
- Do not commit without explicit user authorization.

## Review Focus

- A UID mapped to one allowed package plus one unrelated shared-UID package must be rejected; Task 1 tests this.
- A Binder handle passed from an allowed process to a disallowed process must not bypass checks; Task 2 tests per-transaction authorization.
- LSPatch with no installed module must stop retrying after backoff and keep local mirror settings; Task 3 tests this.
- A process death before the delayed statistics flush must preserve cumulative data for the next startup; Task 5 tests this.
- Old embedded code talking to the new module Service must fail version negotiation without clearing valid settings; Task 3 tests this.

---

## File Structure

- `app/src/main/aidl/com/retrsoft/bilisponsorskip/ISecureModuleBridge.aidl`: versioned cross-process operations.
- `app/src/main/aidl/com/retrsoft/bilisponsorskip/BridgeSettings.aidl`: fixed settings Parcelable declaration.
- `app/src/main/aidl/com/retrsoft/bilisponsorskip/BridgeStatsSnapshot.aidl`: fixed statistics Parcelable declaration.
- `app/src/main/java/com/retrsoft/bilisponsorskip/SecureBridgeModels.kt`: Parcelable models, protocol version, field validation and conversion.
- `app/src/main/java/com/retrsoft/bilisponsorskip/BridgeCallerAuthorizer.kt`: pure authorization policy and Android UID/package resolver.
- `app/src/main/java/com/retrsoft/bilisponsorskip/SecureModuleBridgeService.kt`: authenticated module-side Binder implementation.
- `app/src/main/java/com/retrsoft/bilisponsorskip/SecureModuleBridgeClient.kt`: short-lived bind/transact/unbind client with backoff and in-flight de-duplication.
- `app/src/main/java/com/retrsoft/bilisponsorskip/ModuleStatsRepository.kt`: fixed-slot module-side aggregate statistics.
- Existing settings, controller, lifecycle, manifest and tests are modified only to consume those units.

### Task 1: Protocol Models and Caller Authorization

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/aidl/com/retrsoft/bilisponsorskip/ISecureModuleBridge.aidl`
- Create: `app/src/main/aidl/com/retrsoft/bilisponsorskip/BridgeSettings.aidl`
- Create: `app/src/main/aidl/com/retrsoft/bilisponsorskip/BridgeStatsSnapshot.aidl`
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/SecureBridgeModels.kt`
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/BridgeCallerAuthorizer.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/BridgeCallerAuthorizerTest.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/SecureBridgeModelsTest.kt`

**Interfaces:**
- Produces: `BRIDGE_PROTOCOL_VERSION: Int = 1`.
- Produces: `authorizeBridgeCaller(uid: Int, packages: Set<String>, isIsolated: Boolean): BridgeCallerAuthorization`.
- Produces: `BridgeSettings.fromSnapshot(snapshot)` and `BridgeSettings.toSnapshot(defaults)`.
- Produces: `BridgeStatsSnapshot(declaredPackage, generation, count, savedMs)`.

- [ ] **Step 1: Write failing caller-authorization tests**

Test allowed single-package UIDs and rejection of UID below `Process.FIRST_APPLICATION_UID`, isolated UID, empty package set, non-target package, and mixed target/non-target shared UID. Assert authorization returns the single derived package rather than trusting a declared package.

- [ ] **Step 2: Run authorization tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.BridgeCallerAuthorizerTest`

Expected: compilation failure because the policy and result types do not exist.

- [ ] **Step 3: Implement the pure authorization policy**

Add `BridgeCallerAuthorization.Allowed(packageNames: Set<String>)` and `Denied(reason: String)`. Accept only non-isolated application UIDs whose complete nonempty package set is a subset of `SettingsContract.TARGET_PACKAGES`.

- [ ] **Step 4: Write failing protocol-model validation tests**

Test protocol mismatch, unknown category modes, username/userId maximum lengths, negative statistics, overlong generation, and a caller-declared package not present in the authorized package set. Pin exact limits in the model companion object.

- [ ] **Step 5: Run model tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.SecureBridgeModelsTest`

Expected: compilation failure because Parcelable models do not exist.

- [ ] **Step 6: Add Parcelable support, AIDL declarations and minimal model conversion**

Apply the `kotlin-parcelize` plugin. Keep AIDL arguments field-fixed; do not expose arbitrary Bundle parameters. Define `ISecureModuleBridge.getProtocolVersion()`, `getSettings(loadedModulePath)`, `putSettings(settings, loadedModulePath)`, and `mergeLocalStats(snapshot)`.

- [ ] **Step 7: Run focused model and authorization tests**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.*Bridge*Test'`

Expected: PASS.

- [ ] **Step 8: Review checkpoint**

Inspect the diff for unbounded strings, caller-controlled identity fields, generic Bundle use and API calls above minSdk. Do not commit without explicit permission.

### Task 2: Module-Side Stores and Authenticated Service

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/SecureModuleBridgeService.kt`
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/ModuleSettingsStore.kt`
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/ModuleStatsRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/SecureModuleBridgeServicePolicyTest.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/ModuleStatsRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 AIDL, models and `authorizeBridgeCaller()`.
- Produces: `SecureModuleBridgeService` with per-transaction `Binder.getCallingUid()` authorization.
- Produces: `ModuleSettingsStore.read()` and `write(settings)`.
- Produces: `ModuleStatsRepository.merge(callerPackage, snapshot)` with one fixed slot per target package.

- [ ] **Step 1: Write failing service-policy tests**

Extract transaction policy behind a testable `SecureBridgeTransactionPolicy`. Assert every operation re-evaluates caller authorization, ignores spoofed package identity, rejects version mismatch before state access, and rejects mixed shared UID packages.

- [ ] **Step 2: Run service-policy tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.SecureModuleBridgeServicePolicyTest`

Expected: compilation failure for missing policy/service types.

- [ ] **Step 3: Write failing fixed-slot statistics tests**

Assert four-package maximum, same-generation delta merge, generation replacement in the same slot, count/savedMs business caps, rollback resistance, and no source-key growth.

- [ ] **Step 4: Run statistics tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.ModuleStatsRepositoryTest`

Expected: compilation failure because the repository does not exist.

- [ ] **Step 5: Implement module settings/statistics stores and the Service**

Call `Binder.getCallingUid()` inside every AIDL method. Resolve packages through `PackageManager`; reject denied callers with `SecurityException`. Read/write only validated models. Derive the statistics package from authorization and store generation/count/savedMs under one fixed slot per package. `loadedModulePath` may affect mode metadata but never authorization.

- [ ] **Step 6: Declare only the new Service in the Manifest**

Set `android:exported="true"`; do not add a signature permission that would break LSPatch. Keep both old Receivers temporarily until Task 6 migration is green.

- [ ] **Step 7: Run focused tests and Debug build**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.SecureModuleBridgeServicePolicyTest' --tests 'com.retrsoft.bilisponsorskip.ModuleStatsRepositoryTest' assembleDebug`

Expected: PASS and successful APK build.

- [ ] **Step 8: Review checkpoint**

Verify `Binder.clearCallingIdentity()` is not used before authorization and no Binder object can perform a transaction without a fresh UID check. Do not commit without explicit permission.

### Task 3: Short-Lived Client and Settings Migration

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/SecureModuleBridgeClient.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/Settings.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsActivity.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BiliSettingsEntryInjector.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/SecureModuleBridgeClientPolicyTest.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/SettingsBridgeModeTest.kt`

**Interfaces:**
- Consumes: `ISecureModuleBridge`, `BridgeSettings`, `BRIDGE_PROTOCOL_VERSION`.
- Produces: `SecureModuleBridgeClient.pullSettings()`, `pushSettings(snapshot)`, `mergeStats(snapshot)`, and `requestPullIfDue(reason)`.
- Produces: `SettingsRepository.onHostForeground()` and `onSettingsEntryOpened()`.

- [ ] **Step 1: Write failing client lifecycle tests**

Use a fake binder connector to assert one bind per operation, unconditional unbind after success/failure, in-flight de-duplication, minimum refresh interval, exponential failure backoff, and no retry loop when the module package/Service is absent.

- [ ] **Step 2: Run client tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.SecureModuleBridgeClientPolicyTest`

Expected: compilation failure because the client does not exist.

- [ ] **Step 3: Implement the short-lived client**

Bind by explicit `ComponentName` with `BIND_AUTO_CREATE`, transact on a bounded executor, and unbind in all terminal paths. Cache unavailable-Service failures until backoff expires. Never start or foreground the Service.

- [ ] **Step 4: Write failing settings-mode tests**

Pin these outcomes: LSPosed valid bridge response updates mirror; protocol mismatch keeps existing mirror; LSPatch without Service keeps embedded mirror; embedded save writes local mirror before optional bridge push; bridge failure never clears settings.

- [ ] **Step 5: Run settings-mode tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.SettingsBridgeModeTest`

Expected: FAIL because SettingsRepository still depends on broadcasts.

- [ ] **Step 6: Migrate SettingsRepository and both settings UIs**

Remove request/update broadcasts from the new code path. Module `SettingsActivity` writes module preferences only. Embedded settings update mirror synchronously and push to the module asynchronously when available. Preserve the current snapshot on all bridge failures.

- [ ] **Step 7: Run settings/client tests and full unit suite**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.*Bridge*Test' && ./gradlew testDebugUnitTest`

Expected: PASS.

- [ ] **Step 8: Review checkpoint**

Trace initialization, foreground refresh, settings-entry refresh, embedded save and standalone save. Confirm there is no module-to-host callback or persistent connection. Do not commit without explicit permission.

### Task 4: Foreground Lifecycle Integration

**Files:**
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/XposedInit.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/BiliSettingsEntryInjector.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/ForegroundSettingsRefreshPolicyTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.onHostForeground()` and `onSettingsEntryOpened()`.
- Produces: `ForegroundSettingsRefreshPolicy` that emits only background-to-foreground transitions and respects the minimum interval.

- [ ] **Step 1: Write failing foreground transition tests**

Test multiple Activity resumes while foreground, real background-to-foreground transition, rapid bounce inside minimum interval, and one in-flight request.

- [ ] **Step 2: Run test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.ForegroundSettingsRefreshPolicyTest`

Expected: compilation failure for missing policy.

- [ ] **Step 3: Implement policy and connect existing lifecycle relay**

Reuse `Application.ActivityLifecycleCallbacks`; do not add polling. Trigger settings-entry refresh immediately when the injected preference is opened.

- [ ] **Step 4: Run lifecycle and settings tests**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.ForegroundSettingsRefreshPolicyTest' --tests 'com.retrsoft.bilisponsorskip.SettingsBridgeModeTest'`

Expected: PASS.

- [ ] **Step 5: Review checkpoint**

Verify configuration changes take effect on return to foreground without binding once per Activity resume. Do not commit without explicit permission.

### Task 5: 60-Second Statistics Flush

**Files:**
- Create: `app/src/main/java/com/retrsoft/bilisponsorskip/StatsFlushPolicy.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/LocalSkipStats.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SkipController.kt`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsActivity.kt`
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/StatsFlushPolicyTest.kt`

**Interfaces:**
- Consumes: `SecureModuleBridgeClient.mergeStats(snapshot)`.
- Consumes: Task 2 `ModuleStatsRepository.merge(callerPackage, snapshot)` through the AIDL Service.
- Produces: `StatsFlushPolicy.onChanged()`, `onStartup()`, `onForeground()`, `onFlushResult(success)`.

- [ ] **Step 1: Write failing flush-policy tests**

Use a fake clock/scheduler. Assert repeated skips inside 60 seconds schedule once; failure retains pending state; startup/foreground can flush immediately if dirty; success records acknowledged cumulative values; process-death reconstruction from LocalSkipStats sends the current snapshot.

- [ ] **Step 2: Run flush-policy tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.StatsFlushPolicyTest`

Expected: compilation failure for missing policy.

- [ ] **Step 3: Implement throttled cumulative synchronization**

Keep local persistence on every skip. Schedule one delayed flush per window on an in-process Handler; use the bridge client for the actual transaction. Remove `FLAG_RECEIVER_FOREGROUND` behavior and per-skip cross-process sends.

- [ ] **Step 4: Run focused and full tests**

Run: `./gradlew testDebugUnitTest --tests 'com.retrsoft.bilisponsorskip.*Stats*Test' && ./gradlew testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Review checkpoint**

Verify no timer survives process death, no foreground/periodic scheduling exists, failed IPC loses no cumulative state, and module preferences contain at most four source slots. Do not commit without explicit permission.

### Task 6: Remove Legacy Broadcast Attack Surface

**Files:**
- Delete: `app/src/main/java/com/retrsoft/bilisponsorskip/SettingsSyncReceiver.kt`
- Delete: `app/src/main/java/com/retrsoft/bilisponsorskip/SkipStatsReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/retrsoft/bilisponsorskip/Settings.kt`
- Modify: related tests that reference legacy broadcasts.
- Test: `app/src/test/java/com/retrsoft/bilisponsorskip/LegacyIpcRemovalTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5 replacement Service/client/statistics paths.
- Produces: no externally reachable legacy settings/statistics broadcast components or actions.

- [ ] **Step 1: Write a failing manifest/component behavior check**

Use Android manifest parsing in a Gradle test or built-APK inspection helper to assert the old Receiver class names and actions are absent, and the new Service exists with the expected exported state.

- [ ] **Step 2: Run the check and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.LegacyIpcRemovalTest`

Expected: FAIL while legacy components remain.

- [ ] **Step 3: Delete old receivers, actions, senders and dynamic receiver**

Remove all production references to the four broadcast actions and `RECEIVER_EXPORTED`. Keep unrelated explicit Android intents and user links.

- [ ] **Step 4: Run the removal test and static search**

Run: `./gradlew testDebugUnitTest --tests com.retrsoft.bilisponsorskip.LegacyIpcRemovalTest`

Run: `rg -n 'SettingsSyncReceiver|SkipStatsReceiver|ACTION_UPDATE_SETTINGS|ACTION_UPDATE_MODULE_SETTINGS|ACTION_REQUEST_MODULE_SETTINGS|ACTION_RECORD_LOCAL_SKIP|RECEIVER_EXPORTED' app/src/main`

Expected: test PASS; search returns no legacy IPC match.

- [ ] **Step 5: Build and inspect the APK Manifest**

Run: `./gradlew assembleDebug && apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk`

Expected: only `SecureModuleBridgeService` is exported for bridge IPC; old Receivers are absent.

- [ ] **Step 6: Review checkpoint**

Trace every deleted sender to its replacement and confirm no functional path silently stopped synchronizing. Do not commit without explicit permission.

### Task 7: Documentation, Full Verification and Device Matrix

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: tests only if final regression evidence requires it.

**Interfaces:**
- Consumes: completed secure IPC implementation.
- Produces: upgrade note and evidence for the three fixed runtime vulnerabilities.

- [ ] **Step 1: Update documentation**

Document Binder-based authentication, no-background-polling behavior, 60-second statistics delay, and the requirement to update/repatch both sides together. Remove references to broadcast synchronization.

- [ ] **Step 2: Run ordered verification gates**

Run: `./gradlew testDebugUnitTest assembleDebug lintDebug`

Run: `git diff --check`

Expected: all commands succeed with no test, build, lint or whitespace failures.

- [ ] **Step 3: Run attack regression on devices**

Attempt both legacy broadcast commands and a non-whitelist AIDL client on representative API 24, 28, 32, 33 and 34/35 environments. Verify settings/statistics remain unchanged and every AIDL transaction is denied.

- [ ] **Step 4: Run legitimate-mode regression**

Verify LSPosed setting pull/push and delayed aggregate statistics, then LSPatch embedded settings with no installed module. Verify SponsorBlock, splash/feed/detail filters and bottom navigation behavior.

- [ ] **Step 5: Launch one read-only bypass/regression review**

Provide the original findings and current diff only. Confirm any concrete bypass or regression before changing code, then rerun affected gates.

- [ ] **Step 6: Commit gate**

Stop and present files, diff, test evidence, device coverage and remaining uncertainty. Commit and push only after explicit user authorization.
