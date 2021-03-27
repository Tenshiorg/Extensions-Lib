package io.github.shadow578.tenshi.extensionslib.content;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.shadow578.tenshi.extensionslib.content.Constants.ACTION_TENSHI_CONTENT;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.CATEGORY_TENSHI_CONTENT;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.META_ADAPTER_API_VERSION;
import static io.github.shadow578.tenshi.extensionslib.content.Constants.TARGET_API_VERSION;
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
    private final ArrayList<ContentAdapter> contentAdapters = new ArrayList<>();

    /**
     * persistent storage provider
     */
    @Nullable
    private final IPersistentStorageProvider persistentStorageProvider;

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
     * how many content adapters are available and bound?
     *
     * @return the number of content adapters
     */
    public int getAdapterCount() {
        return contentAdapters.size();
    }

    /**
     * get a read- only list of all discovered adapters
     *
     * @return the list of adapters
     */
    @NonNull
    public List<ContentAdapter> getAdapters() {
        return Collections.unmodifiableList(contentAdapters);
    }

    /**
     * get a adapter by unique name
     *
     * @param uniqueName the unique name of the adapter to get
     * @return the adapter found, or null if no adapter matched the name
     */
    @Nullable
    public ContentAdapter getAdapter(@NonNull String uniqueName) {
        for (ContentAdapter ca : contentAdapters)
            if (ca.getUniqueName().equals(uniqueName))
                return ca;

        return null;
    }

    /**
     * unbind all services.
     * call before closing the application
     */
    public void unbindAll() {
        for (ContentAdapter ca : contentAdapters)
            ca.unbind(ctx);
    }

    //region discovery

    /**
     * discover and bind to all found content adapters
     *
     * @param autoBind automatically bind all found adapters?
     */
    public void discoverContentAdapters(boolean autoBind) {
        // get the package manager
        final PackageManager pm = ctx.getPackageManager();

        // prepare intent for query
        final Intent contentAdapterQuery = new Intent(ACTION_TENSHI_CONTENT);
        contentAdapterQuery.addCategory(CATEGORY_TENSHI_CONTENT);

        // query all possible adapter services
        final List<ResolveInfo> resolvedAdapters = pm.queryIntentServices(contentAdapterQuery, PackageManager.MATCH_ALL);

        // add all found services to list of content adapters
        for (ResolveInfo resolvedAdapter : resolvedAdapters)
            if (notNull(resolvedAdapter.serviceInfo) && resolvedAdapter.serviceInfo.exported) {
                // query metadata
                final ServiceInfo serviceWithMeta = getWithFlags(pm, resolvedAdapter.serviceInfo, PackageManager.GET_META_DATA);

                if (notNull(serviceWithMeta)
                        && shouldCreateAdapter(serviceWithMeta))
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
     * should we attempt to bind the service to a content adapter?
     * Check META_ADAPTER_API_VERSION matches
     *
     * @param adapterService the service to bind
     * @return should we bind the service?
     */
    private boolean shouldCreateAdapter(@NonNull ServiceInfo adapterService) {
        // get metadata
        final Bundle meta = adapterService.metaData;

        // we have not metadata, dont bind
        if (isNull(meta))
            return false;

        // we have meta, get META_ADAPTER_API_VERSION
        int apiVer = meta.getInt(META_ADAPTER_API_VERSION, -1);

        // only bind if api requirement is met
        if (apiVer >= TARGET_API_VERSION)
            return true;
        else {
            Log.w("TenshiCP", fmt("Content adapter %s is outdated (found: %d ; target: %d)", adapterService.name, apiVer, TARGET_API_VERSION));
            return false;
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
        //Log.i("TenshiCP", fmt("Binding Content Adapter %s", adapterService.name));

        // create ContentAdapter instance
        final ContentAdapter adapter = ContentAdapter.fromServiceInfo(adapterService, persistentStorageProvider);

        // abort if adapter is null (instantiation failed)
        // or anothe adapter with the same unique name already exists
        if (isNull(adapter) || notNull(getAdapter(adapter.getUniqueName())))
            return;

        // add to the list of all adapters
        contentAdapters.add(adapter);

        // bind the adapter
        if (bind)
            adapter.bind(ctx);
    }
    //endregion
}

