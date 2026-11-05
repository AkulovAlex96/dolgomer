package app.kvity.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPolicyTest {
    @Test fun freshInstallShowsOnboarding() {
        assertEquals(false, OnboardingPolicy.initialDone(storedFlag = null, installedAtMillis = 0L))
        assertEquals(true, OnboardingPolicy.shouldShow(false))
    }

    @Test fun existingInstallSkipsOnboarding() {
        assertEquals(true, OnboardingPolicy.initialDone(storedFlag = null, installedAtMillis = 1_700_000_000_000L))
        assertEquals(false, OnboardingPolicy.shouldShow(true))
    }

    @Test fun storedFlagWins() {
        assertEquals(true, OnboardingPolicy.initialDone(storedFlag = true, installedAtMillis = 0L))
        assertEquals(false, OnboardingPolicy.initialDone(storedFlag = false, installedAtMillis = 5L))
    }
}
