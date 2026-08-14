/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import com.google.android.setupcompat.util.SystemBarHelper;

public class CustomKeyActivity extends BaseSetupWizardActivity {

    private static final String TAG = CustomKeyActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarHelper.setBackButtonVisible(getWindow(), true);
        setNextText(R.string.next);

        Button accessibilityButton = findViewById(R.id.accessibility_button);
        accessibilityButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.custom_key_activity;
    }

    @Override
    protected int getTitleResId() {
        return R.string.custom_key_title;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_features;
    }
}
