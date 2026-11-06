package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsBlockingPolicyTest {
    @Test
    fun analyticsBlockingDefaultsToDisabledAndParticipatesInSettingsSync() {
        val settings = SettingsSnapshot()

        assertFalse(settings.disableAnalyticsReporting)
        assertFalse(settings.booleanSettings().getValue(SettingsContract.KEY_DISABLE_ANALYTICS_REPORTING))
        assertTrue(SettingsContract.KEY_DISABLE_ANALYTICS_REPORTING in SettingsContract.BOOLEAN_SETTING_KEYS)
    }

    @Test
    fun standaloneSettingsTransportRetainsSavedAnalyticsSwitch() {
        val saved = mapOf(SettingsContract.KEY_DISABLE_ANALYTICS_REPORTING to true)
        val values = SettingsContract.readBooleanSettingsForSync { key, fallback -> saved[key] ?: fallback }

        assertEquals(true, values[SettingsContract.KEY_DISABLE_ANALYTICS_REPORTING])
        assertEquals(true, values[SettingsContract.KEY_ENABLED])
        assertEquals(true, values[SettingsContract.KEY_HIDE_BOTTOM_PUBLISH_TAB])
        assertEquals(SettingsContract.BOOLEAN_SETTING_KEYS.toSet(), values.keys)
    }

    @Test
    fun standaloneSettingsTransportKeepsAnalyticsDisabledForExistingUsers() {
        val values = SettingsContract.readBooleanSettingsForSync { _, fallback -> fallback }

        assertEquals(false, values[SettingsContract.KEY_DISABLE_ANALYTICS_REPORTING])
    }

    @Test
    fun analyticsBlockingRequiresMasterAndIndependentSwitches() {
        assertFalse(shouldBlockAnalytics(SettingsSnapshot(enabled = false, disableAnalyticsReporting = true)))
        assertFalse(shouldBlockAnalytics(SettingsSnapshot(enabled = true, disableAnalyticsReporting = false)))
        assertTrue(shouldBlockAnalytics(SettingsSnapshot(enabled = true, disableAnalyticsReporting = true)))
    }

    @Test
    fun diagnosticsRemainAllowedWhenForwardedThroughSharedNeuronEntries() {
        val settings = SettingsSnapshot(disableAnalyticsReporting = true)
        listOf("infra.crash", "infra.xcrash", "infra.net", "infra.setup", "infra.webimage").forEach { eventId ->
            assertFalse("diagnostic event must pass: $eventId", shouldBlockAnalytics(settings, eventId))
        }
        assertTrue(shouldBlockAnalytics(settings, "main.homepage.bottombar.0.click"))
        assertTrue(shouldBlockAnalytics(settings, "infra.statistics.custom"))
    }

    @Test
    fun sharedNeuronHookUsesTheEventArgumentToPreserveDiagnostics() {
        val settings = SettingsSnapshot(disableAnalyticsReporting = true)
        listOf("trackT", "trackTInCurrentProcess").forEach { method ->
            listOf("infra.crash", "infra.xcrash", "infra.net", "infra.setup", "infra.webimage").forEach { eventId ->
                assertFalse(
                    shouldBlockNeuronCall(settings, method, arrayOf(false, eventId, emptyMap<String, String>(), 1, null)),
                )
            }
            assertTrue(
                shouldBlockNeuronCall(settings, method, arrayOf(false, "main.homepage.0.0.show", emptyMap<String, String>(), 1, null)),
            )
            assertFalse(shouldBlockNeuronCall(settings, method, emptyArray()))
        }
        assertFalse(shouldBlockNeuronCall(settings, "initialize", emptyArray()))
        assertFalse(shouldBlockNeuronCall(settings, "track", arrayOf<Any?>(false, "infra.net")))
        assertTrue(shouldBlockNeuronCall(settings, "reportClick", arrayOf<Any?>(false, "main.homepage.bottombar.0.click")))
    }

    @Test
    fun builderPreservesUnrecognizedPayloadAndOnlyBlocksIdentifiedEvents() {
        val settings = SettingsSnapshot(disableAnalyticsReporting = true)
        val behavior = VerifiedBuilder("main.homepage.bottombar.0.click")
        assertTrue(shouldBlockNeuronBuilder(settings, behavior))
        listOf("infra.crash", "infra.xcrash", "infra.net", "infra.setup", "infra.webimage").forEach { eventId ->
            assertFalse(shouldBlockNeuronBuilder(settings, VerifiedBuilder(eventId)))
        }
        assertFalse(shouldBlockNeuronBuilder(settings, VerifiedBuilder(null)))
        assertFalse(shouldBlockNeuronBuilder(settings, VerifiedBuilder("")))
        assertFalse(shouldBlockNeuronBuilder(settings, Any()))
        assertThrows(NoSuchFieldException::class.java) { shouldBlockNeuronBuilder(settings, MissingEventBuilder()) }
        assertFalse(shouldBlockNeuronBuilder(settings.copy(enabled = false), behavior))
        assertFalse(shouldBlockNeuronBuilder(settings.copy(disableAnalyticsReporting = false), behavior))
    }

    @Test
    fun builderBlockingRejectsReusedObfuscatedClassWithoutSubmitContract() {
        val settings = SettingsSnapshot(disableAnalyticsReporting = true)

        assertFalse(shouldBlockNeuronBuilder(settings, OverridesGate(null, null, "apm", null)))
        assertFalse(shouldBlockNeuronBuilder(settings, SubmitWithArgument()))
        assertFalse(shouldBlockNeuronBuilder(settings, SubmitWithResult()))
        assertFalse(shouldBlockNeuronBuilder(settings, StaticSubmit()))
    }

    @Test
    fun builderRejectsChangedEventFieldMapping() {
        val settings = SettingsSnapshot(disableAnalyticsReporting = true)

        assertThrows(IllegalStateException::class.java) {
            shouldBlockNeuronBuilder(settings, IncorrectEventBuilder())
        }
    }

    @Test
    fun neuronMethodPolicyBlocksBehaviorEventsButKeepsDiagnostics() {
        assertTrue("reportClick" in NEURON_ANALYTICS_METHODS)
        assertTrue("reportExposure" in NEURON_ANALYTICS_METHODS)
        assertTrue("reportPageView" in NEURON_ANALYTICS_METHODS)
        assertTrue("reportPlayer" in NEURON_ANALYTICS_METHODS)
        assertTrue("trackT" in NEURON_ANALYTICS_METHODS)

        assertFalse("trackCrash" in NEURON_ANALYTICS_METHODS)
        assertFalse("trackXCrash" in NEURON_ANALYTICS_METHODS)
        assertFalse("trackNet" in NEURON_ANALYTICS_METHODS)
        assertFalse("trackSetup" in NEURON_ANALYTICS_METHODS)
        assertFalse("trackImage" in NEURON_ANALYTICS_METHODS)
        assertFalse("track" in NEURON_ANALYTICS_METHODS)
        assertFalse("initialize" in NEURON_ANALYTICS_METHODS)
    }

    @Test
    fun legacyAndBuilderPoliciesOnlyContainSubmissionMethods() {
        assertTrue(INFO_EYES_ANALYTICS_METHODS.containsAll(listOf("feedEvent", "report", "report2")))
        assertFalse("initialize" in INFO_EYES_ANALYTICS_METHODS)
        assertTrue(NEURON_BUILDER_ANALYTICS_METHODS == setOf("submit"))
    }

    private class VerifiedBuilder(@JvmField val c: String?) {
        fun submit() = Unit
    }

    private class MissingEventBuilder {
        fun submit() = Unit
    }

    private class OverridesGate(val a: String?, val b: String?, @JvmField val c: String?, val d: String?)

    private class SubmitWithArgument(@JvmField val c: String = "event") {
        fun submit(eventId: String) = Unit
    }

    private class SubmitWithResult(@JvmField val c: String = "event") {
        fun submit(): String = c
    }

    private class IncorrectEventBuilder(@JvmField val c: Long = 1) {
        fun submit() = Unit
    }

    private class StaticSubmit(@JvmField val c: String = "event") {
        companion object {
            @JvmStatic fun submit() = Unit
        }
    }
}
