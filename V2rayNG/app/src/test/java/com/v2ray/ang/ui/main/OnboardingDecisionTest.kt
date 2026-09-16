package com.v2ray.ang.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDecisionTest {
    @Test fun freshInstallShowsOnboarding() {
        assertTrue(shouldShowOnboarding(completed = false, isPackageUpdate = false, hasProfiles = false))
    }

    @Test fun completedOnboardingDoesNotRepeat() {
        assertFalse(shouldShowOnboarding(completed = true, isPackageUpdate = false, hasProfiles = false))
    }

    @Test fun appUpdateDoesNotInterruptExistingUser() {
        assertFalse(shouldShowOnboarding(completed = false, isPackageUpdate = true, hasProfiles = false))
    }

    @Test fun existingProfilesDoNotTriggerOnboarding() {
        assertFalse(shouldShowOnboarding(completed = false, isPackageUpdate = false, hasProfiles = true))
    }
}
