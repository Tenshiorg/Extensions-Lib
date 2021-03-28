package io.github.shadow578.tenshi.extensionslib.content.util;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

import io.github.shadow578.tenshi.extensionslib.content.IContentAdapter;
import io.github.shadow578.tenshi.extensionslib.content.IContentCallback;

import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.notNull;

/**
 * service portion of a ActivityAdapter, to register to Tenshi.
 * Just implement {@link #getActivityClass()} and you're good to go
 * <p>
 * ---
 * a ActivityAdapter requires you to implement both a {@link ActivityAdapterService} that you can register with Tenshi
 * and a {@link ActivityAdapterActivity} that contains the main logic of your content adapter.
 * Communication and callbacks are handled automatically between activity, service and Tenshi
 */
public abstract class ActivityAdapterService<T extends ActivityAdapterActivity<?>> extends Service {
    //region extras
    /**
     * action to signal that the callback should be invoked.
     * only valid if internal callback is set, otherwise ignored
     */
    public static final String ACTION_NOTIFY_RESULT = "io.github.shadow578.tenshicontent.util.activityadapter.ActivityAdapterService.NOTIFY_RESULT";

    /**
     * stream url to pass to callback, string
     */
    public static final String EXTRA_RESULT_STREAM_URL = "streamUrl";

    /**
     * persistent storage to pass to callback, string
     */
    public static final String EXTRA_RESULT_PERSISTENT_STORAGE = "persistentStorage";

    /**
     * unique name to use to get the callback, string
     */
    public static final String EXTRA_RESULT_UNIQUE_NAME = "uniqueName";
    //endregion

    /**
     * internal callback references
     */
    private final HashMap<String, IContentCallback> callbacks = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new ActivityAdapter();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();
        if (action.equals(ACTION_NOTIFY_RESULT)) {
            // this is NOTIFY_RESULT action
            // get extras for the call
            final String uniqueName = intent.getStringExtra(EXTRA_RESULT_UNIQUE_NAME);
            final String streamUrl = intent.getStringExtra(EXTRA_RESULT_STREAM_URL);
            String persistentStor = intent.getStringExtra(EXTRA_RESULT_PERSISTENT_STORAGE);
            if (persistentStor == null)
                persistentStor = "";

            // find the callback for this un
            // also remove it from the map so we dont call it twice
            final IContentCallback callback = callbacks.get(uniqueName);
            callbacks.remove(uniqueName);

            // invoke the callback
            try {
                if (notNull(callback))
                    callback.streamUriResult(streamUrl, persistentStor);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * get the activity adapter activity to launch from this service
     *
     * @return the activity class
     */
    @NonNull
    protected abstract Class<T> getActivityClass();

    /**
     * get the unique names included in this adapter
     *
     * @return the unique names in this adapter
     */
    @NonNull
    protected abstract String[] getUniqueNames();

    /**
     * get the display name for a given unique name in this adapter
     *
     * @param uniqueName the unique name to get the display name of
     * @return the display name
     */
    @NonNull
    protected abstract String getDisplayName(@NonNull String uniqueName);

    /**
     * adapter class for a ActivityAdapterActivity
     * just opens a {@link ActivityAdapterActivity} with required extras and add a entry in {@link #callbacks}
     */
    private class ActivityAdapter extends IContentAdapter.Stub {
        /**
         * get the unique names included in this adapter
         *
         * @return the unique names in this adapter
         */
        @Override
        public String[] getUniqueNames() {
            return ActivityAdapterService.this.getUniqueNames();
        }

        /**
         * get the display name for a given unique name in this adapter
         *
         * @param uniqueName the unique name
         * @return the display name
         */
        @Override
        public String getDisplayName(String uniqueName) {
            if (notNull(uniqueName))
                return ActivityAdapterService.this.getDisplayName(uniqueName);
            return uniqueName;
        }

        /**
         * query a video stream URI for a anime and episode.
         * if this anime is not found or no uri can be found for some other reason, return null
         *
         * @param uniqueName       the unique name of the adapter to query the uri from
         * @param malID            the anime's id on MAL
         * @param enTitle          the english title of the anime (from MAL)
         * @param jpTitle          the japanese title of the anime (from MAL)
         * @param episode          the episode number to get the stream url of
         * @param peristentStorage persistent storage
         * @param cb               callback that is called when the stream uri was found
         */
        @Override
        public void requestStreamUri(String uniqueName, int malID, String enTitle, String jpTitle, int episode, String peristentStorage, IContentCallback cb) {
            // set callback
            callbacks.remove(uniqueName);
            callbacks.put(uniqueName, cb);

            // start activity
            final Intent i = new Intent(getApplicationContext(), getActivityClass());
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            i.putExtra(ActivityAdapterActivity.EXTRA_MAL_ID, malID);
            i.putExtra(ActivityAdapterActivity.EXTRA_ANIME_TITLE_EN, enTitle);
            i.putExtra(ActivityAdapterActivity.EXTRA_ANIME_TITLE_JP, jpTitle);
            i.putExtra(ActivityAdapterActivity.EXTRA_TARGET_EPISODE, episode);
            i.putExtra(ActivityAdapterActivity.EXTRA_PERSISTENT_STORAGE, peristentStorage);
            i.putExtra(ActivityAdapterActivity.EXTRA_UNIQUE_NAME, uniqueName);
            getApplicationContext().startActivity(i);
        }
    }
}

