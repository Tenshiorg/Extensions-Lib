package io.github.shadow578.tenshi.extensionslib.content.util;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.shadow578.tenshi.extensionslib.content.IContentAdapter;
import io.github.shadow578.tenshi.extensionslib.content.IContentCallback;

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
    //endregion

    /**
     * internal callback reference
     */
    @Nullable
    private IContentCallback callback = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new ActivityAdapter();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();
        if (callback != null && action.equals(ACTION_NOTIFY_RESULT)) {
            // this is NOTIFY_RESULT action and we have a callback, call it!
            // get extras for the call
            final String streamUrl = intent.getStringExtra(EXTRA_RESULT_STREAM_URL);
            String persistentStor = intent.getStringExtra(EXTRA_RESULT_PERSISTENT_STORAGE);
            if (persistentStor == null)
                persistentStor = "";

            // invoke the callback
            try {
                callback.streamUriResult(streamUrl, persistentStor);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            // clear the callback so we don't call it another time
            callback = null;
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
     * adapter class for a ActivityAdapterActivity
     * just opens a {@link ActivityAdapterActivity} with required extras and sets {@link #callback}
     */
    private class ActivityAdapter extends IContentAdapter.Stub {
        @Override
        public void requestStreamUri(int malID, String enTitle, String jpTitle, int episode, String peristentStorage, IContentCallback cb) {
            // set callback
            callback = cb;

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
            getApplicationContext().startActivity(i);
        }
    }
}

