package io.github.shadow578.tenshi.extensionslib.content;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.shadow578.tenshi.extensionslib.lang.Consumer;

import static io.github.shadow578.tenshi.extensionslib.content.Constants.ACTION_TENSHI_CONTENT_ADAPTER;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.CATEGORY_TENSHI_CONTENT_ADAPTER;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.META_API_VERSION;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.TARGET_API_VERSION;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.async;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.elvisEmpty;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.fmt;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.isNull;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.notNull;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.nullOrEmpty;

/**
 * Handles binding to ITenshiContentAdapter services and provides a async wrapper to the service
 */
public class ContentAdapter implements ServiceConnection {
    /**
     * persistent storage provider
     */
    @Nullable
    private final ContentAdapterManager.IPersistentStorageProvider persistentStorageProvider;

    /**
     * the bound service info
     */
    @NonNull
    private final ServiceInfo service;

    /**
     * api version of this adapter.
     */
    private final int apiVersion;

    /**
     * the AIDL service connection. null if not yet connected or disconnected
     */
    @Nullable
    private IContentAdapter adapter;

    /**
     * lock for adapter
     */
    private final Object ADAPTER_LOCK = new Object();

    /**
     * flag to disconnect if the service disconnected.
     * Note that this flag only indicates that the service disconnected, not that the service connect in the first place.
     * for that, check adapter is not null
     */
    private boolean didDisconnect = false;

    private ContentAdapter(@Nullable ContentAdapterManager.IPersistentStorageProvider psProvider,
                           @NonNull ServiceInfo svc,
                           int apiVer) {
        persistentStorageProvider = psProvider;
        service = svc;
        apiVersion = apiVer;
    }


    /**
     * create a new Content Adapter from a given service that has the {@link Constants#ACTION_TENSHI_CONTENT_ADAPTER}
     * Requires the service info to have metadata
     *
     * @param svc        the service to create the adapter from
     * @param psProvider persistent storage provider to use
     * @return the adapter instance, or null if creation failed (eg. service binding failed, or api version is out of date)
     */
    @Nullable
    public static ContentAdapter fromServiceInfo(@NonNull ServiceInfo svc,
                                                 @Nullable ContentAdapterManager.IPersistentStorageProvider psProvider) {
        // get metadata
        final Bundle meta = svc.metaData;

        // we have no metadata, dont bind
        if (isNull(meta))
            return null;

        // get api version from meta
        final int apiVersion = meta.getInt(META_API_VERSION, -1);

        // only create if api version requirement is met
        if (apiVersion < TARGET_API_VERSION) {
            Log.w("TenshiCP", fmt("Content adapter %s is outdated (found: %d ; target: %d)", svc.name, apiVersion, TARGET_API_VERSION));
            return null;
        }

        // create the content adapter instance it
        return new ContentAdapter(psProvider, svc, apiVersion);
    }

    /**
     * initialize the adapter and create wrappers for all available unique names
     *
     * @param ctx       context to bind the service in. binding is required to create the wrappers
     * @param keepBound should the adapter stay bound?
     * @return was init successful?
     */
    public List<ContentAdapterWrapper> initWrappers(@NonNull Context ctx, boolean keepBound) {
        // bind the adapter
        bind(ctx);

        try {
            // get all unique names
            final String[] uniqueNames = adapter.getUniqueNames();
            if (isNull(uniqueNames) || uniqueNames.length <= 0)
                return null;

            // init a wrapper for every unique name
            ArrayList<ContentAdapterWrapper> wrappers = new ArrayList<>();
            for (String unName : uniqueNames) {
                // skip if no or empty unique name
                if (nullOrEmpty(unName)) {
                    Log.e("TenshiCP", fmt("service %s does not have a unique name\n" +
                            "if you're developing this adapter, make sure your adapter implements getUniqueName() correctly", service.name));
                    continue;
                }

                // get the display name for this unique name
                final String diName = elvisEmpty(adapter.getDisplayName(unName), unName);

                // log a warning if no display name found
                // but fallback to the unique name)
                if (unName.equalsIgnoreCase(diName))
                    Log.w("TenshiCP", fmt("content adapter %s does not define a display name (or it's equal to the unique name)! \n " +
                            "If you're developing this adapter, please consider implementing getDisplayName() in your adapter", unName));

                // create the wrapper and add it
                wrappers.add(new ContentAdapterWrapper(this, unName, diName));
            }

            return wrappers;
        } catch (RemoteException ex) {
            Log.e("TenshiCP", "exception in bind: " + ex.toString());
            ex.printStackTrace();
            return null;
        } finally {
            // unbind the service unless keepBound is set
            if (!keepBound)
                unbind(ctx);
        }
    }

    /**
     * bind the service of this content adapter. do this before calling {@link #requestStreamUri(String, int, String, String, int, Consumer)}
     *
     * @param ctx the context to bind from
     */
    public void bind(@NonNull Context ctx) {
        ctx.bindService(getServiceIntent(), this, Context.BIND_AUTO_CREATE);
    }

    /**
     * unbind the service of this content adatper. If not bound, nothing will happen
     *
     * @param ctx the context to unbind from
     */
    public void unbind(@NonNull Context ctx) {
        ctx.stopService(getServiceIntent());
    }

    /**
     * get the api / aidl version of this adapter
     *
     * @return the api / aidl version of this adapter
     */
    public int getApiVersion() {
        return apiVersion;
    }

    /**
     * get a intent for the service
     *
     * @return the intent for the service
     */
    private Intent getServiceIntent() {
        final Intent i = new Intent(ACTION_TENSHI_CONTENT_ADAPTER);
        i.addCategory(CATEGORY_TENSHI_CONTENT_ADAPTER);
        i.setComponent(new ComponentName(service.packageName, service.name));
        return i;
    }

    //region ServiceConnection
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i("TenshiCP", fmt("Content Adapter Service %s connected", name.getClassName()));
        synchronized (ADAPTER_LOCK) {
            adapter = IContentAdapter.Stub.asInterface(service);
            didDisconnect = false;
            ADAPTER_LOCK.notifyAll();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.w("TenshiCP", fmt("Content Adapter Service %s connected", name.getClassName()));
        synchronized (ADAPTER_LOCK) {
            adapter = null;
            didDisconnect = true;
            ADAPTER_LOCK.notifyAll();
        }
    }
    //endregion

    //region ITenshiContentAdapter wrapper

    /**
     * query a video stream URI for a anime and episode.
     * if this anime is not found or no uri can be found for some other reason, return null
     *
     * @param uniqueName unique name to query from
     * @param malID      the anime's id on MAL
     * @param enTitle    the english title of the anime (from MAL)
     * @param jpTitle    the japanese title of the anime (from MAL)
     * @param episode    the episode number to get the stream url of
     * @param callback   the callback called as soon as the service answered. The result may be null if the service died or answered null.
     */
    public void requestStreamUri(@NonNull String uniqueName, int malID, @NonNull String enTitle, @NonNull String jpTitle, int episode, @NonNull Consumer<String> callback) {
        async(() -> {
            try {
                synchronized (ADAPTER_LOCK) {
                    if (waitUntilServiceConnected() && notNull(adapter)) {
                        // load persistent storage for this adapter
                        final String storageIn = notNull(persistentStorageProvider) ? persistentStorageProvider.getPersistentStorage(uniqueName, malID) : "";

                        // request uri
                        adapter.requestStreamUri(uniqueName, malID, enTitle, jpTitle, episode, storageIn, new IContentCallback.Stub() {
                            @Override
                            public void streamUriResult(String streamUri, String persistentStorage) {
                                // save modified storage
                                final String storageOut = elvisEmpty(persistentStorage, "");
                                if (notNull(persistentStorageProvider) && !storageOut.equals(storageIn))
                                    persistentStorageProvider.setPersistentStorage(uniqueName, malID, storageOut);

                                // run callback on ui thread
                                async(() -> streamUri, callback);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("TenshiCP", e.toString());
                e.printStackTrace();

                // run callback on ui thread
                async(() -> null, callback);
            }
            return null;
        }, ignored -> {
        });
    }

    /**
     * wait forever until the service is connected
     *
     * @return is the service now connected?
     */
    private boolean waitUntilServiceConnected() {
        // check if service disconnected (wont connect again)
        if (didDisconnect)
            return false;

        // check if already connected
        if (notNull(adapter))
            return true;

        // wait for the adapter to change
        try {
            ADAPTER_LOCK.wait();
        } catch (InterruptedException e) {
            // wait failed, not connected
            return false;
        }

        // connected if not disconnected and not null
        return !didDisconnect && notNull(adapter);
    }
    //endregion
}

