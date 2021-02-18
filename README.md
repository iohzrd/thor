# Thor
The **Thor** browser is a web browser with the focus on decentralized technologies.
It supports the **ipns** and **ipfs** protocol.

In addition to the protocols enhancements it focus on privacy and usability.

**Privacy:**
The **Tor** service is integrated which can be used to visit **onion** 
sites and in case of using the application in **incognito** mode, the service will be used to 
route all connections via the **Tor** network.

**Warning:** 
The **incognito** mode is not active when using the decentralized protocols
**ipns** and **ipfs**. It only applies to to the normal protocols like **https** and **http**.

**Usability:**
To improve the usability of the browser a **Ad-blocker** is integrated.
It based on the information which are coming from **https://pgl.yoyo.org/adservers/**


## General
The basic characteristics of the app are decentralized, respect of personal data,
open source, free of charge, transparent, free of advertising and legally impeccable.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=threads.thor)

## IPFS, IPNS
The browser runs an IPFS service in the background, which will be started automatically when
the browser detects the first access to. 

**Note:** 
The service itself is configured in client mode, that means that you are only be able
to download data, but not providing data to others.

Additional information about **ipns** and **ipfs** can be found here **https://ipfs.io/**

## TOR
The TOR service is running from the moment you are starting the application. This enables
the user to visit **onion** sites, even when you are not in **incognito** mode.

In **incognito** mode, all **http** and **https** requests will be routed via the **Tor** network.

**Warning:** 
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


## Privacy Policy

### Data Protection
<p>As an application provider, we take the protection of all personal data very seriously.
                All personal information is treated confidentially and in accordance with the legal
                requirements,
                regulations, as explained in this privacy policy.
            </p>
            <p>This app is designed so that the user do not have to enter any personal data.
                Never will data collected by us, and especially not passed to third parties.
                The users behaviour is also not analyzed by this application.
            </p>
<p>No information and data is tracked by this application.</p>

### Android Permissions
<p>This section describes briefly why special Android permissions are required.
            </p>
            <ul>
                <li>
                    <h4>Foreground Service</h4>
                    <p>The foreground service permission is required to download content over a
                        longer period of time.
                    </p>
                </li>
            </ul>