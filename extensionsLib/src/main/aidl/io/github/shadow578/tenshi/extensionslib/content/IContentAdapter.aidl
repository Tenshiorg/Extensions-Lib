// IContentAdapter.aidl
package io.github.shadow578.tenshi.extensionslib.content;

import io.github.shadow578.tenshi.extensionslib.content.IContentCallback;

/**
* AIDL interface class for Tenshi Content Adapters,
* Version 2 (Keep this in sync with io.github.shadow578.tenshi.extensionslib.content.Constants#API_VERSION)
*/
interface IContentAdapter {
        /**
         * get the unique names included in this adapter
         * @return the unique names in this adapter
         */
        String[] getUniqueNames();

        /**
         * get the display name for a given unique name in this adapter
         * @return the display name
         */
        String getDisplayName(in String uniqueName);

        /**
         * query a video stream URI for a anime and episode.
         * if this anime is not found or no uri can be found for some other reason, return null
         *
         * @param uniqueName the unique name of the adapter to query the uri from
         * @param malID the anime's id on MAL
         * @param enTitle the english title of the anime (from MAL)
         * @param jpTitle the japanese title of the anime (from MAL)
         * @param episode the episode number to get the stream url of
         * @param persistentStorage persistent storage for this content adatper, per anime (return the modified storage in callback). This can be whatever string, tho not null (use json if you need multiple values)
         * @param callback callback that is called when the stream uri was found
         */
        void requestStreamUri(in String uniqueName, in int malID, in String enTitle, in String jpTitle, in int episode, in String peristentStorage, IContentCallback callback);
}