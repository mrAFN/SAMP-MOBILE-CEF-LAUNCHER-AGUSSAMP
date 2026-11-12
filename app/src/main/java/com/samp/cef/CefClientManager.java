package com.samp.cef;

import android.app.Activity;
import android.util.Log;

/**
 * Jembatan native <-> Java. Semua operasi browser membawa browserId (0..0xFFFE),
 * atau {@link CefJavaManager#BROWSER_ALL} untuk semua browser.
 */
public class CefClientManager {
    private static final String TAG = "SAMP_CEF_CLIENT";
    private final Activity mActivity;
    private CefJavaManager mJavaManager;

    private static volatile CefClientManager sInstance;

    static {
        try {
            System.loadLibrary("SAMPMobileCef");
            Log.i(TAG, "libSAMPMobileCef.so loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "libSAMPMobileCef library link pending: " + e.getMessage());
        }
    }

    public CefClientManager(Activity activity) {
        this.mActivity = activity;
        sInstance = this;
    }

    public static CefClientManager getInstance() {
        return sInstance;
    }

    public void release() {
        if (sInstance == this) {
            sInstance = null;
        }
    }

    public void setJavaManager(CefJavaManager javaManager) {
        this.mJavaManager = javaManager;
    }

    public CefJavaManager getJavaManager() {
        return mJavaManager;
    }

    // ---------------------------------------------------------------- open

    public void openBrowser(int browserId, String url, boolean focused) {
        Log.i(TAG, "openBrowser id=" + browserId + " focused=" + focused + " url=" + url);

        if (url == null || url.isEmpty()) {
            Log.e(TAG, "openBrowser failed: url is null/empty");
            return;
        }
        if (mJavaManager == null) {
            Log.e(TAG, "openBrowser failed: CefJavaManager is not set (call setJavaManager() first)");
            return;
        }
        mJavaManager.openBrowser(browserId, url, focused);
    }

    // dipanggil dari native (JNI): signature (ILjava/lang/String;Z)V
    public static void staticOpenBrowser(int browserId, String url, boolean focused) {
        CefClientManager instance = sInstance;
        if (instance == null) {
            Log.e(TAG, "staticOpenBrowser failed: no CefClientManager instance available yet");
            return;
        }
        instance.openBrowser(browserId, url, focused);
    }

    // ---------------------------------------------------------------- destroy

    public void closeBrowser(int browserId) {
        Log.i(TAG, "closeBrowser id=" + browserId);
        if (mJavaManager == null) {
            Log.e(TAG, "closeBrowser failed: CefJavaManager is not set");
            return;
        }
        mJavaManager.destroyBrowser(browserId);
    }

    // (I)V
    public static void staticCloseBrowser(int browserId) {
        CefClientManager instance = sInstance;
        if (instance == null) {
            Log.e(TAG, "staticCloseBrowser failed: no CefClientManager instance available yet");
            return;
        }
        instance.closeBrowser(browserId);
    }

    // ---------------------------------------------------------------- show / hide

    public void showBrowser(int browserId) {
        Log.i(TAG, "showBrowser id=" + browserId);
        if (mJavaManager == null) {
            Log.e(TAG, "showBrowser failed: CefJavaManager is not set");
            return;
        }
        mJavaManager.showBrowser(browserId);
    }

    // (I)V
    public static void staticShowBrowser(int browserId) {
        CefClientManager instance = sInstance;
        if (instance == null) { Log.e(TAG, "staticShowBrowser failed: no instance"); return; }
        instance.showBrowser(browserId);
    }

    public void hideBrowser(int browserId) {
        if (mJavaManager != null) {
            mJavaManager.hideBrowser(browserId);
        }
    }

    // (I)V
    public static void staticHideBrowser(int browserId) {
        if (sInstance != null) {
            sInstance.hideBrowser(browserId);
        } else {
            Log.w(TAG, "staticHideBrowser: sInstance null");
        }
    }

    // ---------------------------------------------------------------- url / focus

    public void setBrowserUrl(int browserId, String url) {
        Log.i(TAG, "setBrowserUrl id=" + browserId + " url=" + url);
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "setBrowserUrl failed: url is null/empty");
            return;
        }
        if (mJavaManager == null) {
            Log.e(TAG, "setBrowserUrl failed: CefJavaManager is not set");
            return;
        }
        mJavaManager.loadUrl(browserId, url);
    }

    // (ILjava/lang/String;)V
    public static void staticSetBrowserUrl(int browserId, String url) {
        CefClientManager instance = sInstance;
        if (instance == null) { Log.e(TAG, "staticSetBrowserUrl failed: no instance"); return; }
        instance.setBrowserUrl(browserId, url);
    }

    public void setBrowserFocus(int browserId, boolean isFocused) {
        if (mJavaManager != null) {
            mJavaManager.setBrowserFocus(browserId, isFocused);
        }
    }

    // (IZ)V
    public static void staticSetBrowserFocus(int browserId, boolean isFocused) {
        if (sInstance != null) {
            sInstance.setBrowserFocus(browserId, isFocused);
        } else {
            Log.w(TAG, "staticSetBrowserFocus: sInstance null");
        }
    }

    // ---------------------------------------------------------------- server -> html

    public void dispatchServerEvent(int browserId, String eventName, String eventData) {
        if (mJavaManager != null) {
            mJavaManager.dispatchServerEvent(browserId, eventName, eventData);
        }
    }

    // (ILjava/lang/String;Ljava/lang/String;)V
    public static void staticDispatchServerEvent(int browserId, String eventName, String eventData) {
        if (sInstance != null) {
            sInstance.dispatchServerEvent(browserId, eventName, eventData);
        } else {
            Log.w(TAG, "staticDispatchServerEvent: sInstance null");
        }
    }

    // ---------------------------------------------------------------- client -> server

    public void onBrowserInit(int browserId, boolean isSuccess, int errorCode) {
        try {
            nativeOnBrowserInit(browserId, isSuccess, errorCode);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "nativeOnBrowserInit call failed: " + e.getMessage());
        }
    }

    public void sendClientEvent(int browserId, String eventName, String eventDataJson) {
        try {
            nativeSendClientEvent(browserId, eventName, eventDataJson);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "nativeSendClientEvent call failed: " + e.getMessage());
        }
    }

    private native void nativeOnBrowserInit(int browserId, boolean isSuccess, int errorCode);
    private native void nativeSendClientEvent(int browserId, String eventName, String eventDataJson);
}