/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.cts.verifier.R;
import com.android.cts.verifier.features.FeatureUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserRestrictions {
    private static final String[] RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY = new String[] {
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_ADJUST_VOLUME,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
        UserManager.DISALLOW_CONFIG_CREDENTIALS,
        UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
        UserManager.DISALLOW_CONFIG_TETHERING,
        UserManager.DISALLOW_CONFIG_WIFI,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_FUN,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_MODIFY_ACCOUNTS,
        UserManager.DISALLOW_NETWORK_RESET,
        UserManager.DISALLOW_OUTGOING_BEAM,
        UserManager.DISALLOW_REMOVE_MANAGED_PROFILE,
        UserManager.DISALLOW_SHARE_LOCATION,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_UNIFIED_PASSWORD,
        UserManager.DISALLOW_CONFIG_DATE_TIME,
        UserManager.DISALLOW_CONFIG_LOCATION,
        UserManager.DISALLOW_AIRPLANE_MODE,
        UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT,
        UserManager.DISALLOW_CONFIG_BRIGHTNESS,
        UserManager.DISALLOW_CELLULAR_2G,
    };

    private static final ArrayMap<String, UserRestrictionItem> USER_RESTRICTION_ITEMS;
    static {
        final int[] restrictionLabels = new int[] {
            R.string.disallow_add_user,
            R.string.disallow_adjust_volume,
            R.string.disallow_apps_control,
            R.string.disallow_config_cell_broadcasts,
            R.string.disallow_config_credentials,
            R.string.disallow_config_mobile_networks,
            R.string.disallow_config_tethering,
            R.string.disallow_config_wifi,
            R.string.disallow_debugging_features,
            R.string.disallow_factory_reset,
            R.string.disallow_fun,
            R.string.disallow_install_unknown_sources,
            R.string.disallow_modify_accounts,
            R.string.disallow_network_reset,
            R.string.disallow_outgoing_beam,
            R.string.disallow_remove_managed_profile,
            R.string.disallow_share_location,
            R.string.disallow_uninstall_apps,
            R.string.disallow_unified_challenge,
            R.string.disallow_config_date_time,
            R.string.disallow_config_location,
            R.string.disallow_airplane_mode,
            R.string.disallow_config_screen_timeout,
            R.string.disallow_config_brightness,
            R.string.disallow_cellular_2g,
        };

        final int[] restrictionActions = new int[] {
            R.string.disallow_add_user_action,
            R.string.disallow_adjust_volume_action,
            R.string.disallow_apps_control_action,
            R.string.disallow_config_cell_broadcasts_action,
            R.string.disallow_config_credentials_action,
            R.string.disallow_config_mobile_networks_action,
            R.string.disallow_config_tethering_action,
            R.string.disallow_config_wifi_action,
            R.string.disallow_debugging_features_action,
            R.string.disallow_factory_reset_action,
            R.string.disallow_fun_action,
            R.string.disallow_install_unknown_sources_action,
            R.string.disallow_modify_accounts_action,
            R.string.disallow_network_reset_action,
            R.string.disallow_outgoing_beam_action,
            R.string.disallow_remove_managed_profile_action,
            R.string.disallow_share_location_action,
            R.string.disallow_uninstall_apps_action,
            R.string.disallow_unified_challenge_action,
            R.string.disallow_config_date_time_action,
            R.string.disallow_config_location_action,
            R.string.disallow_airplane_mode_action,
            R.string.disallow_config_screen_timeout_action,
            R.string.disallow_config_brightness_action,
            R.string.disallow_cellular_2g_action,
        };

        final String[] settingsIntentActions = new String[] {
            Settings.ACTION_SETTINGS,
            Settings.ACTION_SOUND_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_SECURITY_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_WIFI_SETTINGS,
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Settings.ACTION_SYNC_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_NFC_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS,
            Settings.ACTION_SECURITY_SETTINGS,
            Settings.ACTION_DATE_SETTINGS,
            Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
        };

        if (RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length != restrictionLabels.length
                || RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length != restrictionActions.length
                || RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length != settingsIntentActions.length) {
            throw new AssertionError("Number of items in restrictionIds, restrictionLabels, "
                    + "restrictionActions, and settingsIntentActions do not match");
        }
        USER_RESTRICTION_ITEMS = new ArrayMap<>(RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length);
        for (int i = 0; i < RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length; ++i) {
            USER_RESTRICTION_ITEMS.put(RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY[i],
                    new UserRestrictionItem(
                            restrictionLabels[i],
                            restrictionActions[i],
                            settingsIntentActions[i]));
        }
    }

    /**
     * Copied from UserRestrictionsUtils. User restrictions that cannot be set by profile owners.
     * Applied to all users.
     */
    private static final List<String> DEVICE_OWNER_ONLY_RESTRICTIONS =
            Arrays.asList(
                    UserManager.DISALLOW_USER_SWITCH,
                    UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
                    UserManager.DISALLOW_MICROPHONE_TOGGLE,
                    UserManager.DISALLOW_CAMERA_TOGGLE,
                    UserManager.DISALLOW_CELLULAR_2G);

    /**
     * Copied from UserRestrictionsUtils. User restrictions that cannot be set by profile owners
     * of secondary users. When set by DO they will be applied to all users.
     */
    private static final List<String> PRIMARY_USER_ONLY_RESTRICTIONS =
            Arrays.asList(
                    UserManager.DISALLOW_BLUETOOTH,
                    UserManager.DISALLOW_USB_FILE_TRANSFER,
                    UserManager.DISALLOW_CONFIG_TETHERING,
                    UserManager.DISALLOW_NETWORK_RESET,
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_ADD_USER,
                    UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                    UserManager.DISALLOW_SMS,
                    UserManager.DISALLOW_FUN,
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_CREATE_WINDOWS,
                    UserManager.DISALLOW_DATA_ROAMING,
                    UserManager.DISALLOW_AIRPLANE_MODE);

    private static final List<String> ALSO_VALID_FOR_MANAGED_PROFILE_POLICY_TRANSPARENCY =
            Arrays.asList(
                    UserManager.DISALLOW_APPS_CONTROL,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_MODIFY_ACCOUNTS, UserManager.DISALLOW_SHARE_LOCATION,
                    UserManager.DISALLOW_UNIFIED_PASSWORD,
                    UserManager.DISALLOW_CONFIG_LOCATION);
    private static final List<String> ALSO_VALID_FOR_MANAGED_USER_POLICY_TRANSPARENCY =
            Arrays.asList(
                    UserManager.DISALLOW_ADJUST_VOLUME,
                    UserManager.DISALLOW_APPS_CONTROL,
                    UserManager.DISALLOW_CONFIG_WIFI,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    UserManager.DISALLOW_MODIFY_ACCOUNTS,
                    UserManager.DISALLOW_OUTGOING_BEAM,
                    UserManager.DISALLOW_SHARE_LOCATION,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_CONFIG_DATE_TIME,
                    UserManager.DISALLOW_CONFIG_LOCATION,
                    UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT,
                    UserManager.DISALLOW_CONFIG_BRIGHTNESS);

    private static final String ACTION_CREDENTIALS_INSTALL = "com.android.credentials.INSTALL";

    public static String getRestrictionLabel(Context context, String restriction) {
        final UserRestrictionItem item = findRestrictionItem(restriction);
        return context.getString(item.label);
    }

    public static String getUserAction(Context context, String restriction) {
        final UserRestrictionItem item = findRestrictionItem(restriction);
        return context.getString(item.userAction);
    }

    private static UserRestrictionItem findRestrictionItem(String restriction) {
        final UserRestrictionItem item = USER_RESTRICTION_ITEMS.get(restriction);
        if (item == null) {
            throw new IllegalArgumentException("Unknown restriction: " + restriction);
        }
        return item;
    }

    public static List<String> getUserRestrictionsForPolicyTransparency(int mode) {
        if (isDeviceOwnerMode(mode)) {
            ArrayList<String> result = new ArrayList<String>();
            // They are all valid except for DISALLOW_REMOVE_MANAGED_PROFILE
            for (String st : RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY) {
                if (!st.equals(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE)
                        && !st.equals(UserManager.DISALLOW_UNIFIED_PASSWORD)) {
                    result.add(st);
                }
            }
            return result;
        } else if (mode == PolicyTransparencyTestListActivity.MODE_MANAGED_PROFILE) {
            return ALSO_VALID_FOR_MANAGED_PROFILE_POLICY_TRANSPARENCY;
        } else if (mode == PolicyTransparencyTestListActivity.MODE_MANAGED_USER) {
            return ALSO_VALID_FOR_MANAGED_USER_POLICY_TRANSPARENCY;
        }
        throw new RuntimeException("Invalid mode " + mode);
    }

    /**
     * Creates and returns a new intent to set user restriction
     */
    public static Intent getUserRestrictionTestIntent(Context context, String restriction,
                int mode) {
        final UserRestrictionItem item = USER_RESTRICTION_ITEMS.get(restriction);
        final Intent intent =
                new Intent(PolicyTransparencyTestActivity.ACTION_SHOW_POLICY_TRANSPARENCY_TEST)
                        .putExtra(PolicyTransparencyTestActivity.EXTRA_TEST,
                                PolicyTransparencyTestActivity.TEST_CHECK_USER_RESTRICTION)
                        .putExtra(CommandReceiverActivity.EXTRA_USER_RESTRICTION, restriction)
                        .putExtra(PolicyTransparencyTestActivity.EXTRA_TITLE,
                                context.getString(item.label))
                        .putExtra(PolicyTransparencyTestActivity.EXTRA_SETTINGS_INTENT_ACTION,
                                item.intentAction);

        intent.putExtra(CommandReceiverActivity.EXTRA_USE_CURRENT_USER_DPM,
                !(isDeviceOwnerMode(mode) && isOnlyValidForDeviceOwnerOrPrimaryUser(restriction)));
        return intent;
    }

    public static boolean isRestrictionValid(Context context, String restriction) {
        final PackageManager pm = context.getPackageManager();
        final TelephonyManager tm =
                context.getSystemService(TelephonyManager.class);
        switch (restriction) {
            case UserManager.DISALLOW_ADD_USER:
                return UserManager.supportsMultipleUsers();
            case UserManager.DISALLOW_ADJUST_VOLUME:
                return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
            case UserManager.DISALLOW_AIRPLANE_MODE:
                return (!pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                    && hasSettingsActivity(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS));
            case UserManager.DISALLOW_CONFIG_BRIGHTNESS:
                return (hasSettingsActivity(context, Settings.ACTION_DISPLAY_SETTINGS)
                    && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
            case UserManager.DISALLOW_CONFIG_CELL_BROADCASTS:
                if (context.getResources().getBoolean(context.getResources()
                        .getIdentifier("config_disable_all_cb_messages", "bool", "android"))) {
                    return false;
                }
                if (!tm.isSmsCapable()) {
                    return false;
                }
                // Get com.android.internal.R.bool.config_cellBroadcastAppLinks
                final int resId = context.getResources().getIdentifier(
                        "config_cellBroadcastAppLinks", "bool", "android");
                boolean isCellBroadcastAppLinkEnabled = context.getResources().getBoolean(resId);
                try {
                    if (isCellBroadcastAppLinkEnabled) {
                        String packageName = getDefaultCellBroadcastReceiverPackageName(context);
                        if (packageName == null || pm.getApplicationEnabledSetting(packageName)
                                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                            isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
                }
                return isCellBroadcastAppLinkEnabled;
            case UserManager.DISALLOW_FUN:
                // Easter egg is not available on watch or automotive
                return FeatureUtil.isFunSupported(context);
            case UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS:
                return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
            case UserManager.DISALLOW_CONFIG_WIFI:
                return pm.hasSystemFeature(PackageManager.FEATURE_WIFI);
            case UserManager.DISALLOW_NETWORK_RESET:
                // This test should not run on watch
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            case UserManager.DISALLOW_OUTGOING_BEAM:
                return pm.hasSystemFeature(PackageManager.FEATURE_NFC)
                        && pm.hasSystemFeature(PackageManager.FEATURE_NFC_BEAM);
            case UserManager.DISALLOW_SHARE_LOCATION:
                return pm.hasSystemFeature(PackageManager.FEATURE_LOCATION);
            case UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES:
                return FeatureUtil.isInstallUnknownSourcesSupported(context);
            case UserManager.DISALLOW_CONFIG_CREDENTIALS:
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                        && hasSettingsActivity(context, ACTION_CREDENTIALS_INSTALL);
            case UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT:
                return FeatureUtil.isScreenTimeoutSupported(context);
            case UserManager.DISALLOW_CONFIG_LOCATION:
                return FeatureUtil.isConfigLocationSupported(context);
            case UserManager.DISALLOW_APPS_CONTROL:
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            case UserManager.DISALLOW_UNINSTALL_APPS:
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            case UserManager.DISALLOW_CELLULAR_2G:
                return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                        && tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK);
            default:
                return true;
        }
    }

    /**
     * Utility method to query the default CBR's package name.
     * from frameworks/base/telephony/common/com/android/internal/telephony/CellBroadcastUtils.java
     */
    private static String getDefaultCellBroadcastReceiverPackageName(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ResolveInfo resolveInfo = packageManager.resolveActivity(
                new Intent(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION),
                PackageManager.MATCH_SYSTEM_ONLY);
        String packageName;

        if (resolveInfo == null) {
            return null;
        }

        packageName = resolveInfo.activityInfo.applicationInfo.packageName;

        if (TextUtils.isEmpty(packageName) || packageManager.checkPermission(
            android.Manifest.permission.READ_CELL_BROADCASTS, packageName)
                == PackageManager.PERMISSION_DENIED) {
            return null;
        }

        return packageName;
    }

    /**
     * Utility to check if the Settings app handles an intent action
     */
    private static boolean hasSettingsActivity(Context context, String intentAction) {
        PackageManager packageManager = context.getPackageManager();
        ResolveInfo resolveInfo = packageManager.resolveActivity(
                new Intent(intentAction),
                PackageManager.MATCH_SYSTEM_ONLY);

        if (resolveInfo == null) {
            return false;
        }

        return !TextUtils.isEmpty(resolveInfo.activityInfo.applicationInfo.packageName);
    }

    /**
     * Checks whether target mode is device owner test mode
     */
    private static boolean isDeviceOwnerMode(int mode) {
        return mode == PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER;
    }

    private static boolean isOnlyValidForDeviceOwnerOrPrimaryUser(String restriction) {
        return DEVICE_OWNER_ONLY_RESTRICTIONS.contains(restriction)
                || PRIMARY_USER_ONLY_RESTRICTIONS.contains(restriction);
    }

    private static class UserRestrictionItem {
        final int label;
        final int userAction;
        final String intentAction;
        public UserRestrictionItem(int label, int userAction, String intentAction) {
            this.label = label;
            this.userAction = userAction;
            this.intentAction = intentAction;
        }
    }
}
