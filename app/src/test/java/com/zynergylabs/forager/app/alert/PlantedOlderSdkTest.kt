package com.zynergylabs.forager.app.alert

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Planted for the drift check (dispatch 2026-10-26-08): sets sdk 30 and is not in the older-SDK list. Never merges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PlantedOlderSdkTest {
    @Test fun planted() = Unit
}
