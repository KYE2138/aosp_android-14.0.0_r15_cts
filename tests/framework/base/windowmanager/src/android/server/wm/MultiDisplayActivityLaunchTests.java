/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.server.wm.ActivityLauncher.KEY_ACTION;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_ACTIVITY;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_IMPLICIT;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_PENDING;
import static android.server.wm.ActivityLauncher.KEY_NEW_TASK;
import static android.server.wm.ActivityLauncher.KEY_USE_APPLICATION_CONTEXT;
import static android.server.wm.CliIntentExtra.extraBool;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.WindowManagerState.STATE_DESTROYED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.ALT_LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.BROADCAST_RECEIVER_ACTIVITY;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.NON_RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.NO_HISTORY_ACTIVITY;
import static android.server.wm.app.Components.NO_HISTORY_ACTIVITY2;
import static android.server.wm.app.Components.RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_TOP_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TOP_ACTIVITY;
import static android.server.wm.app.Components.VIRTUAL_DISPLAY_ACTIVITY;
import static android.server.wm.second.Components.IMPLICIT_TARGET_SECOND_ACTIVITY;
import static android.server.wm.second.Components.IMPLICIT_TARGET_SECOND_TEST_ACTION;
import static android.server.wm.second.Components.SECOND_ACTIVITY;
import static android.server.wm.second.Components.SECOND_LAUNCH_BROADCAST_ACTION;
import static android.server.wm.second.Components.SECOND_LAUNCH_BROADCAST_RECEIVER;
import static android.server.wm.third.Components.THIRD_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.SizeInfo;
import android.server.wm.WindowManagerState.DisplayContent;
import android.server.wm.WindowManagerState.Task;
import android.view.SurfaceView;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:MultiDisplayActivityLaunchTests
 *
 *  Tests activity launching behavior on multi-display environment.
 */
@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplayActivityLaunchTests extends MultiDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsMultiDisplay());
    }

    /**
     * Tests launching an activity on virtual display.
     */
    @Test
    public void testLaunchActivityOnSecondaryDisplay() throws Exception {
        validateActivityLaunchOnNewDisplay(ACTIVITY_TYPE_STANDARD);
    }

    /**
     * Tests launching a recent activity on virtual display.
     */
    @Test
    public void testLaunchRecentActivityOnSecondaryDisplay() throws Exception {
        validateActivityLaunchOnNewDisplay(ACTIVITY_TYPE_RECENTS);
    }

    /**
     * Tests launching an assistant activity on virtual display.
     */
    @Test
    public void testLaunchAssistantActivityOnSecondaryDisplay() {
        validateActivityLaunchOnNewDisplay(ACTIVITY_TYPE_ASSISTANT);
    }

    private void validateActivityLaunchOnNewDisplay(int activityType) {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        separateTestJournal();
        getLaunchActivityBuilder().setUseInstrumentation().setWithShellPermission(true)
                .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setMultipleTask(true).setActivityType(activityType)
                .setDisplayId(newDisplay.mId).execute();
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be focused and on top");

        // Check that activity config corresponds to display config.
        final SizeInfo reportedSizes = getLastReportedSizesForActivity(TEST_ACTIVITY);
        assertEquals("Activity launched on secondary display must have proper configuration",
                CUSTOM_DENSITY_DPI, reportedSizes.densityDpi);

        assertEquals("Top activity must have correct activity type", activityType,
                mWmState.getFrontRootTaskActivityType(newDisplay.mId));
    }

    /**
     * Tests launching an activity on primary display explicitly.
     */
    @Test
    public void testLaunchActivityOnPrimaryDisplay() throws Exception {
        // Launch activity on primary display explicitly.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, DEFAULT_DISPLAY);

        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be focused and on top");

        // Launch another activity on primary display using the first one
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setMultipleTask(true).setDisplayId(DEFAULT_DISPLAY).execute();
        mWmState.computeState(TEST_ACTIVITY);

        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be focused");
    }

    /**
     * Tests launching an existing activity from an activity that resides on secondary display. An
     * existing activity on a different display should be moved to the display of the launching
     * activity.
     */
    @Test
    public void testLaunchActivityFromSecondaryDisplay() {
        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setDisplayId(DEFAULT_DISPLAY).execute();

        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();
        final int newDisplayId = newDisplay.mId;

        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(BROADCAST_RECEIVER_ACTIVITY).setNewTask(true)
                .setDisplayId(newDisplayId).execute();
        waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                "Activity should be resumed on secondary display");

        mBroadcastActionTrigger.launchActivityNewTask(getActivityName(TEST_ACTIVITY));
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplayId,
                "Activity should be resumed on secondary display");

        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setDisplayId(DEFAULT_DISPLAY).execute();
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity should be the top resumed on default display");
    }

    /**
     * Tests that an activity can be launched on a secondary display while the primary
     * display is off.
     */
    @Test
    public void testLaunchExternalDisplayActivityWhilePrimaryOff() {
        // Leanback devices may launch a live broadcast app during screen off-on cycles.
        final boolean mayLaunchActivityOnScreenOff = isLeanBack();

        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY);
        waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be resumed");

        final PrimaryDisplayStateSession displayStateSession =
                mObjectTracker.manage(new PrimaryDisplayStateSession());
        final ExternalDisplaySession externalDisplaySession = createManagedExternalDisplaySession();
        displayStateSession.turnScreenOff();

        // Make sure there is no resumed activity when the primary display is off
        if (!mayLaunchActivityOnScreenOff) {
            waitAndAssertActivityState(RESIZEABLE_ACTIVITY, STATE_STOPPED,
                    "Activity launched on primary display must be stopped after turning off");
            assertEquals("Unexpected resumed activity",
                    0, mWmState.getResumedActivitiesCount());
        }

        final DisplayContent newDisplay = externalDisplaySession
                .setCanShowWithInsecureKeyguard(true).createVirtualDisplay();

        launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

        // Check that the test activity is resumed on the external display
        waitAndAssertActivityStateOnDisplay(TEST_ACTIVITY, STATE_RESUMED, newDisplay.mId,
                "Activity launched on external display must be resumed");
        if (!mayLaunchActivityOnScreenOff) {
            mWmState.assertFocusedAppOnDisplay("App on default display must still be focused",
                    RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY);
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display. It should land on the
     * virtual display with correct configuration.
     */
    @Test
    public void testLaunchNonResizeableActivityOnSecondaryDisplay() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_FULLSCREEN, newDisplay.mId);

        waitAndAssertTopResumedActivity(NON_RESIZEABLE_ACTIVITY, newDisplay.mId,
                "Activity requested to launch on secondary display must be focused");

        final Configuration taskConfig = mWmState
                .getTaskByActivity(NON_RESIZEABLE_ACTIVITY).mFullConfiguration;
        final Configuration displayConfig = mWmState
                .getDisplay(newDisplay.mId).mFullConfiguration;

        // Check that activity config corresponds to display config.
        assertEquals("Activity launched on secondary display must have proper configuration",
                taskConfig.densityDpi, displayConfig.densityDpi);

        assertEquals("Activity launched on secondary display must have proper configuration",
                taskConfig.windowConfiguration.getBounds(),
                displayConfig.windowConfiguration.getBounds());
    }

    /**
     * Tests successfully moving a non-resizeable activity to a virtual display.
     */
    @Test
    public void testMoveNonResizeableActivityToSecondaryDisplay() {
        final VirtualDisplayLauncher virtualLauncher =
                mObjectTracker.manage(new VirtualDisplayLauncher());
        // Create new virtual display.
        final DisplayContent newDisplay = virtualLauncher
                .setSimulateDisplay(true).createDisplay();
        // Launch a non-resizeable activity on a primary display.
        final ActivitySession nonResizeableSession = virtualLauncher.launchActivity(
                builder -> builder.setTargetActivity(NON_RESIZEABLE_ACTIVITY).setNewTask(true));

        // Launch a resizeable activity on new secondary display to create a new task there.
        virtualLauncher.launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay);
        final int externalFrontRootTaskId = mWmState
                .getFrontRootTaskId(newDisplay.mId);

        // Clear lifecycle callback history before moving the activity so the later verification
        // can get the callbacks which are related to the reparenting.
        nonResizeableSession.takeCallbackHistory();

        // Try to move the non-resizeable activity to the top of the root task on secondary display.
        moveActivityToRootTaskOrOnTop(NON_RESIZEABLE_ACTIVITY, externalFrontRootTaskId);
        // Wait for a while to check that it will move.
        assertTrue("Non-resizeable activity should be moved",
                mWmState.waitForWithAmState(
                        state -> newDisplay.mId == state
                                .getDisplayByActivity(NON_RESIZEABLE_ACTIVITY),
                        "seeing if activity won't be moved"));

        waitAndAssertTopResumedActivity(NON_RESIZEABLE_ACTIVITY, newDisplay.mId,
                "The moved non-resizeable activity must be focused");
        assertActivityLifecycle(nonResizeableSession, true /* relaunched */);
    }

    /**
     * Tests launching a non-resizeable activity on virtual display from activity there. It should
     * land on the secondary display based on the resizeability of the root activity of the task.
     */
    @Test
    public void testLaunchNonResizeableActivityFromSecondaryDisplaySameTask() {
        // Create new simulated display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);
        waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be focused");

        // Launch non-resizeable activity from secondary display.
        mBroadcastActionTrigger.launchActivityNewTask(getActivityName(NON_RESIZEABLE_ACTIVITY));
        waitAndAssertTopResumedActivity(NON_RESIZEABLE_ACTIVITY, newDisplay.mId,
                "Launched activity must be on the secondary display and resumed");
    }

    /**
     * Tests launching a non-resizeable activity on virtual display in a new task from activity
     * there. It must land on the display as its caller.
     */
    @Test
    public void testLaunchNonResizeableActivityFromSecondaryDisplayNewTask() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be focused");

        // Launch non-resizeable activity from secondary display in a new task.
        getLaunchActivityBuilder().setTargetActivity(NON_RESIZEABLE_ACTIVITY)
                .setNewTask(true).setMultipleTask(true).execute();

        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);

        // Check that non-resizeable activity is on the same display.
        final int newFrontRootTaskId = mWmState.getFocusedTaskId();
        final Task newFrontRootTask = mWmState.getRootTask(newFrontRootTaskId);
        assertEquals("Launched activity must be on the same display", newDisplay.mId,
                newFrontRootTask.mDisplayId);
        assertEquals("Launched activity must be resumed",
                getActivityName(NON_RESIZEABLE_ACTIVITY),
                newFrontRootTask.mResumedActivity);
        mWmState.assertFocusedRootTask(
                "Top task must be the one with just launched activity",
                newFrontRootTaskId);
        mWmState.assertResumedActivity("NON_RESIZEABLE_ACTIVITY not resumed",
                NON_RESIZEABLE_ACTIVITY);
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity
     * via shell command and without specifying the display id - the second activity
     * must appear on the same display due to process affinity.
     */
    @Test
    public void testConsequentLaunchActivity() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be on top");

        // Launch second activity without specifying display.
        launchActivity(LAUNCHING_ACTIVITY);

        // Check that activity is launched in focused task on the new display.
        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Launched activity must be focused");
        mWmState.assertResumedActivity("LAUNCHING_ACTIVITY must be resumed", LAUNCHING_ACTIVITY);
    }

    /**
     * Tests launching an activity on a virtual display and then launching another activity in
     * a new process via shell command and without specifying the display id - the second activity
     * must appear on the primary display.
     */
    @Test
    public void testConsequentLaunchActivityInNewProcess() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be on top");

        // Launch second activity without specifying display.
        launchActivity(SECOND_ACTIVITY);

        // Check that activity is launched in focused task on primary display.
        waitAndAssertTopResumedActivity(SECOND_ACTIVITY, DEFAULT_DISPLAY,
                "Launched activity must be focused");
        assertBothDisplaysHaveResumedActivities(pair(newDisplay.mId, TEST_ACTIVITY),
                pair(DEFAULT_DISPLAY, SECOND_ACTIVITY));
    }

    /**
     * Tests launching an activity on simulated display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @Test
    public void testConsequentLaunchActivityFromSecondaryDisplay() {
        // Create new simulated display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be on top");

        // Launch second activity from app on secondary display without specifying display id.
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).execute();

        // Check that activity is launched in focused task on external display.
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Launched activity must be on top");
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @Test
    public void testConsequentLaunchActivityFromVirtualDisplay() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be on top");

        // Launch second activity from app on secondary display without specifying display id.
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).execute();
        mWmState.computeState(TEST_ACTIVITY);

        // Check that activity is launched in focused task on external display.
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Launched activity must be on top");
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one with specifying the target display - it must appear on the secondary display.
     */
    @Test
    public void testConsequentLaunchActivityFromVirtualDisplayToTargetDisplay() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be on top");

        // Launch second activity from app on secondary display specifying same display id.
        getLaunchActivityBuilder()
                .setTargetActivity(SECOND_ACTIVITY)
                .setDisplayId(newDisplay.mId)
                .execute();

        // Check that activity is launched in focused task on external display.
        waitAndAssertTopResumedActivity(SECOND_ACTIVITY, newDisplay.mId,
                "Launched activity must be on top");

        // Launch other activity with different uid and check if it has launched successfully.
        getLaunchActivityBuilder()
                .setUseBroadcastReceiver(SECOND_LAUNCH_BROADCAST_RECEIVER,
                        SECOND_LAUNCH_BROADCAST_ACTION)
                .setDisplayId(newDisplay.mId)
                .setTargetActivity(THIRD_ACTIVITY)
                .execute();

        // Check that activity is launched in focused task on external display.
        waitAndAssertTopResumedActivity(THIRD_ACTIVITY, newDisplay.mId,
                "Launched activity must be on top");
    }

    /**
     * Tests that when an {@link Activity} is running on one display but is started from a second
     * display then the {@link Activity} is moved to the second display.
     */
    @Test
    public void testLaunchExistingActivityReparentDisplay() {
        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        launchActivityOnDisplay(SECOND_ACTIVITY, DEFAULT_DISPLAY);

        waitAndAssertTopResumedActivity(SECOND_ACTIVITY, DEFAULT_DISPLAY,
                "Must launch activity on same display.");

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId,
                extraBool(KEY_USE_APPLICATION_CONTEXT, true), extraBool(KEY_NEW_TASK, true),
                extraBool(KEY_LAUNCH_ACTIVITY, true), extraBool(KEY_LAUNCH_IMPLICIT, true),
                extraString(KEY_ACTION, IMPLICIT_TARGET_SECOND_TEST_ACTION));

        waitAndAssertTopResumedActivity(IMPLICIT_TARGET_SECOND_ACTIVITY, newDisplay.mId,
                "Must launch activity on same display.");
    }

    /**
     * Tests launching an activity to secondary display from activity on primary display.
     */
    @Test
    public void testLaunchActivityFromAppToSecondaryDisplay() {
        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);

        // Create new simulated display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch activity on secondary display from the app on primary display.
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                .setDisplayId(newDisplay.mId).execute();

        // Check that activity is launched on external display.
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be focused");
        assertBothDisplaysHaveResumedActivities(pair(DEFAULT_DISPLAY, LAUNCHING_ACTIVITY),
                pair(newDisplay.mId, TEST_ACTIVITY));
    }

    /** Tests that launching app from pending activity queue on external display is allowed. */
    @Test
    public void testLaunchPendingActivityOnSecondaryDisplay() {
        pressHomeButton();
        // Create new simulated display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();
        final Bundle bundle = ActivityOptions.makeBasic().
                setLaunchDisplayId(newDisplay.mId).toBundle();
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setComponent(SECOND_ACTIVITY)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(KEY_LAUNCH_ACTIVITY, true)
                .putExtra(KEY_NEW_TASK, true);
        mContext.startActivity(intent, bundle);

        // If home key was pressed, stopAppSwitches will be called.
        // Since this test case is not start activity from shell, it won't grant
        // STOP_APP_SWITCHES and this activity should be put into pending activity queue
        // and this activity should been launched after
        // ActivityTaskManagerService.APP_SWITCH_DELAY_TIME
        mWmState.waitForPendingActivityContain(SECOND_ACTIVITY);
        // If the activity is not pending, skip this test.
        mWmState.assumePendingActivityContain(SECOND_ACTIVITY);
        // In order to speed up test case without waiting for APP_SWITCH_DELAY_TIME, we launch
        // another activity with LaunchActivityBuilder, in this way the activity can be start
        // directly and also trigger pending activity to be launched.
        getLaunchActivityBuilder()
                .setTargetActivity(THIRD_ACTIVITY)
                .execute();
        mWmState.waitForValidState(SECOND_ACTIVITY);
        waitAndAssertTopResumedActivity(THIRD_ACTIVITY, DEFAULT_DISPLAY,
                "Top activity must be the newly launched one");
        mWmState.assertVisibility(SECOND_ACTIVITY, true);
        assertEquals("Activity launched by app on secondary display must be on that display",
                newDisplay.mId, mWmState.getDisplayByActivity(SECOND_ACTIVITY));
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    @Test
    public void testMoveToDisplayOnLaunch() {
        // Launch activity with unique affinity, so it will the only one in its task.
        launchActivity(LAUNCHING_ACTIVITY);

        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession().createDisplay();
        mWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        // Launch something to that display so that a new task is created. We need this to be
        // able to compare task numbers in tasks later.
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay.mId);
        mWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);

        final int rootTaskNum = mWmState.getDisplay(DEFAULT_DISPLAY).mRootTasks.size();
        final int rootTaskNumOnSecondary = mWmState
                .getDisplay(newDisplay.mId).mRootTasks.size();

        // Launch activity on new secondary display.
        // Using custom command here, because normally we add flags
        // {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
        // when launching on some specific display. We don't do it here as we want an existing
        // task to be used.
        final String launchCommand = "am start -n " + getActivityName(LAUNCHING_ACTIVITY)
                + " --display " + newDisplay.mId;
        executeShellCommand(launchCommand);

        // Check that activity is brought to front.
        waitAndAssertActivityStateOnDisplay(LAUNCHING_ACTIVITY, STATE_RESUMED, newDisplay.mId,
                "Existing task must be brought to front");

        // Check that task has moved from primary display to secondary.
        // Since it is 1-to-1 relationship between task and root task for standard type &
        // fullscreen activity, we check the number of root tasks here
        final int rootTaskNumFinal = mWmState.getDisplay(DEFAULT_DISPLAY)
                .mRootTasks.size();
        assertEquals("Root task number in default root task must be decremented.", rootTaskNum - 1,
                rootTaskNumFinal);
        final int rootTaskNumFinalOnSecondary = mWmState
                .getDisplay(newDisplay.mId).mRootTasks.size();
        assertEquals("Root task number on external display must be incremented.",
                rootTaskNumOnSecondary + 1, rootTaskNumFinalOnSecondary);
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    @Test
    public void testMoveToEmptyDisplayOnLaunch() {
        // Launch activity with unique affinity, so it will the only one in its task. And choose
        // resizeable activity to prevent the test activity be relaunched when launch it to another
        // display, which may affect on this test case.
        launchActivity(RESIZEABLE_ACTIVITY);

        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession().createDisplay();
        mWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        final int rootTaskNum = mWmState.getDisplay(DEFAULT_DISPLAY).mRootTasks.size();

        // Launch activity on new secondary display.
        // Using custom command here, because normally we add flags
        // {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
        // when launching on some specific display. We don't do it here as we want an existing
        // task to be used.
        final String launchCommand = "am start -n " + getActivityName(RESIZEABLE_ACTIVITY)
                + " --display " + newDisplay.mId;
        executeShellCommand(launchCommand);

        // Check that activity is brought to front.
        waitAndAssertActivityStateOnDisplay(RESIZEABLE_ACTIVITY, STATE_RESUMED, newDisplay.mId,
                "Existing task must be brought to front");

        // Check that task has moved from primary display to secondary.
        final int rootTaskNumFinal = mWmState.getDisplay(DEFAULT_DISPLAY)
                .mRootTasks.size();
        assertEquals("Root task number in default root task must be decremented.", rootTaskNum - 1,
                rootTaskNumFinal);
    }

    /**
     * Tests that if a second task has the same affinity as a running task but in a separate
     * process the second task launches in the same display.
     */
    @Test
    public void testLaunchSameAffinityLaunchesSameDisplay() {
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
        mWmState.computeState(LAUNCHING_ACTIVITY);

        // Check that activity is on the secondary display.
        final int frontRootTaskId = mWmState.getFrontRootTaskId(newDisplay.mId);
        final Task firstFrontRootTask = mWmState.getRootTask(frontRootTaskId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityName(LAUNCHING_ACTIVITY),
                firstFrontRootTask.mResumedActivity);
        mWmState.assertFocusedRootTask("Top root task must be on secondary display",
                frontRootTaskId);

        executeShellCommand("am start -n " + getActivityName(ALT_LAUNCHING_ACTIVITY));
        mWmState.waitForValidState(ALT_LAUNCHING_ACTIVITY);

        // Check that second activity gets launched on the default display despite
        // the affinity match on the secondary display.
        final int displayFrontRootTaskId = mWmState.getFrontRootTaskId(newDisplay.mId);
        final Task displayFrontRootTask = mWmState.getRootTask(displayFrontRootTaskId);
        waitAndAssertTopResumedActivity(ALT_LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity launched on same display must be resumed");
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Existing task must be brought to front");

        // Check that the third intent is redirected to the first task due to the root
        // component match on the secondary display.
        final Task secondFrontRootTask = mWmState.getRootTask(frontRootTaskId);
        final int secondFrontRootTaskId = mWmState.getFrontRootTaskId(newDisplay.mId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityName(ALT_LAUNCHING_ACTIVITY),
                displayFrontRootTask.mResumedActivity);
        mWmState.assertFocusedRootTask("Top root task must be on primary display",
                secondFrontRootTaskId);
        assertEquals("Second display must contain 2 root tasks", 2,
                mWmState.getDisplay(newDisplay.mId).getRootTasks().size());
        assertEquals("Top task must contain 2 activities", 2,
                secondFrontRootTask.getActivities().size());
    }

    /**
     * Tests that an activity is launched on the preferred display where the caller resided when
     * both displays have matching tasks.
     */
    @Test
    public void testTaskMatchOrderAcrossDisplays() {
        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setDisplayId(DEFAULT_DISPLAY).execute();
        final int rootTaskId = mWmState.getFrontRootTaskId(DEFAULT_DISPLAY);

        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(BROADCAST_RECEIVER_ACTIVITY).setNewTask(true)
                .setDisplayId(DEFAULT_DISPLAY).execute();

        final DisplayContent newDisplay = createManagedVirtualDisplaySession().createDisplay();
        getLaunchActivityBuilder().setUseInstrumentation().setWithShellPermission(true)
                .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setDisplayId(newDisplay.mId).execute();
        assertNotEquals("Top focus root task should not be on default display",
                rootTaskId, mWmState.getFocusedTaskId());

        mBroadcastActionTrigger.launchActivityNewTask(getActivityName(TEST_ACTIVITY));
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity must be launched on default display");
        mWmState.assertFocusedRootTask("Top focus root task must be on the default display",
                rootTaskId);
    }

    /**
     * Tests that the task affinity search respects the launch display id.
     */
    @Test
    public void testLaunchDisplayAffinityMatch() {
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

        // Check that activity is on the secondary display.
        final int frontRootTaskId = mWmState.getFrontRootTaskId(newDisplay.mId);
        final Task firstFrontRootTask = mWmState.getRootTask(frontRootTaskId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityName(LAUNCHING_ACTIVITY), firstFrontRootTask.mResumedActivity);
        mWmState.assertFocusedRootTask("Focus must be on secondary display", frontRootTaskId);

        // We don't want FLAG_ACTIVITY_MULTIPLE_TASK, so we can't use launchActivityOnDisplay
        executeShellCommand("am start -n " + getActivityName(ALT_LAUNCHING_ACTIVITY)
                + " -f 0x10000000" // FLAG_ACTIVITY_NEW_TASK
                + " --display " + newDisplay.mId);
        mWmState.computeState(ALT_LAUNCHING_ACTIVITY);

        // Check that second activity gets launched into the affinity matching
        // task on the secondary display
        final int secondFrontRootTaskId = mWmState.getFrontRootTaskId(newDisplay.mId);
        final Task secondFrontRootTask =
                mWmState.getRootTask(secondFrontRootTaskId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityName(ALT_LAUNCHING_ACTIVITY),
                secondFrontRootTask.mResumedActivity);
        mWmState.assertFocusedRootTask("Top root task must be on secondary display",
                secondFrontRootTaskId);
        assertEquals("Second display must only contain 1 task",
                1, mWmState.getDisplay(newDisplay.mId).getRootTasks().size());
        assertEquals("Top root task must contain 2 activities",
                2, secondFrontRootTask.getActivities().size());
    }

    /**
     * Tests that a new activity launched by an activity will end up on the same display
     * even if the root task is not on the top for the display.
     */
    @Test
    public void testNewTaskSameDisplay() {
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);

        // Check that the first activity is launched onto the secondary display
        waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be resumed");

        executeShellCommand("am start -n " + getActivityName(TEST_ACTIVITY));

        // Check that the second activity is launched on the same display
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                "Activity launched on default display must be resumed");

        mBroadcastActionTrigger.launchActivityNewTask(getActivityName(LAUNCHING_ACTIVITY));

        // Check that the third activity ends up in a new task in the same display where the
        // first activity lands
        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity must be launched on secondary display");
        assertEquals("Secondary display must contain 2 root tasks", 2,
                mWmState.getDisplay(newDisplay.mId).mRootTasks.size());
    }

    /**
     * Tests that a new task launched by an activity will end up on the same display
     * even if the focused task is not on that activity's display.
     */
    @Test
    public void testNewTaskDefaultDisplay() {
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);

        // Check that the first activity is launched onto the secondary display
        waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be resumed");

        launchActivityOnDisplay(SECOND_ACTIVITY, DEFAULT_DISPLAY);

        // Check that the second activity is launched on the default display because the affinity
        // is different
        waitAndAssertTopResumedActivity(SECOND_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on default display must be resumed");
        assertBothDisplaysHaveResumedActivities(pair(DEFAULT_DISPLAY, SECOND_ACTIVITY),
                pair(newDisplay.mId, BROADCAST_RECEIVER_ACTIVITY));

        mBroadcastActionTrigger.launchActivityNewTask(getActivityName(LAUNCHING_ACTIVITY));

        // Check that the third activity ends up in a new task in the same display where the
        // first activity lands
        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                "Activity must be launched on secondary display");
        assertEquals("Secondary display must contain 2 root tasks", 2,
                mWmState.getDisplay(newDisplay.mId).mRootTasks.size());
        assertBothDisplaysHaveResumedActivities(pair(DEFAULT_DISPLAY, SECOND_ACTIVITY),
                pair(newDisplay.mId, LAUNCHING_ACTIVITY));
    }

    /**
     * Test that launching an activity implicitly will end up on the same display
     */
    @Test
    public void testLaunchingFromApplicationContext() {
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId,
                extraBool(KEY_LAUNCH_ACTIVITY, true), extraBool(KEY_LAUNCH_IMPLICIT, true),
                extraBool(KEY_NEW_TASK, true), extraBool(KEY_USE_APPLICATION_CONTEXT, true),
                extraString(KEY_ACTION, IMPLICIT_TARGET_SECOND_TEST_ACTION));
        waitAndAssertTopResumedActivity(IMPLICIT_TARGET_SECOND_ACTIVITY, newDisplay.mId,
                "Implicitly launched activity must launch on the same display");
    }

    /**
     * Test that launching an activity from pending intent will end up on the same display
     */
    @Test
    public void testLaunchingFromPendingIntent() {
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId,
                extraBool(KEY_LAUNCH_ACTIVITY, true),
                extraBool(KEY_LAUNCH_IMPLICIT, true),
                extraBool(KEY_NEW_TASK, true),
                extraBool(KEY_USE_APPLICATION_CONTEXT, true),
                extraBool(KEY_LAUNCH_PENDING, true),
                extraString(KEY_ACTION, IMPLICIT_TARGET_SECOND_TEST_ACTION));

        waitAndAssertTopResumedActivity(IMPLICIT_TARGET_SECOND_ACTIVITY, newDisplay.mId,
                "Activity launched from pending intent must launch on the same display");
    }

    /**
     * Tests than an immediate launch after new display creation is handled correctly.
     */
    @Test
    public void testImmediateLaunchOnNewDisplay() {
        // Create new virtual display and immediately launch an activity on it.
        SurfaceView surfaceView = new SurfaceView(mContext);
        final VirtualDisplay virtualDisplay = mDm.createVirtualDisplay(
                "testImmediateLaunchOnNewDisplay", /*width=*/ 400, /*height=*/ 400,
                /*densityDpi=*/ 320, surfaceView.getHolder().getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        try {
            int displayId = virtualDisplay.getDisplay().getDisplayId();
            ComponentName componentName = new ComponentName(mContext,
                    ImmediateLaunchTestActivity.class);
            getLaunchActivityBuilder().setTargetActivity(componentName).setDisplayId(
                    displayId).setUseInstrumentation().execute();

            // Check that activity is launched and placed correctly.
            waitAndAssertActivityStateOnDisplay(componentName, STATE_RESUMED, displayId,
                    "Test activity must be on top");
            final int frontRootTaskId = mWmState.getFrontRootTaskId(displayId);
            final Task firstFrontRootTask = mWmState.getRootTask(frontRootTaskId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(componentName), firstFrontRootTask.mResumedActivity);
        } finally {
            virtualDisplay.release();
        }

    }

    @Test
    public void testLaunchPendingIntentActivity() throws Exception {
        final DisplayContent displayContent = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Activity should be launched on primary display by default.
        getPendingIntentActivity(TEST_ACTIVITY).send();
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display and on top");

        final int resultCode = 1;
        // Activity should be launched on target display according to the caller context.
        final Context displayContext =
                mContext.createDisplayContext(mDm.getDisplay(displayContent.mId));
        getPendingIntentActivity(TOP_ACTIVITY).send(displayContext, resultCode, null /* intent */);
        waitAndAssertTopResumedActivity(TOP_ACTIVITY, displayContent.mId,
                "Activity launched on secondary display and on top");

        // Activity should be brought to front on the same display if it already existed.
        getPendingIntentActivity(TEST_ACTIVITY).send(displayContext, resultCode, null /* intent */);
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display and on top");

        // Activity should be moved to target display.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayContent.mId);
        getPendingIntentActivity(TEST_ACTIVITY).send(mContext, resultCode, null /* intent */,
                null /* onFinished */, null /* handler */, null /* requiredPermission */,
                options.toBundle());
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, displayContent.mId,
                "Activity launched on secondary display and on top");
    }

    @Test
    public void testLaunchActivityClearTask() {
        assertBroughtExistingTaskToAnotherDisplay(FLAG_ACTIVITY_CLEAR_TASK, LAUNCHING_ACTIVITY);
    }

    @Test
    public void testLaunchActivityClearTop() {
        assertBroughtExistingTaskToAnotherDisplay(FLAG_ACTIVITY_CLEAR_TOP, LAUNCHING_ACTIVITY);
    }

    @Test
    public void testLaunchActivitySingleTop() {
        assertBroughtExistingTaskToAnotherDisplay(FLAG_ACTIVITY_SINGLE_TOP, TEST_ACTIVITY);
    }

    @Test
    public void testLaunchActivitySingleTopOnNewDisplay() {
        launchActivity(SINGLE_TOP_ACTIVITY);
        waitAndAssertTopResumedActivity(SINGLE_TOP_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display and on top");
        final int taskId = mWmState.getTaskByActivity(SINGLE_TOP_ACTIVITY).getTaskId();

        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Launch activity on new secondary display.
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(SINGLE_TOP_ACTIVITY)
                .allowMultipleInstances(false)
                .setDisplayId(newDisplay.mId).execute();

        waitAndAssertTopResumedActivity(SINGLE_TOP_ACTIVITY, newDisplay.mId,
                "Activity launched on secondary display must be on top");

        final int taskId2 = mWmState.getTaskByActivity(SINGLE_TOP_ACTIVITY).getTaskId();
        assertEquals("Activity must be in the same task.", taskId, taskId2);
        assertEquals("Activity is the only member of its task", 1,
                mWmState.getActivityCountInTask(taskId2, null));
    }

    /**
     * This test case tests the behavior that a fullscreen activity was started on top of the
     * no-history activity from default display while sleeping. The no-history activity from
     * the external display should not be finished.
     */
    @Test
    public void testLaunchNoHistoryActivityOnNewDisplay() {
        launchActivity(NO_HISTORY_ACTIVITY);
        waitAndAssertTopResumedActivity(NO_HISTORY_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display and on top");

        final int taskId = mWmState.getTaskByActivity(NO_HISTORY_ACTIVITY).getTaskId();

        final PrimaryDisplayStateSession displayStateSession =
                mObjectTracker.manage(new PrimaryDisplayStateSession());

        // Create new virtual display.
        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        launchActivityOnDisplay(NO_HISTORY_ACTIVITY2, newDisplay.mId);

        // Check that the activity is resumed on the external display
        waitAndAssertActivityStateOnDisplay(NO_HISTORY_ACTIVITY2, STATE_RESUMED, newDisplay.mId,
                "Activity launched on external display must be resumed");
        final int taskId2 = mWmState.getTaskByActivity(NO_HISTORY_ACTIVITY2).getTaskId();

        displayStateSession.turnScreenOff();
        launchActivityOnDisplay(SHOW_WHEN_LOCKED_ACTIVITY, DEFAULT_DISPLAY);

        assertNotEquals("Activity must not be in the same task.", taskId, taskId2);
        assertEquals("No-history activity is the member of its task", 1,
                mWmState.getActivityCountInTask(taskId2, NO_HISTORY_ACTIVITY2));
        assertFalse("No-history activity should not be finished.",
                mWmState.hasActivityState(NO_HISTORY_ACTIVITY2, STATE_DESTROYED));
    }

    private void assertBroughtExistingTaskToAnotherDisplay(int flags, ComponentName topActivity) {
        // Start TEST_ACTIVITY on top of LAUNCHING_ACTIVITY within the same task
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).execute();

        final DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .createDisplay();

        // Start LAUNCHING_ACTIVITY on secondary display with target flags, verify the task
        // be reparented to secondary display
        getLaunchActivityBuilder()
                .setUseInstrumentation()
                .setTargetActivity(LAUNCHING_ACTIVITY)
                .setIntentFlags(flags)
                .allowMultipleInstances(false)
                .setDisplayId(newDisplay.mId).execute();
        waitAndAssertTopResumedActivity(topActivity, newDisplay.mId,
                "Activity launched on secondary display and on top");
    }

    private PendingIntent getPendingIntentActivity(ComponentName activity) {
        final Intent intent = new Intent();
        intent.setClassName(activity.getPackageName(), activity.getClassName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(mContext, 1 /* requestCode */, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static class ImmediateLaunchTestActivity extends Activity {}
}
