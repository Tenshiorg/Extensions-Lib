package io.github.shadow578.tenshi.extensionslib.content;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.github.shadow578.tenshi.extensionslib.lang.Consumer;

import static io.github.shadow578.tenshi.extensionslib.content.Constants.ACTION_TENSHI_CONTENT_ADAPTER;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.CATEGORY_TENSHI_CONTENT_ADAPTER;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.async;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.fmt;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.isNull;
import static io.github.shadow578.tenshi.extensionslib.lang.LanguageUtil.notNull;

/**
 * Discovers and Manages {@link ContentAdapter} connections
 */
public class ContentAdapterManager {
    /**
     * allows content adapters to load and save storage between calls
     */
    public interface IPersistentStorageProvider {
        /**
         * get the persistent storage value for a content adapter and anime.
         * Persistent storage is unique for each adapter unique name AND anime id.
         *
         * @param uniqueName the unique name of the content provider
         * @param animeId    the anime id to get the persistent storage for
         * @return the persistent storage value. this cannot be null. if no persistent storage exists, a empty string should be returned.
         */
        @NonNull
        String getPersistentStorage(@NonNull String uniqueName, int animeId);

        /**
         * set the value of the persistent storage for a content adapter and anime
         * Persistent storage is unique for each adapter unique name AND anime id.
         *
         * @param uniqueName        the unique name of the content provider
         * @param animeId           the anime id to set the persistent storage of
         * @param persistentStorage the value to set
         */
        void setPersistentStorage(@NonNull String uniqueName, int animeId, @NonNull String persistentStorage);
    }

    /**
     * the context to work in
     */
    @NonNull
    private final Context ctx;

    /**
     * a list of all adapters
     */
    @NonNull
    private final ArrayList<ContentAdapterWrapper> contentAdapters = new ArrayList<>();

    /**
     * persistent storage provider
     */
    @Nullable
    private final IPersistentStorageProvider persistentStorageProvider;

    /**
     * contains all callbacks for when discovery finished
     */
    private final ArrayList<Consumer<ContentAdapterManager>> discoveryFinishedCallbacks = new ArrayList<>();

    /**
     * did adapter discovery already finish?
     */
    private boolean discoveryFinished = false;

    /**
     * initialize the content adapter manager
     *
     * @param c          the context to work in
     * @param psProvider persistent storage provider, to allow adapters to load and save values between invocations
     */
    public ContentAdapterManager(@NonNull Context c, @Nullable IPersistentStorageProvider psProvider) {
        ctx = c;
        persistentStorageProvider = psProvider;
    }

    /**
     * discover and bind to all found content adapters.
     * Runs on a background thread. to get notified when discovery finished, use {@link #addOnDiscoveryEndCallback(Consumer)}
     *
     * @param autoBind automatically bind all found adapters?
     */
    public void discoverAndInit(boolean autoBind) {
        // unset flag first
        discoveryFinished = false;

        // run in background thread
        async(() -> {
            // run the discovery
            synchronized (contentAdapters) {
                discoverContentAdapters(autoBind);
            }
            return null;
        }, p -> {
            // call the callbacks and set finish discovery flag
            discoveryFinished = true;
            synchronized (discoveryFinishedCallbacks) {
                final Iterator<Consumer<ContentAdapterManager>> callbacksIterator = discoveryFinishedCallbacks.iterator();
                while (callbacksIterator.hasNext()) {
                    // call callback
                    final Consumer<ContentAdapterManager> cb = callbacksIterator.next();
                    if (notNull(cb))
                        cb.invoke(this);

                    // remove from list
                    callbacksIterator.remove();
                }
            }
        });
    }

    /**
     * add a callback for when adapter discovery ended.
     * If discovery already ended, the callback is called immediately.
     * The callback is called on the main thread.
     *
     * @param callback the callback to call when discovery finished.
     */
    public void addOnDiscoveryEndCallback(@NonNull Consumer<ContentAdapterManager> callback) {
        // if already finished, call directly
        if (discoveryFinished) {
            callback.invoke(this);
            return;
        }

        // otherwise add to callbacks list
        synchronized (discoveryFinishedCallbacks) {
            discoveryFinishedCallbacks.add(callback);
        }
    }

    /**
     * get a read- only list of all discovered adapters
     *
     * @return the list of adapters
     */
    @NonNull
    public List<ContentAdapterWrapper> getAdapters() {
        synchronized (contentAdapters) {
            return Collections.unmodifiableList(contentAdapters);
        }
    }

    /**
     * get a adapter by unique name
     *
     * @param uniqueName the unique name of the adapter to get
     * @return the adapter found, or null if no adapter matched the name
     */
    @Nullable
    public ContentAdapterWrapper getAdapter(@NonNull String uniqueName) {
        synchronized (contentAdapters) {
            for (ContentAdapterWrapper ca : contentAdapters)
                if (ca.getUniqueName().equals(uniqueName))
                    return ca;

            return null;
        }
    }

    /**
     * get a adapter by unique name, or the first possible adapter.
     * if no adapters are available still returns null.
     *
     * @param uniqueName the unique name of the adapter to get
     * @return the adapter found or the default adapter, or null if no adapters are available
     */
    @Nullable
    public ContentAdapterWrapper getAdapterOrDefault(@NonNull String uniqueName) {
        synchronized (contentAdapters) {
            // try to find content adapter
            final ContentAdapterWrapper ca = getAdapter(uniqueName);
            if (notNull(ca))
                return ca;

            // get default adapter if possible
            if (getAdapterCount() > 0)
                return contentAdapters.get(0);
            else
                return null;
        }
    }

    /**
     * how many content adapters are available and bound?
     *
     * @return the number of content adapters
     */
    public int getAdapterCount() {
        synchronized (contentAdapters) {
            return contentAdapters.size();
        }
    }

    /**
     * unbind all services.
     * call before closing the application
     */
    public void unbindAll() {
        synchronized (contentAdapters) {
            for (ContentAdapterWrapper ca : contentAdapters)
                ca.unbind(ctx);
        }
    }

    //region discovery

    /**
     * discover and bind to all found content adapters
     *
     * @param autoBind automatically bind all found adapters?
     */
    private void discoverContentAdapters(boolean autoBind) {
        // get the package manager
        final PackageManager pm = ctx.getPackageManager();

        // prepare intent for query
        final Intent contentAdapterQuery = new Intent(ACTION_TENSHI_CONTENT_ADAPTER);
        contentAdapterQuery.addCategory(CATEGORY_TENSHI_CONTENT_ADAPTER);

        // query all possible adapter services
        final List<ResolveInfo> resolvedAdapters = pm.queryIntentServices(contentAdapterQuery, PackageManager.MATCH_ALL);

        // add all found services to list of content adapters
        for (ResolveInfo resolvedAdapter : resolvedAdapters)
            if (notNull(resolvedAdapter.serviceInfo) && resolvedAdapter.serviceInfo.exported) {
                // query metadata
                final ServiceInfo serviceWithMeta = getWithFlags(pm, resolvedAdapter.serviceInfo, PackageManager.GET_META_DATA);

                if (notNull(serviceWithMeta))
                    createAndAddAdapter(serviceWithMeta, autoBind);
            }
    }

    /**
     * get the service with the given flags
     *
     * @param pm    the package manager to use
     * @param svc   the service to get
     * @param flags flags for getServiceInfo();
     * @return the service, or null if failed
     */
    @Nullable
    private ServiceInfo getWithFlags(@NonNull PackageManager pm, @NonNull ServiceInfo svc, @SuppressWarnings("SameParameterValue") int flags) {
        try {
            return pm.getServiceInfo(new ComponentName(svc.packageName, svc.name), flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * create the content adapter instance and add to the list of content adapters.
     * optionally call .bind() on the adapter
     *
     * @param adapterService the service to bind
     * @param bind           should we call .bind() on the adapter?
     */
    private void createAndAddAdapter(@NonNull ServiceInfo adapterService, boolean bind) {
        Log.i("TenshiCP", fmt("Binding Content Adapter %s", adapterService.name));

        // create ContentAdapter instance
        final ContentAdapter adapter = ContentAdapter.fromServiceInfo(adapterService, persistentStorageProvider);

        // stop if adapter is null
        if (isNull(adapter))
            return;

        // get the list of adapter wrappers from the adapter
        final List<ContentAdapterWrapper> wrappers = adapter.initWrappers(ctx, bind);

        // stop and unbind if wrapper initialization failed
        if (isNull(wrappers) || wrappers.isEmpty()) {
            adapter.unbind(ctx);
            return;
        }

        // add each wrapper to the global list
        // but only if they are unique to the list
        for (ContentAdapterWrapper w : wrappers) {
            if (notNull(getAdapter(w.getUniqueName()))) {
                Log.w("TenshiCP", fmt("adapter %s is duplicated, not adding it!", w.getUniqueName()));
                continue;
            }

            // add to list
            contentAdapters.add(w);
        }
    }
    //endregion
}

