/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs

import android.content.Context
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.qs.carrier.QSCarrierGroup
import com.android.systemui.qs.carrier.QSCarrierGroupController
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider
import com.android.systemui.statusbar.phone.StatusBarIconController
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.statusbar.policy.VariableDateView
import com.android.systemui.statusbar.policy.VariableDateViewController
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.capture
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidTestingRunner::class)
class QuickStatusBarHeaderControllerTest : SysuiTestCase() {

    @Mock
    private lateinit var view: QuickStatusBarHeader
    @Mock
    private lateinit var quickQSPanelController: QuickQSPanelController
    @Mock(answer = Answers.RETURNS_SELF)
    private lateinit var qsCarrierGroupControllerBuilder: QSCarrierGroupController.Builder
    @Mock
    private lateinit var qsCarrierGroupController: QSCarrierGroupController
    @Mock
    private lateinit var iconContainer: StatusIconContainer
    @Mock
    private lateinit var qsCarrierGroup: QSCarrierGroup
    @Mock
    private lateinit var variableDateViewControllerFactory: VariableDateViewController.Factory
    @Mock
    private lateinit var variableDateViewController: VariableDateViewController
    @Mock
    private lateinit var batteryMeterViewController: BatteryMeterViewController
    @Mock
    private lateinit var clock: Clock
    @Mock
    private lateinit var variableDateView: VariableDateView
    @Mock
    private lateinit var mockView: View
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var context: Context

    private lateinit var controller: QuickStatusBarHeaderController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(view.resources).thenReturn(mContext.resources)
        `when`(view.isAttachedToWindow).thenReturn(true)
        `when`(view.context).thenReturn(context)

        controller = QuickStatusBarHeaderController(
                view,
                privacyIconsController,
                statusBarIconController,
                demoModeController,
                quickQSPanelController,
                qsCarrierGroupControllerBuilder,
                qsExpansionPathInterpolator,
                featureFlags,
                variableDateViewControllerFactory,
                batteryMeterViewController,
                insetsProvider,
                iconManagerFactory,
        )
    }

    @After
    fun tearDown() {
        controller.onViewDetached()
    }

    @Test
    fun testListeningStatus() {
        controller.setListening(true)
        verify(quickQSPanelController).setListening(true)

        controller.setListening(false)
        verify(quickQSPanelController).setListening(false)
    }
}
