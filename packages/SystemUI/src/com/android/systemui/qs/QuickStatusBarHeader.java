/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.internal.policy.SystemBarUtils;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.battery.BatteryMeterView;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.VariableDateView;
import com.android.systemui.util.LargeScreenUtils;
import com.android.systemui.tuner.TunerService;

import java.util.List;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout implements TunerService.Tunable,
        View.OnClickListener, View.OnLongClickListener {

    private static final String SHOW_QS_CLOCK =
            "system:" + Settings.System.SHOW_QS_CLOCK;
    private static final String SHOW_QS_DATE =
            "system:" + Settings.System.SHOW_QS_DATE;

    private boolean mExpanded;
    private boolean mQsDisabled;

    @Nullable
    private TouchAnimator mAlphaAnimator;
    @Nullable
    private TouchAnimator mTranslationAnimator;
    @Nullable
    private TouchAnimator mIconsAlphaAnimator;
    private TouchAnimator mIconsAlphaAnimatorFixed;

    protected QuickQSPanel mHeaderQsPanel;
    private View mDatePrivacyView;
    private View mDateView;
    // DateView next to clock. Visible on QQS
    private VariableDateView mClockDateView;
    private View mStatusIconsView;
    private View mContainer;

    private View mQSCarriers;
    private ViewGroup mClockContainer;
    private Clock mClockView;
    private Space mDatePrivacySeparator;
    private View mClockIconsSeparator;
    private boolean mShowClockIconsSeparator;
    private View mRightLayout;
    private View mDateContainer;
    private View mPrivacyContainer;

    private BatteryMeterView mBatteryRemainingIcon;
    private StatusIconContainer mIconContainer;
    private View mPrivacyChip;

    @Nullable
    private TintedIconManager mTintedIconManager;
    @Nullable
    private QSExpansionPathInterpolator mQSExpansionPathInterpolator;
    private StatusBarContentInsetsProvider mInsetsProvider;

    private int mRoundedCornerPadding = 0;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mKeyguardExpansionFraction;
    private int mTextColorPrimary = Color.TRANSPARENT;
    private int mTopViewMeasureHeight;

    @NonNull
    private List<String> mRssiIgnoredSlots = List.of();
    private boolean mIsSingleCarrier;

    private boolean mHasCenterCutout;
    private boolean mConfigShowBatteryEstimate;

    private boolean mUseCombinedQSHeader;
    private boolean mShowDate;

    private final ActivityStarter mActivityStarter;
    private final Vibrator mVibrator;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * How much the view containing the clock and QQS will translate down when QS is fully expanded.
     *
     * This matches the measured height of the view containing the date and privacy icons.
     */
    public int getOffsetTranslation() {
        return mTopViewMeasureHeight;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mDatePrivacyView = findViewById(R.id.quick_status_bar_date_privacy);
        mStatusIconsView = findViewById(R.id.quick_qs_status_icons);
        mQSCarriers = findViewById(R.id.carrier_group);
        mContainer = findViewById(R.id.qs_container);
        mIconContainer = findViewById(R.id.statusIcons);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mDateView = findViewById(R.id.date);
        mDateView.setOnClickListener(this);
        mDateView.setOnLongClickListener(this);
        mClockDateView = findViewById(R.id.date_clock);
        mClockDateView.setOnClickListener(this);
        mClockDateView.setOnLongClickListener(this);
        mClockIconsSeparator = findViewById(R.id.separator);
        mRightLayout = findViewById(R.id.rightLayout);
        mDateContainer = findViewById(R.id.date_container);
        mPrivacyContainer = findViewById(R.id.privacy_container);

        mClockContainer = findViewById(R.id.clock_container);
        mClockView = findViewById(R.id.clock);
        mClockView.setQsHeader();
        mClockView.setOnClickListener(this);
        mDatePrivacySeparator = findViewById(R.id.space);
        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);
        mBatteryRemainingIcon.setOnClickListener(this);
        mBatteryRemainingIcon.setOnLongClickListener(this);

        updateResources();
        Configuration config = mContext.getResources().getConfiguration();
        setDatePrivacyContainersWidth(config.orientation == Configuration.ORIENTATION_LANDSCAPE);

        // QS will always show the estimate, and BatteryMeterView handles the case where
        // it's unavailable or charging
        mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);

        mIconsAlphaAnimatorFixed = new TouchAnimator.Builder()
                .addFloat(mIconContainer, "alpha", 0, 1)
                .addFloat(mBatteryRemainingIcon, "alpha", 0, 1)
                .build();

        Dependency.get(TunerService.class).addTunable(this,
                StatusBarIconController.ICON_HIDE_LIST,
                SHOW_QS_CLOCK,
                SHOW_QS_DATE);
    }

    void onAttach(TintedIconManager iconManager,
            QSExpansionPathInterpolator qsExpansionPathInterpolator,
            List<String> rssiIgnoredSlots,
            StatusBarContentInsetsProvider insetsProvider,
            boolean useCombinedQSHeader) {
        mUseCombinedQSHeader = useCombinedQSHeader;
        mTintedIconManager = iconManager;
        mRssiIgnoredSlots = rssiIgnoredSlots;
        mInsetsProvider = insetsProvider;
        int fillColor = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.textColorPrimary);

        // Set the correct tint for the status icons so they contrast
        iconManager.setTint(fillColor);

        mQSExpansionPathInterpolator = qsExpansionPathInterpolator;
        updateAnimators();
    }

    void setIsSingleCarrier(boolean isSingleCarrier) {
        mIsSingleCarrier = isSingleCarrier;
        if (mIsSingleCarrier) {
            mIconContainer.removeIgnoredSlots(mRssiIgnoredSlots);
        }
        updateAlphaAnimator();
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mDatePrivacyView.getMeasuredHeight() != mTopViewMeasureHeight) {
            mTopViewMeasureHeight = mDatePrivacyView.getMeasuredHeight();
            updateAnimators();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
        setDatePrivacyContainersWidth(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void setDatePrivacyContainersWidth(boolean landscape) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mDateContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mDateContainer.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mPrivacyContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mPrivacyContainer.setLayoutParams(lp);
    }

    private void updateBatteryMode() {
        if (mConfigShowBatteryEstimate) {
            mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);
        } else {
            mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ON);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only react to touches inside QuickQSPanel
        if (event.getY() > mHeaderQsPanel.getTop()) {
            return super.onTouchEvent(event);
        } else {
            return false;
        }
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = 0;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        if (largeScreenHeaderActive) {
            qqsLP.topMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.qqs_layout_margin_top);
        } else {
            qqsLP.topMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height);
        }
        mHeaderQsPanel.setLayoutParams(qqsLP);

        updateBatteryMode();
        updateHeadersPadding();
        updateAnimators();

        updateClockDatePadding();
    }

    private void updateClockDatePadding() {
        int startPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.status_bar_left_clock_starting_padding);
        int endPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.status_bar_left_clock_end_padding);
        mClockView.setPaddingRelative(
                startPadding,
                mClockView.getPaddingTop(),
                endPadding,
                mClockView.getPaddingBottom()
        );

        MarginLayoutParams lp = (MarginLayoutParams) mClockDateView.getLayoutParams();
        lp.setMarginStart(endPadding);
        mClockDateView.setLayoutParams(lp);
    }

    private void updateAnimators() {
        if (mUseCombinedQSHeader) {
            mTranslationAnimator = null;
            return;
        }
        updateAlphaAnimator();
        int offset = mTopViewMeasureHeight;

        mTranslationAnimator = new TouchAnimator.Builder()
                .addFloat(mContainer, "translationY", 0, offset)
                .setInterpolator(mQSExpansionPathInterpolator != null
                        ? mQSExpansionPathInterpolator.getYInterpolator()
                        : null)
                .build();
    }

    private void updateAlphaAnimator() {
        if (mUseCombinedQSHeader) {
            mAlphaAnimator = null;
            return;
        }
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                // These views appear on expanding down
                .addFloat(mDateView, "alpha", 0, 0, 1)
                .addFloat(mClockDateView, "alpha", 1, 0, 0)
                .addFloat(mQSCarriers, "alpha", 0, 1)
                .setListener(new TouchAnimator.ListenerAdapter() {
                    @Override
                    public void onAnimationAtEnd() {
                        super.onAnimationAtEnd();
                        if (!mIsSingleCarrier) {
                            mIconContainer.addIgnoredSlots(mRssiIgnoredSlots);
                        }
                        // Make it gone so there's enough room for carrier names
                        mClockDateView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationStarted() {
                        if (mShowDate) {
                            mClockDateView.setVisibility(View.VISIBLE);
                            mClockDateView.setFreezeSwitching(true);
                        }
                        setSeparatorVisibility(false);
                        if (!mIsSingleCarrier) {
                            mIconContainer.addIgnoredSlots(mRssiIgnoredSlots);
                        }
                    }

                    @Override
                    public void onAnimationAtStart() {
                        super.onAnimationAtStart();
                        if (mShowDate) {
                            mClockDateView.setFreezeSwitching(false);
                            mClockDateView.setVisibility(View.VISIBLE);
                        }
                        setSeparatorVisibility(mShowClockIconsSeparator);
                        // In QQS we never ignore RSSI.
                        mIconContainer.removeIgnoredSlots(mRssiIgnoredSlots);
                    }
                });
        mAlphaAnimator = builder.build();
    }

    void setChipVisibility(boolean visibility) {
        if (visibility) {
            // Animates the icons and battery indicator from alpha 0 to 1, when the chip is visible
            mIconsAlphaAnimator = mIconsAlphaAnimatorFixed;
            mIconsAlphaAnimator.setPosition(mKeyguardExpansionFraction);
        } else {
            mIconsAlphaAnimator = null;
            mIconContainer.setAlpha(1);
            mBatteryRemainingIcon.setAlpha(1);
        }

    }

    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        updateResources();
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    /**
     * Scroll the headers away.
     *
     * @param scrollY the scroll of the QSPanel container
     */
    public void setExpandedScrollAmount(int scrollY) {
        mStatusIconsView.setScrollY(scrollY);
        mDatePrivacyView.setScrollY(scrollY);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case SHOW_QS_CLOCK:
                boolean showClock =
                        TunerService.parseIntegerSwitch(newValue, true);
                mClockView.setClockVisibleByUser(showClock);
                break;
            case SHOW_QS_DATE:
                mShowDate =
                        TunerService.parseIntegerSwitch(newValue, true);
                mDateContainer.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
                mClockDateView.setVisibility(mShowDate ? View.VISIBLE : View.GONE);
                break;
            default:
                break;
        }
        mClockView.setClockVisibleByUser(!StatusBarIconController.getIconHideList(
                mContext, newValue).contains("clock"));
    }
}
