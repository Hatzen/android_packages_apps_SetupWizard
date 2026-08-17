/*
 * SPDX-FileCopyrightText: 2013 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.util;

import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.GET_ACTIVITIES;
import static android.telephony.TelephonyManager.PHONE_TYPE_GSM;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import static org.lineageos.setupwizard.SetupWizardApp.AIRPLANE_MODE_ON;
import static org.lineageos.setupwizard.SetupWizardApp.DISABLE_NAV_KEYS;
import static org.lineageos.setupwizard.SetupWizardApp.ENABLE_RECOVERY_UPDATE;
import static org.lineageos.setupwizard.SetupWizardApp.EXTENDED_RESTART_MENU;
import static org.lineageos.setupwizard.SetupWizardApp.KEY_SEND_METRICS;
import static org.lineageos.setupwizard.SetupWizardApp.KISS_LAUNCHER_PACKAGE;
import static org.lineageos.setupwizard.SetupWizardApp.LOGV;
import static org.lineageos.setupwizard.SetupWizardApp.NAVIGATION_OPTION_KEY;
import static org.lineageos.setupwizard.SetupWizardApp.SHOW_BATTERY_PERCENT;
import static org.lineageos.setupwizard.SetupWizardApp.UPDATE_RECOVERY_PROP;

import android.app.StatusBarManager;
import android.app.WallpaperManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.om.IOverlayManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.biometrics.BiometricManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import lineageos.hardware.LineageHardwareManager;
import lineageos.providers.LineageSettings;

import org.lineageos.setupwizard.BaseSetupWizardActivity;
import org.lineageos.setupwizard.SetupWizardApp;

import java.io.File;
import java.util.List;

public class SetupWizardUtils {

    private static final String TAG = SetupWizardUtils.class.getSimpleName();

    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final String GMS_SUW_PACKAGE = "com.google.android.setupwizard";
    private static final String GMS_TV_SUW_PACKAGE = "com.google.android.tungsten.setupwraith";
    private static final String UPDATER_PACKAGE = "org.lineageos.updater";

    private static final String UPDATE_RECOVERY_EXEC = "/vendor/bin/install-recovery.sh";
    private static final String CONFIG_HIDE_RECOVERY_UPDATE = "config_hideRecoveryUpdate";
    private static final String PROP_BUILD_DATE = "ro.build.date.utc";

    private SetupWizardUtils() {
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("SetupWizardPrefs", MODE_PRIVATE);
    }

    public static boolean hasWifi(Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    public static boolean hasTelephony(Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    public static boolean hasRecoveryUpdater(Context context) {
        boolean fileExists = new File(UPDATE_RECOVERY_EXEC).exists();
        if (!fileExists) {
            return false;
        }

        boolean featureHidden = false;
        try {
            PackageManager pm = context.getPackageManager();
            Resources updaterResources = pm.getResourcesForApplication(UPDATER_PACKAGE);
            int res = updaterResources.getIdentifier(
                    CONFIG_HIDE_RECOVERY_UPDATE, "bool", UPDATER_PACKAGE);
            featureHidden = updaterResources.getBoolean(res);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
        }
        return !featureHidden;
    }

    public static boolean isOwner() {
        return UserHandle.myUserId() == 0;
    }

    public static boolean isManagedProfile(Context context) {
        return context.getSystemService(UserManager.class).isManagedProfile();
    }

    public static StatusBarManager disableStatusBar(Context context) {
        StatusBarManager statusBarManager = context.getSystemService(StatusBarManager.class);
        if (statusBarManager != null) {
            if (LOGV) {
                Log.v(SetupWizardApp.TAG, "Disabling status bar");
            }
            statusBarManager.setDisabledForSetup(true);
        } else {
            Log.w(SetupWizardApp.TAG,
                    "Skip disabling status bar - could not get StatusBarManager");
        }
        return statusBarManager;
    }

    public static void enableStatusBar() {
        StatusBarManager statusBarManager = SetupWizardApp.getStatusBarManager();
        if (statusBarManager != null) {
            if (LOGV) {
                Log.v(SetupWizardApp.TAG, "Enabling status bar");
            }
            statusBarManager.setDisabledForSetup(false);

            // Session must be destroyed if it's not used anymore
            statusBarManager = null;
        } else {
            Log.w(SetupWizardApp.TAG,
                    "Skip enabling status bar - could not get StatusBarManager");
        }
    }

    public static boolean hasGMS(Context context) {
        String gmsSuwPackage = hasLeanback(context) ? GMS_TV_SUW_PACKAGE : GMS_SUW_PACKAGE;

        if (isPackageInstalled(context, GMS_PACKAGE) &&
                isPackageInstalled(context, gmsSuwPackage)) {
            PackageManager packageManager = context.getPackageManager();
            if (LOGV) {
                Log.v(TAG, GMS_SUW_PACKAGE + " state = " +
                        packageManager.getApplicationEnabledSetting(gmsSuwPackage));
            }
            return packageManager.getApplicationEnabledSetting(gmsSuwPackage) !=
                    COMPONENT_ENABLED_STATE_DISABLED;
        }
        return false;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName, GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void finishSetupWizard(BaseSetupWizardActivity context) {
        if (LOGV) {
            Log.v(TAG, "finishSetupWizard");
        }
        ContentResolver contentResolver = context.getContentResolver();
        Settings.Global.putInt(contentResolver,
                Settings.Global.DEVICE_PROVISIONED, 1);
        final int userSetupComplete =
                Settings.Secure.getInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);
        if (userSetupComplete != 0 && !SetupWizardUtils.isManagedProfile(context)) {
            Log.e(TAG, "finishSetupWizard, but userSetupComplete=" + userSetupComplete + "! "
                    + "This should not happen!");
        }
        Settings.Secure.putInt(contentResolver,
                Settings.Secure.USER_SETUP_COMPLETE, 1);
        if (hasLeanback(context)) {
            Settings.Secure.putInt(contentResolver,
                    Settings.Secure.TV_USER_SETUP_COMPLETE, 1);
        }

        handleEnableMetrics(context);
        handleNavKeys(context);
        handleRecoveryUpdate();
        handleNavigationOption();
        handleAirplaneMode(context);
        handleExtendedRestartMenu(context);
        handleBatteryPercent(context);
        handleDeveloperOptions(context);
        handleBluetooth(context);
        handleNfc(context);
        WallpaperManager.getInstance(context).forgetLoadedWallpaper();
        disableHome(context);
        enableStatusBar();
        context.finishAffinity();
        context.nextAction(RESULT_SKIP);
        Log.i(TAG, "Setup complete!");
    }

    public static boolean isBluetoothDisabled() {
        return SystemProperties.getBoolean("config.disable_bluetooth", false);
    }

    public static boolean isNetworkConnectedToInternetViaEthernet(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities networkCapabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return networkCapabilities != null &&
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static boolean hasLeanback(Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static boolean hasBiometric(Context context) {
        BiometricManager biometricManager = context.getSystemService(BiometricManager.class);
        int result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return switch (result) {
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                    BiometricManager.BIOMETRIC_SUCCESS -> true;
            default -> false;
        };
    }

    /**
     * Disable the Home component, which is presumably SetupWizardActivity at this time.
     */
    public static void disableHome(Context context) {
        ComponentName homeComponent = getHomeComponent(context);
        if (homeComponent != null) {
            setComponentEnabledState(context, homeComponent, COMPONENT_ENABLED_STATE_DISABLED);
        } else {
            Log.w(TAG, "Home component not found. Skipping.");
        }
    }

    private static ComponentName getHomeComponent(Context context) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        intent.setPackage(context.getPackageName());
        ComponentName comp = intent.resolveActivity(context.getPackageManager());
        if (LOGV) {
            Log.v(TAG, "resolveActivity for intent=" + intent + " returns " + comp);
        }
        return comp;
    }

    public static void disableComponent(Context context, Class<?> cls) {
        setComponentEnabledState(context, new ComponentName(context, cls),
                COMPONENT_ENABLED_STATE_DISABLED);
    }

    public static void enableComponent(Context context, Class<?> cls) {
        setComponentEnabledState(context, new ComponentName(context, cls),
                COMPONENT_ENABLED_STATE_ENABLED);
    }

    public static void setComponentEnabledState(Context context, ComponentName componentName,
            int enabledState) {
        context.getPackageManager().setComponentEnabledSetting(componentName,
                enabledState, DONT_KILL_APP);
    }

    private static void handleEnableMetrics(Context context) {
        Bundle privacyData = SetupWizardApp.getSettingsBundle();
        if (privacyData != null
                && privacyData.containsKey(KEY_SEND_METRICS)) {
            LineageSettings.Secure.putInt(context.getContentResolver(),
                    LineageSettings.Secure.STATS_COLLECTION,
                    privacyData.getBoolean(KEY_SEND_METRICS)
                            ? 1 : 0);
        }
    }

    private static void handleNavKeys(Context context) {
        if (SetupWizardApp.getSettingsBundle().containsKey(DISABLE_NAV_KEYS)) {
            writeDisableNavkeysOption(context,
                    SetupWizardApp.getSettingsBundle().getBoolean(DISABLE_NAV_KEYS));
        }
    }

    private static void handleRecoveryUpdate() {
        if (SetupWizardApp.getSettingsBundle().containsKey(ENABLE_RECOVERY_UPDATE)) {
            boolean update = SetupWizardApp.getSettingsBundle()
                    .getBoolean(ENABLE_RECOVERY_UPDATE);

            SystemProperties.set(UPDATE_RECOVERY_PROP, String.valueOf(update));
        }
    }

    private static void handleNavigationOption() {
        Bundle settingsBundle = SetupWizardApp.getSettingsBundle();
        IOverlayManager overlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        
        String selectedNavMode;
        if (settingsBundle.containsKey(NAVIGATION_OPTION_KEY)) {
            selectedNavMode = settingsBundle.getString(NAVIGATION_OPTION_KEY);
        } else {
            // Default to 3-button navigation
            selectedNavMode = NAV_BAR_MODE_3BUTTON_OVERLAY;
        }

        try {
            overlayManager.setEnabledExclusiveInCategory(selectedNavMode,
                    UserHandle.USER_CURRENT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set navigation mode to " + selectedNavMode, e);
        }
    }

    private static void writeDisableNavkeysOption(Context context, boolean enabled) {
        final boolean virtualKeysEnabled = LineageSettings.System.getIntForUser(
                context.getContentResolver(), LineageSettings.System.FORCE_SHOW_NAVBAR, 0,
                UserHandle.USER_CURRENT) != 0;
        if (enabled != virtualKeysEnabled) {
            LineageSettings.System.putIntForUser(context.getContentResolver(),
                    LineageSettings.System.FORCE_SHOW_NAVBAR, enabled ? 1 : 0,
                    UserHandle.USER_CURRENT);

            final LineageHardwareManager hardware = LineageHardwareManager.getInstance(context);
            hardware.set(LineageHardwareManager.FEATURE_KEY_DISABLE, enabled);
        }
    }

    public static long getBuildDateTimestamp() {
        return SystemProperties.getLong(PROP_BUILD_DATE, 0);
    }

    public static boolean simMissing(Context context) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        if (tm == null || sm == null) {
            return false;
        }
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        if (subs != null) {
            for (SubscriptionInfo sub : subs) {
                int simState = tm.getSimState(sub.getSimSlotIndex());
                if (LOGV) {
                    Log.v(TAG, "getSimState(" + sub.getSubscriptionId() + ") == " + simState);
                }
                if (simState != -1) {
                    final int subId = sub.getSubscriptionId();
                    final TelephonyManager subTm = tm.createForSubscriptionId(subId);
                    if (subTm.getCurrentPhoneType() == PHONE_TYPE_GSM) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void handleAirplaneMode(Context context) {
        // Always enable airplane mode
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 1);
        // Broadcast the change
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", true);
        context.sendBroadcast(intent);
    }

    private static void handleExtendedRestartMenu(Context context) {
        // Enable extended restart menu (LineageOS specific setting)
        LineageSettings.Secure.putInt(context.getContentResolver(),
                LineageSettings.Secure.ADVANCED_REBOOT, 1);
    }

    private static void handleBatteryPercent(Context context) {
        // Show battery percentage
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.SHOW_BATTERY_PERCENT, 1);
    }

    private static void handleDeveloperOptions(Context context) {
        // Enable developer options and ADB
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.ADB_ENABLED, 1);
    }

    private static void handleBluetooth(Context context) {
        // Disable Bluetooth
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
            }
        }
    }

    private static void handleNfc(Context context) {
        // Disable NFC
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.disable();
        }
    }

    // TODO: Keep if we want to force set this launcher
    private static void handleKissLauncher(Context context) {
        // Force KISS Launcher as default if installed
        if (isPackageInstalled(context, KISS_LAUNCHER_PACKAGE)) {
            // Disable all other launchers and enable KISS
            PackageManager pm = context.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(homeIntent, 0);

            for (ResolveInfo info : resolveInfos) {
                String packageName = info.activityInfo.packageName;
                if (!packageName.equals(KISS_LAUNCHER_PACKAGE) &&
                        !packageName.equals("org.lineageos.setupwizard")) {
                    // Disable other launchers
                    try {
                        pm.setApplicationEnabledSetting(packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to disable launcher: " + packageName, e);
                    }
                }
            }
        }
    }
}
