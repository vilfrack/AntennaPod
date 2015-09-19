package de.test.antennapod.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.test.FlakyTest;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;

import com.robotium.solo.Solo;
import com.robotium.solo.Timeout;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.service.playback.PlayerStatus;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.PodDBAdapter;
import de.danoeh.antennapod.core.util.playback.Playable;
import de.danoeh.antennapod.core.util.playback.PlaybackController;

/**
 * test cases for starting and ending playback from the MainActivity and AudioPlayerActivity
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class PlaybackSonicTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private static final String TAG = PlaybackTest.class.getSimpleName();
    public static final int EPISODES_DRAWER_LIST_INDEX = 1;
    public static final int QUEUE_DRAWER_LIST_INDEX = 0;

    private Solo solo;
    private UITestUtils uiTestUtils;

    private Context context;

    private PlaybackController controller;
    protected FeedMedia currentMedia;

    private PlaybackController createController(Activity activity) {
        return new PlaybackController(activity, false) {

            @Override
            public void setupGUI() {
            }

            @Override
            public void onPositionObserverUpdate() {
            }

            @Override
            public void onBufferStart() {
            }

            @Override
            public void onBufferEnd() {
            }

            @Override
            public void onBufferUpdate(float progress) {
            }

            @Override
            public void handleError(int code) {
            }

            @Override
            public void onReloadNotification(int code) {
            }

            @Override
            public void onSleepTimerUpdate() {
            }

            @Override
            public ImageButton getPlayButton() {
                return null;
            }

            @Override
            public void postStatusMsg(int msg) {
            }

            @Override
            public void clearStatusMsg() {
            }

            @Override
            public boolean loadMediaInfo() {
                Playable playable = controller.getMedia();
                if(playable == null) {
                    currentMedia = null;
                    return true;
                } else if(playable instanceof  FeedMedia) {
                    currentMedia = (FeedMedia) playable;
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onAwaitingVideoSurface() {
            }

            @Override
            public void onServiceQueried() {
            }

            @Override
            public void onShutdownNotification() {
            }

            @Override
            public void onPlaybackEnd() {
                currentMedia = null;
            }

            @Override
            public void onPlaybackSpeedChange() {
            }

            @Override
            protected void setScreenOn(boolean enable) {
            }
        };
    }

    public PlaybackSonicTest() {
        super(MainActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        PodDBAdapter.deleteDatabase();

        context = getInstrumentation().getTargetContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .clear()
                .putBoolean(UserPreferences.PREF_UNPAUSE_ON_HEADSET_RECONNECT, false)
                .putBoolean(UserPreferences.PREF_PAUSE_ON_HEADSET_DISCONNECT, false)
                .putBoolean(UserPreferences.PREF_SONIC, true)
                .commit();

        controller = createController(getActivity());
        controller.init();

        solo = new Solo(getInstrumentation(), getActivity());

        uiTestUtils = new UITestUtils(context);
        uiTestUtils.setup();

        // create database
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @Override
    public void tearDown() throws Exception {
        controller.release();
        solo.finishOpenedActivities();
        uiTestUtils.tearDown();

        // shut down playback service
        skipEpisode();
        context.sendBroadcast(new Intent(PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE));

        super.tearDown();
    }

    private void openNavDrawer() {
        solo.clickOnScreen(50, 50);
    }

    private void setContinuousPlaybackPreference(boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(UserPreferences.PREF_FOLLOW_QUEUE, value).commit();
    }

    private void skipEpisode() {
        Intent skipIntent = new Intent(PlaybackService.ACTION_SKIP_CURRENT_EPISODE);
        context.sendBroadcast(skipIntent);
    }

    private void startLocalPlayback() {
        openNavDrawer();
        // if we try to just click on plain old text then
        // we might wind up clicking on the fragment title and not
        // the drawer element like we want.
        ListView drawerView = (ListView)solo.getView(R.id.nav_list);
        // this should be 'Episodes'
        View targetView = drawerView.getChildAt(EPISODES_DRAWER_LIST_INDEX);
        solo.waitForView(targetView);
        solo.clickOnView(targetView);
        solo.waitForText(solo.getString(R.string.all_episodes_short_label));
        solo.clickOnText(solo.getString(R.string.all_episodes_short_label));

        final List<FeedItem> episodes = DBReader.getRecentlyPublishedEpisodes(10);
        assertTrue(solo.waitForView(solo.getView(R.id.butSecondaryAction)));

        solo.clickOnView(solo.getView(R.id.butSecondaryAction));
        long mediaId = episodes.get(0).getMedia().getId();
        boolean playing = solo.waitForCondition(() -> {
            if (currentMedia != null) {
                return currentMedia.getId() == mediaId;
            } else {
                return false;
            }
        }, Timeout.getSmallTimeout());
        assertTrue(playing);
    }

    private void startLocalPlaybackFromQueue() {
        openNavDrawer();

        // if we try to just click on plain old text then
        // we might wind up clicking on the fragment title and not
        // the drawer element like we want.
        ListView drawerView = (ListView)solo.getView(R.id.nav_list);
        // this should be 'Queue'
        View targetView = drawerView.getChildAt(QUEUE_DRAWER_LIST_INDEX);
        solo.waitForView(targetView);
        solo.clickOnView(targetView);

        assertTrue(solo.waitForView(solo.getView(R.id.butSecondaryAction)));
        final List<FeedItem> queue = DBReader.getQueue();
        solo.clickOnImageButton(1);
        assertTrue(solo.waitForView(solo.getView(R.id.butPlay)));
        long mediaId = queue.get(0).getMedia().getId();
        boolean playing = solo.waitForCondition(() -> {
            if(currentMedia != null) {
                return currentMedia.getId() == mediaId;
            } else {
                return false;
            }
        }, Timeout.getSmallTimeout());
        assertTrue(playing);
    }

    public void testStartLocal() throws Exception {
        uiTestUtils.addLocalFeedData(true);
        DBWriter.clearQueue().get();
        startLocalPlayback();
    }

    public void testContinousPlaybackOffSingleEpisode() throws Exception {
        setContinuousPlaybackPreference(false);
        uiTestUtils.addLocalFeedData(true);
        DBWriter.clearQueue().get();
        startLocalPlayback();
    }

    @FlakyTest(tolerance = 3)
    public void testContinousPlaybackOffMultipleEpisodes() throws Exception {
        setContinuousPlaybackPreference(false);
        uiTestUtils.addLocalFeedData(true);
        List<FeedItem> queue = DBReader.getQueue();
        final FeedItem first = queue.get(0);

        startLocalPlaybackFromQueue();
        boolean stopped = solo.waitForCondition(() -> {
            if (currentMedia != null) {
                return currentMedia.getId() != first.getMedia().getId();
            } else {
                return false;
            }
        }, Timeout.getSmallTimeout());
        assertTrue(stopped);
        Thread.sleep(1000);
        PlayerStatus status = controller.getStatus();
        assertFalse(status.equals(PlayerStatus.PLAYING));
    }

    @FlakyTest(tolerance = 3)
    public void testContinuousPlaybackOnMultipleEpisodes() throws Exception {
        setContinuousPlaybackPreference(true);
        uiTestUtils.addLocalFeedData(true);
        List<FeedItem> queue = DBReader.getQueue();
        final FeedItem first = queue.get(0);
        final FeedItem second = queue.get(1);

        startLocalPlaybackFromQueue();
        boolean firstPlaying = solo.waitForCondition(() -> {
            if (currentMedia != null) {
                return currentMedia.getId() == first.getMedia().getId();
            } else {
                return false;
            }
        }, Timeout.getSmallTimeout());
        assertTrue(firstPlaying);
        boolean secondPlaying = solo.waitForCondition(() -> {
            if (currentMedia != null) {
                return currentMedia.getId() == second.getMedia().getId();
            } else {
                return false;
            }
        }, Timeout.getLargeTimeout());
        assertTrue(secondPlaying);
    }

    /**
     * Check if an episode can be played twice without problems.
     */
    private void replayEpisodeCheck(boolean followQueue) throws Exception {
        setContinuousPlaybackPreference(followQueue);
        uiTestUtils.addLocalFeedData(true);
        DBWriter.clearQueue().get();
        final List<FeedItem> episodes = DBReader.getRecentlyPublishedEpisodes(10);

        startLocalPlayback();
        long mediaId = episodes.get(0).getMedia().getId();
        boolean startedPlaying = solo.waitForCondition(() -> {
            if (currentMedia != null) {
                return currentMedia.getId() == mediaId;
            } else {
                return false;
            }
        }, Timeout.getSmallTimeout());
        assertTrue(startedPlaying);

        boolean stoppedPlaying = solo.waitForCondition(() -> {
            return currentMedia == null || currentMedia.getId() != mediaId;
        }, Timeout.getLargeTimeout());
        assertTrue(stoppedPlaying);

        startLocalPlayback();
        boolean startedReplay = solo.waitForCondition(() -> {
            if(currentMedia != null) {
                return currentMedia.getId() == mediaId;
            } else {
                return false;
            }
        }, Timeout.getLargeTimeout());
        assertTrue(startedReplay);
    }

    public void testReplayEpisodeContinuousPlaybackOn() throws Exception {
        replayEpisodeCheck(true);
    }

    public void testReplayEpisodeContinuousPlaybackOff() throws Exception {
        replayEpisodeCheck(false);
    }


}
