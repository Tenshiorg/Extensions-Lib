package io.github.shadow578.tenshi.extensionslib.content.util;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * a {@link ActivityAdapterActivity} that provides a {@link WebView} and means to inject javascript payloads into the loaded page.
 * Override {@link #addJavascriptInterfaces(WebView)} to add custom interfaces.
 * Otherwise, the setup is the same as with {@link ActivityAdapterActivity}
 */
public abstract class WebViewAdapterActivity<T extends ActivityAdapterService<?>> extends ActivityAdapterActivity<T> {

    /**
     * the webview that is shown
     */
    protected WebView webView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //init the webview and set it as content
        webView = initWebView();
        setContentView(webView);

        // navigate to the initial url
        webView.loadUrl(getInitialUrl());
    }

    /**
     * initialize the webview, ready for js injection
     *
     * @return the initialized webview
     */
    protected WebView initWebView() {
        // create the client that will inject our js payload
        final WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.loadUrl("javascript:(function _js_loader(){ " + getJSLoader() + " })()");
            }
        };

        // create and setup webview
        final WebView webView = new WebView(this);
        webView.setWebViewClient(webViewClient);
        configureWebView(webView, webView.getSettings());

        // add javascript interfaces
        addJavascriptInterfaces(webView);
        return webView;
    }

    /**
     * add javascript interfaces to the webview.
     * By default, {@link JSInterface} is added as 'App' (basic app calls)
     * and {@link JSPayloadIf} is added as 'JSPayloadIf' (payload access)
     *
     * @param webView the webview to add the interfaces to
     */
    protected void addJavascriptInterfaces(@NonNull WebView webView) {
        webView.addJavascriptInterface(new JSPayloadIf(), "JSPayloadIf");
        webView.addJavascriptInterface(new JSInterface(), "App");
    }

    /**
     * configure the webview's settings.
     * by default only enables js
     * @param webView the webview to configure
     * @param settings the webview's settings (same as webView.getSettings())
     */
    @SuppressLint("SetJavaScriptEnabled")
    protected void configureWebView(@SuppressWarnings({"unused", "RedundantSuppression"}) @NonNull WebView webView, @NonNull WebSettings settings)
    {
        // required for javascript interfaces... and like 99% of all pages
        settings.setJavaScriptEnabled(true);
    }

    /**
     * get the initial url to navigate to
     *
     * @return the initial url
     */
    @NonNull
    protected abstract String getInitialUrl();

    /**
     * get the javascript payload.
     * this is loaded and called by the loader returned by {@link #getJSLoader()}.
     * unless you have changed the loader, you can just act like this is inside a script tag (including line breaks and comments)
     *
     * @param url the url we just loaded
     * @return the js payload to inject. empty string if you don't want to inject anything.
     */
    @NonNull
    protected abstract String getJSPayload(@NonNull String url);

    /**
     * load a payload from raw resources
     *
     * @param id the id of the payload to load
     * @return the loaded payload. never null, tho may be empty if the raw file is empty
     * @throws IOException if the id was not found or closing of the resource failed
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    @NonNull
    protected String loadPayloadFromRaw(@SuppressWarnings("SameParameterValue") @RawRes int id) throws IOException {
        // open reader for the resource
        try (BufferedReader rawIn = new BufferedReader(new InputStreamReader(
                getResources().openRawResource(id)))) {

            // read and append line- by- line
            final StringBuilder payload = new StringBuilder();
            String ln;
            while ((ln = rawIn.readLine()) != null)
                payload.append(ln).append("\n");

            // return the payload
            return payload.toString();
        }
    }

    //region JS

    /**
     * get the js loader script. You can get the payload using the {@link JSPayloadIf#getPayloadJs(String)} function
     * the loader is is loaded by first wrapping it into a function, then invoking using {@link WebView#loadUrl(String)}
     * by default, the payload is just wrapped in a eval()
     *
     * @return the js loader
     */
    @NonNull
    protected String getJSLoader() {
        return "eval(JSPayloadIf.getPayloadJs(document.location.href));";
    }

    /**
     * javascript interface to access the payload
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    private class JSPayloadIf {
        /**
         * get the javascript payload for a given url.
         * this should ONLY be used by the loader script!!
         *
         * @param url the url to get the payload for.
         * @return the payload. may be a empty string, but never null
         */
        @JavascriptInterface
        public String getPayloadJs(String url) {
            return getJSPayload(url);
        }
    }

    /**
     * basic methods accessible from javascript.
     * Exposed functions:
     * <li>toast(String)</li>
     * <li>log(String)</li>
     * <li>logE(String)</li>
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    protected class JSInterface {
        /**
         * show a toast from js
         *
         * @param msg the message of the toast
         */
        @JavascriptInterface
        public void toast(String msg) {
            Toast.makeText(WebViewAdapterActivity.this, "" + msg, Toast.LENGTH_SHORT).show();
        }

        /**
         * log a debug message from js
         *
         * @param msg the message to log
         */
        @JavascriptInterface
        public void log(String msg) {
            Log.d("JSInterface", "" + msg);
        }

        /**
         * log a error message from js
         *
         * @param msg the message to log
         */
        @JavascriptInterface
        public void logE(String msg) {
            Log.d("JSInterface", "" + msg);
        }
    }
    //endregion
}
