package io.github.shadow578.tenshi.extensionslib.content;

import android.content.Context;

import androidx.annotation.NonNull;

import io.github.shadow578.tenshi.extensionslib.lang.Consumer;

/**
 * wrapper for a content adapter with unique name
 */
public class ContentAdapterWrapper {

    /**
     * parent content adapter
     */
    @NonNull
    private final ContentAdapter contentAdapter;

    /**
     * the unique name to use in calls
     */
    @NonNull
    private final String uniqueName;

    /**
     * the display name of this adapter
     */
    @NonNull
    private final String displayName;

    public ContentAdapterWrapper(@NonNull ContentAdapter contentAdapter, @NonNull String uniqueName, @NonNull String displayName) {
        this.contentAdapter = contentAdapter;
        this.uniqueName = uniqueName;
        this.displayName = displayName;
    }

    /**
     * get the unique name of this adapter
     *
     * @return the unique name of this adapter
     */
    @NonNull
    public String getUniqueName() {
        return uniqueName;
    }

    /**
     * get the display name of this adapter
     *
     * @return the display name of this adapter
     */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * get the api / aidl version of this adapter
     *
     * @return the api / aidl version of this adapter
     */
    public int getApiVersion() {
        return contentAdapter.getApiVersion();
    }

    /**
     * bind the service of this content adapter. do this before calling {@link #requestStreamUri(int, String, String, int, Consumer)}
     *
     * @param ctx the context to bind from
     */
    public void bind(@NonNull Context ctx) {
        contentAdapter.bind(ctx);
    }

    /**
     * unbind the service of this content adatper. If not bound, nothing will happen
     *
     * @param ctx the context to unbind from
     */
    public void unbind(@NonNull Context ctx) {
        contentAdapter.unbind(ctx);
    }

    /**
     * query a video stream URI for a anime and episode.
     * if this anime is not found or no uri can be found for some other reason, return null
     *
     * @param malID    the anime's id on MAL
     * @param enTitle  the english title of the anime (from MAL)
     * @param jpTitle  the japanese title of the anime (from MAL)
     * @param episode  the episode number to get the stream url of
     * @param callback the callback called as soon as the service answered. The result may be null if the service died or answered null.
     */
    public void requestStreamUri(int malID, @NonNull String enTitle, @NonNull String jpTitle, int episode, @NonNull Consumer<String> callback) {
        contentAdapter.requestStreamUri(uniqueName, malID, enTitle, jpTitle, episode, callback);
    }
}
