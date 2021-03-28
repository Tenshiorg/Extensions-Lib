package io.github.shadow578.tenshi.extensionslib.content.util;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.*;

/**
 * activity portion of a ActivityAdapter, to handle the main logic
 * Implement {@link #getServiceClass()}, call {@link #loadAdapterParams()} in {@link #onCreate(Bundle)} and you're good to go.
 * Once you're finished, call {@link #invokeCallback(String)} or {@link #invokeCallback(String, String)} to report back to Tenshi
 * <p>
 * ---
 * a ActivityAdapter requires you to implement both a {@link ActivityAdapterService} that you can register with Tenshi
 * and a {@link ActivityAdapterActivity} that contains the main logic of your content adapter.
 * Communication and callbacks are handled automatically between activity, service and Tenshi
 */
public abstract class ActivityAdapterActivity<T extends ActivityAdapterService<?>> extends AppCompatActivity {
    //region extras
    /**
     * MAL id to load, int
     */
    public static final String EXTRA_MAL_ID = "malID";

    /**
     * english anime title, string
     */
    public static final String EXTRA_ANIME_TITLE_EN = "titleEN";

    /**
     * japanese anime title, string
     */
    public static final String EXTRA_ANIME_TITLE_JP = "titleJP";

    /**
     * episode to watch, int
     */
    public static final String EXTRA_TARGET_EPISODE = "episode";

    /**
     * persistent storage, string
     */
    public static final String EXTRA_PERSISTENT_STORAGE = "persistentStorage";

    /**
     * unique name, string
     */
    public static final String EXTRA_UNIQUE_NAME = "uniqueName";
    //endregion

    //region call params
    /**
     * the anime's id on MAL
     */
    protected int malID;

    /**
     * the english title of the anime (from MAL)
     */
    protected String enTitle;

    /**
     * the japanese title of the anime (from MAL)
     */
    protected String jpTitle;

    /**
     * the episode number to get the stream url of
     */
    protected int episode;

    /**
     * persistent storage for this content adapter.
     * Automatically used in {@link #invokeCallback(String)}
     */
    protected String persistentStorage;

    /**
     * unique name to work under.
     * Automatically used in {@link #invokeCallback(String)}
     */
    protected String uniqueName;
    //endregion

    /**
     * load the call parameter fields from the intent extras.
     * call this in {@link #onCreate(Bundle)}, and finish if returns false
     * <p>
     * loads:
     * {@link #malID}
     * {@link #enTitle}
     * {@link #jpTitle}
     * {@link #episode}
     * {@link #persistentStorage}
     *
     * @return was the load successful? if false, do not continue
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    protected boolean loadAdapterParams() {
        // ensure we have a intent
        final Intent i = getIntent();
        if (isNull(i))
            return false;

        // load data
        malID = i.getIntExtra(EXTRA_MAL_ID, -1);
        enTitle = i.getStringExtra(EXTRA_ANIME_TITLE_EN);
        jpTitle = i.getStringExtra(EXTRA_ANIME_TITLE_JP);
        episode = i.getIntExtra(EXTRA_TARGET_EPISODE, -1);
        persistentStorage = i.getStringExtra(EXTRA_PERSISTENT_STORAGE);
        uniqueName = i.getStringExtra(EXTRA_UNIQUE_NAME);

        // persistent storage is optional, but cannot be null
        persistentStorage = elvis(persistentStorage, "");

        return !nullOrEmpty(uniqueName);
    }

    /**
     * invoke the callback of the content adapter, with automatic persistent storage
     * after this, the activity should call finish()
     *
     * @param streamUrl the stream url for the callback
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    protected void invokeCallback(@Nullable String streamUrl) {
        invokeCallback(streamUrl, elvis(persistentStorage, ""));
    }

    /**
     * invoke the callback of the content adapter
     * after this, the activity should call finish()
     *
     * @param streamUrl   the stream url for the callback
     * @param persStorage persistent storage for the callback
     */
    protected void invokeCallback(@Nullable String streamUrl, @NonNull String persStorage) {
        final Intent i = new Intent(this, getServiceClass());
        i.setAction(ActivityAdapterService.ACTION_NOTIFY_RESULT);
        i.putExtra(ActivityAdapterService.EXTRA_RESULT_STREAM_URL, streamUrl);
        i.putExtra(ActivityAdapterService.EXTRA_RESULT_PERSISTENT_STORAGE, persStorage);
        i.putExtra(ActivityAdapterService.EXTRA_RESULT_UNIQUE_NAME, uniqueName);
        startService(i);
    }

    /**
     * get the activity adapter service to invoke the callback on
     *
     * @return the service class
     */
    @NonNull
    protected abstract Class<T> getServiceClass();
}

