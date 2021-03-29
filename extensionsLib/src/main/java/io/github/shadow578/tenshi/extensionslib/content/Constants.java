package io.github.shadow578.tenshi.extensionslib.content;

/**
 * constants used for ContentAdapters
 */
public final class Constants {
    /**
     * intent action for content adapters
     */
    public static final String ACTION_TENSHI_CONTENT_ADAPTER = "io.github.shadow578.tenshi.content.ADAPTER";

    /**
     * intent category for content adapters
     */
    public static final String CATEGORY_TENSHI_CONTENT_ADAPTER = ACTION_TENSHI_CONTENT_ADAPTER;

    /**
     * metadata key for content adapter version int
     */
    public static final String META_API_VERSION = "io.github.shadow578.tenshi.content.ADAPTER_VERSION";

    /**
     * target for META_API_VERSION.
     * for a service to be bound, it has to have this or a higher version
     */
    public static final int TARGET_API_VERSION = 2;
}
