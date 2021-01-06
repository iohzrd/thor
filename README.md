# Thor
The **Thor** browser is a mobile browser with the focus on decentralized techniques.
It supports the **ipns**, **ipfs** and the **magnet** protocol.

In addition to the enhancement of the protocols is has two more enhancements which focus
on privacy and usability.

The first enhancement is the integration of a **Tor** service which can be used to visit **onion** 
sites and in case of using the application in **incognito** mode, the service will be used to 
protect your privacy in a way.

**Warning** 
The **incognito** mode is not active when using the decentralized protocols
**ipns**, **ipfs** and **magnet**. It only applies to
to the normal protocols like **https** and **http**.

The second enhancement of the browser is the usage of a an **adblocker**.
It based on the data which are coming from **https://pgl.yoyo.org/adservers/**


## IPFS, IPNS
The browser runs an IPFS service in the background, which will be started automatically when
the browser detects the first access to. 

Note, the service itself is configured in **CLIENT** mode, that means that you are only be able
to download data, but not providing the data to others.

Additional information about **ipns** and **ipfs** can be found here **https://ipfs.io/**

## MAGNET
The browser runs the bittorrent service only when a **magnet** URL should be downloaded.

The service is only active during download of the **magnet** URL. In this phase you are **seeding** 
the data also to others users.

## Tor
The TOR service is running from the moment you are starting the application. So this enables
the user to visit **onion** sites, even when they are not in **incognito** mode.

In **incognito** mode, all **http** and **https** requests will be routed via the **Tor** network.

**Warning** 
When you main goal is to focus on privacy, it might be better to use the 
**Tor** browser (https://www.torproject.org/) directly.

## Settings
This application based on the WebKit API (like Chrome, Brave, etc). This section just gives
a brief overview of the settings which have been made for the browser.
This information is probably only useful for people with technical background.


General Browser Settings:
```
WebSettings settings = webView.getSettings();
settings.setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")");

settings.setJavaScriptEnabled(true);
settings.setJavaScriptCanOpenWindowsAutomatically(false);

settings.setSafeBrowsingEnabled(true);
settings.setAllowFileAccessFromFileURLs(false);
settings.setAllowContentAccess(false);
settings.setAllowUniversalAccessFromFileURLs(false);
settings.setAllowFileAccess(false);
settings.setLoadsImagesAutomatically(true);
settings.setBlockNetworkLoads(false);
settings.setBlockNetworkImage(false);
settings.setDomStorageEnabled(true);
settings.setAppCacheEnabled(true);
settings.setCacheMode(WebSettings.LOAD_DEFAULT);
settings.setDatabaseEnabled(true);
settings.setSupportZoom(true);
settings.setBuiltInZoomControls(true);
settings.setDisplayZoomControls(false);
settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
settings.setUseWideViewPort(true);
settings.setLoadWithOverviewMode(true);
settings.setMediaPlaybackRequiresUserGesture(true);
settings.setSupportMultipleWindows(false);
settings.setGeolocationEnabled(false);
```

### Cookies
The application accept all cookies, except third party cookies.

```
CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, false);
```

Background information **https://www.youtube.com/watch?v=fx6MyPeMLEM&t=1355s**


### Incognito Mode
In **incognito** mode, the **Tor** service is active. Moreover **javascript** will be
disabled. The purpose of the changed settings is, to protect your privacy at least a bit. 
(Key words are **Device and Browser Fingerprint**)

```
public static void setIncognitoMode(@NonNull WebView webView, boolean incognito) {
        webView.getSettings().setJavaScriptEnabled(!incognito);
}

```
