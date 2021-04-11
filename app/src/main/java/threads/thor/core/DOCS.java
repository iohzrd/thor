package threads.thor.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Pair;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.DnsAddrResolver;
import io.ipfs.IPFS;
import io.ipfs.format.Node;
import io.ipfs.utils.Link;
import io.libp2p.core.multiformats.Multiaddr;
import threads.LogUtils;
import threads.thor.InitApplication;
import threads.thor.Settings;
import threads.thor.core.books.BOOKS;
import threads.thor.core.books.Bookmark;
import threads.thor.core.pages.PAGES;
import threads.thor.core.pages.Page;
import threads.thor.magic.ContentInfo;
import threads.thor.magic.ContentInfoUtil;
import threads.thor.services.MimeTypeService;
import threads.thor.utils.MimeType;

public class DOCS {

    private static final String INDEX_HTML = "index.html";
    private static final String TAG = DOCS.class.getSimpleName();
    private static final HashSet<Long> threads = new HashSet<>();
    private static final HashSet<Uri> uris = new HashSet<>();
    private static DOCS INSTANCE = null;
    private final IPFS ipfs;
    private final PAGES pages;
    private final BOOKS books;
    private final Hashtable<Uri, Uri> redirects = new Hashtable<>();
    private final Hashtable<String, String> resolves = new Hashtable<>();
    private boolean isRedirectIndex;
    private boolean isRedirectUrl;


    private DOCS(@NonNull Context context) {
        long start = System.currentTimeMillis();
        ipfs = IPFS.getInstance(context);
        pages = PAGES.getInstance(context);
        books = BOOKS.getInstance(context);
        refreshRedirectOptions(context);
        LogUtils.info(InitApplication.TIME_TAG, "DOCS finish [" +
                (System.currentTimeMillis() - start) + "]...");
    }

    public static DOCS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (DOCS.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DOCS(context);
                }
            }
        }
        return INSTANCE;
    }

    private void pageProvider(@NonNull String cid, @NonNull Closeable closeable) {
        ipfs.load(closeable, cid);
    }

    private void pageConnect(@NonNull String pid, @NonNull Closeable closeable) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {

                Page page = pages.getPage(pid);
                boolean connected = false;
                if (page != null) {
                    String address = page.getAddress();
                    if (!address.isEmpty()) {
                        connected = ipfs.swarmConnect(
                                    address.concat(Content.P2P_PATH).concat(page.getPid()), closeable);
                    }
                }
                if (!connected) {
                    connected = ipfs.swarmConnect(Content.P2P_PATH + pid, closeable);
                }

                if (page != null) {
                    Multiaddr info = ipfs.swarmPeer(pid);
                    if (info != null) {
                        String address = info.toString();
                        if (!address.isEmpty() && !address.contains(Content.CIRCUIT)) {
                            if (!Objects.equals(address, page.getAddress())) {
                                pages.setPageAddress(pid, address);
                                pages.resetBootstrap(pid);
                            } else {
                                pages.incrementRating(pid);
                                // success here, same address
                                if (!page.isBootstrap()) {
                                    pages.setBootstrap(pid);
                                }
                            }
                        } else {
                            if (!page.getAddress().isEmpty()) {
                                pages.setPageAddress(pid, "");
                            }
                            if (page.isBootstrap()) {
                                pages.resetBootstrap(pid);
                            }
                        }
                    } else {
                        if (!page.getAddress().isEmpty()) {
                            pages.setPageAddress(pid, "");
                        }
                        if (page.isBootstrap()) {
                            pages.resetBootstrap(pid);
                        }
                    }

                }

                LogUtils.error(TAG, "Connect " + pid + " " + connected);
            } catch (ClosedException ignore) {
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable.getMessage());
            } finally {
                LogUtils.info(TAG, " finish onStart ...");
            }
        });
    }

    public void refreshRedirectOptions(@NonNull Context context) {
        isRedirectIndex = Settings.isRedirectIndexEnabled(context);
        isRedirectUrl = Settings.isRedirectUrlEnabled(context);
    }

    public int numUris() {
        synchronized (TAG.intern()) {
            return uris.size();
        }
    }

    public void detachUri(@NonNull Uri uri) {
        synchronized (TAG.intern()) {
            uris.remove(uri);
        }
    }

    public void attachUri(@NonNull Uri uri) {
        synchronized (TAG.intern()) {
            uris.add(uri);
        }
    }

    public void attachThread(@NonNull Long thread) {
        synchronized (TAG.intern()) {
            threads.add(thread);
        }
    }

    public void releaseContent() {
        ipfs.reset();
    }

    public void releaseThreads() {
        synchronized (TAG.intern()) {
            threads.clear();
        }
    }


    public boolean shouldRun(@NonNull Long thread) {
        synchronized (TAG.intern()) {
            return threads.contains(thread);
        }
    }

    @NonNull
    private String getMimeType(@NonNull Context context, @NonNull String cid,
                               @NonNull Closeable closeable) throws ClosedException {

        if (ipfs.isDir(cid, closeable)) {
            return MimeType.DIR_MIME_TYPE;
        }

        return getContentMimeType(context, cid, closeable);
    }

    @NonNull
    private String getContentMimeType(@NonNull Context context, @NonNull String cid,
                                      @NonNull Closeable closeable) throws ClosedException {

        String mimeType = MimeType.OCTET_MIME_TYPE;

        try (InputStream in = ipfs.getLoaderStream(cid, closeable)) {

            ContentInfo info = ContentInfoUtil.getInstance(context).findMatch(in);

            if (info != null) {
                mimeType = info.getMimeType();
            }

        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
        }

        return mimeType;
    }


    @Nullable
    public String getHost(@NonNull Uri uri) {
        try {
            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                return uri.getHost();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }


    @Nullable
    public Node resolvePath(@NonNull Uri uri, @NonNull Closeable closeable) throws
            InvalidNameException, ClosedException, ResolveNameException {
        List<String> paths = uri.getPathSegments();

        String root = getRoot(uri, closeable);
        Objects.requireNonNull(root);

        return ipfs.resolveNode(root, paths, closeable);
    }


    @NonNull
    public String resolveName(@NonNull Uri uri, @NonNull String name, @NonNull Closeable closeable)
            throws ResolveNameException, ClosedException {
        String pid = ipfs.decodeName(name);
        String resolved = resolves.get(pid);
        if (resolved != null) {
            return resolved;
        }

        long sequence = 0L;
        String cid = null;
        Page page = pages.getPage(pid);
        if (page != null) {
            sequence = page.getSequence();
            cid = page.getContent();
        } else {
            page = pages.createPage(pid);
            pages.storePage(page);
        }


        IPFS.ResolvedName resolvedName = ipfs.resolveName(name, sequence, closeable);
        if (resolvedName == null) {

            if (cid != null) {
                resolves.put(pid, cid);
                return cid;
            }

            throw new ResolveNameException(uri.toString());
        }
        resolves.put(pid, resolvedName.getHash());
        pages.setPageContent(pid, resolvedName.getHash());
        pages.setPageSequence(pid, resolvedName.getSequence());
        return resolvedName.getHash();
    }


    public String generateDirectoryHtml(@NonNull Uri uri, @NonNull String root,
                                        @NonNull List<String> paths, @Nullable List<Link> links) {
        String title = root;

        if (!paths.isEmpty()) {
            title = paths.get(paths.size() - 1);
        }


        StringBuilder answer = new StringBuilder("<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=2, user-scalable=yes\">" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">" +
                "<title>" + title + "</title>");

        answer.append("</head><body>");


        answer.append("<div style=\"padding: 16px; word-break:break-word; background-color: #696969; color: white;\">Index of ").append(uri.toString()).append("</div>");

        if (links != null) {
            if (!links.isEmpty()) {
                answer.append("<form><table  width=\"100%\" style=\"border-spacing: 4px;\">");
                for (Link link : links) {

                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(uri.getScheme())
                            .authority(uri.getAuthority());
                    for (String path : paths) {
                        builder.appendPath(path);
                    }
                    builder.appendPath(link.getName());
                    builder.appendQueryParameter("download", "0");
                    Uri linkUri = builder.build();


                    answer.append("<tr>");
                    answer.append("<td>");
                    if (!link.isDirectory()) {
                        answer.append(MimeTypeService.getSvgResource(link.getName()));
                    } else {
                        answer.append(MimeTypeService.SVG_FOLDER);
                    }
                    answer.append("</td>");

                    answer.append("<td width=\"100%\" style=\"word-break:break-word\">");
                    answer.append("<a href=\"");
                    answer.append(linkUri.toString());
                    answer.append("\">");
                    answer.append(link.getName());
                    answer.append("</a>");
                    answer.append("</td>");

                    answer.append("<td>");
                    answer.append(getFileSize(link.getSize()));
                    answer.append("</td>");


                    answer.append("<td align=\"center\">");
                    String text = "<button style=\"float:none!important;display:inline;\" name=\"download\" value=\"1\" formenctype=\"text/plain\" formmethod=\"get\" type=\"submit\" formaction=\"" +
                            linkUri + "\">" + MimeTypeService.getSvgDownload() + "</button>";
                    answer.append(text);
                    answer.append("</td>");
                    answer.append("</tr>");
                }
                answer.append("</table></form>");
            }

        }
        answer.append("</body></html>");


        return answer.toString();
    }


    private String getFileSize(long size) {

        String fileSize;

        if (size < 1000) {
            fileSize = String.valueOf(size);
            return fileSize.concat(" B");
        } else if (size < 1000 * 1000) {
            fileSize = String.valueOf((double) (size / 1000));
            return fileSize.concat(" KB");
        } else {
            fileSize = String.valueOf((double) (size / (1000 * 1000)));
            return fileSize.concat(" MB");
        }
    }

    public void connectUri(@NonNull Uri uri, @NonNull Closeable closeable) {
        try {
            String host = getHost(uri);
            if (host != null) {
                String pid = ipfs.decodeName(host);
                if (!pid.isEmpty()) {
                    pageConnect(pid, closeable);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    @NonNull
    public WebResourceResponse getResponse(@NonNull Context context, @NonNull Uri uri,
                                           @NonNull String root, @NonNull List<String> paths,
                                           @NonNull Closeable closeable) throws Exception {

        if (paths.isEmpty()) {
            if (ipfs.isDir(root, closeable)) {
                List<Link> links = ipfs.links(root, closeable);
                String answer = generateDirectoryHtml(uri, root, paths, links);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else {
                String mimeType = getContentMimeType(context, root, closeable);
                return getContentResponse(root, mimeType, closeable);
            }


        } else {
            String cid = ipfs.resolve(root, paths, closeable);
            if (cid.isEmpty()) {
                throw new ContentException(uri.toString());
            }
            if (ipfs.isDir(cid, closeable)) {
                List<Link> links = ipfs.links(cid, closeable);
                String answer = generateDirectoryHtml(uri, root, paths, links);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));

            } else {
                String mimeType = getMimeType(context, uri, cid, closeable);
                return getContentResponse(cid, mimeType, closeable);
            }
        }
    }


    @NonNull
    private WebResourceResponse getContentResponse(@NonNull String content, @NonNull String mimeType,
                                                   @NonNull Closeable closeable)
            throws ClosedException {

        try (InputStream in = ipfs.getLoaderStream(content, closeable)) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            Map<String, String> responseHeaders = new HashMap<>();

            return new WebResourceResponse(mimeType, Content.UTF8, 200,
                    "OK", responseHeaders, new BufferedInputStream(in));
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            throw new RuntimeException(throwable);
        }


    }

    @NonNull
    public String getMimeType(@NonNull Context context, @NonNull Uri uri,
                              @NonNull String cid, @NonNull Closeable closeable)
            throws ClosedException {

        List<String> paths = uri.getPathSegments();
        if (!paths.isEmpty()) {
            String name = paths.get(paths.size() - 1);
            String mimeType = MimeTypeService.getMimeType(name);
            if (!mimeType.equals(MimeType.OCTET_MIME_TYPE)) {
                return mimeType;
            } else {
                return getMimeType(context, cid, closeable);
            }
        } else {
            return getMimeType(context, cid, closeable);
        }

    }

    @NonNull
    public String getFileName(@NonNull Uri uri) {

        List<String> paths = uri.getPathSegments();
        if (!paths.isEmpty()) {
            return paths.get(paths.size() - 1);
        } else {
            return "" + uri.getHost();
        }

    }

    @NonNull
    public String getContent(@NonNull Uri uri, @NonNull Closeable closeable)
            throws InvalidNameException, ResolveNameException, ClosedException {

        String host = uri.getHost();
        Objects.requireNonNull(host);

        String root = getRoot(uri, closeable);
        Objects.requireNonNull(root);

        List<String> paths = uri.getPathSegments();
        if (paths.isEmpty()) {
            return root;
        }

        return ipfs.resolve(root, paths, closeable);
    }


    @Nullable
    public String getRoot(@NonNull Uri uri, @NonNull Closeable closeable)
            throws ResolveNameException, InvalidNameException, ClosedException {
        String host = uri.getHost();
        Objects.requireNonNull(host);

        String root;
        if (Objects.equals(uri.getScheme(), Content.IPNS)) {

            if (ipfs.decodeName(host).isEmpty()) {
                throw new InvalidNameException(uri.toString());
            }
            root = resolveName(uri, host, closeable);

        } else {
            if (!ipfs.isValidCID(host)) {
                throw new InvalidNameException(uri.toString());
            }
            root = host;
        }

        return root;

    }

    @NonNull
    public WebResourceResponse getResponse(@NonNull Context context, @NonNull Uri uri,
                                           @NonNull Closeable closeable) throws Exception {


        List<String> paths = uri.getPathSegments();

        String root = getRoot(uri, closeable);
        Objects.requireNonNull(root);

        pageProvider(root, closeable);

        return getResponse(context, uri, root, paths, closeable);

    }


    @NonNull
    public Uri redirectHttp(@NonNull Uri uri) {
        try {
            if (Objects.equals(uri.getScheme(), Content.HTTP)) {
                String host = uri.getHost();
                Objects.requireNonNull(host);
                if (Objects.equals(host, "localhost") || Objects.equals(host, "127.0.0.1")) {
                    List<String> paths = uri.getPathSegments();
                    if (paths.size() >= 2) {
                        String protocol = paths.get(0);
                        String authority = paths.get(1);
                        List<String> subPaths = new ArrayList<>(paths);
                        subPaths.remove(protocol);
                        subPaths.remove(authority);
                        if (ipfs.isValidCID(authority)) {
                            if (Objects.equals(protocol, Content.IPFS)) {
                                Uri.Builder builder = new Uri.Builder();
                                builder.scheme(Content.IPFS)
                                        .authority(authority);

                                for (String path : subPaths) {
                                    builder.appendPath(path);
                                }
                                return builder.build();
                            } else if (Objects.equals(protocol, Content.IPNS)) {
                                Uri.Builder builder = new Uri.Builder();
                                builder.scheme(Content.IPNS)
                                        .authority(authority);

                                for (String path : subPaths) {
                                    builder.appendPath(path);
                                }
                                return builder.build();
                            }
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return uri;
    }


    @NonNull
    public Uri redirectHttps(@NonNull Uri uri) {
        try {
            if (isRedirectUrl && Objects.equals(uri.getScheme(), Content.HTTPS)) {


                List<String> paths = uri.getPathSegments();
                if (paths.size() >= 2) {
                    String protocol = paths.get(0);
                    if (Objects.equals(protocol, Content.IPFS) ||
                            Objects.equals(protocol, Content.IPNS)) {
                        String authority = paths.get(1);
                        List<String> subPaths = new ArrayList<>(paths);
                        subPaths.remove(protocol);
                        subPaths.remove(authority);
                        if (ipfs.isValidCID(authority)) {
                            if (Objects.equals(protocol, Content.IPFS)) {
                                Uri.Builder builder = new Uri.Builder();
                                builder.scheme(Content.IPFS)
                                        .authority(authority);

                                for (String path : subPaths) {
                                    builder.appendPath(path);
                                }
                                return builder.build();
                            } else if (Objects.equals(protocol, Content.IPNS)) {
                                Uri.Builder builder = new Uri.Builder();
                                builder.scheme(Content.IPNS)
                                        .authority(authority);

                                for (String path : subPaths) {
                                    builder.appendPath(path);
                                }
                                return builder.build();
                            }
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return uri;
    }

    @NonNull
    public Pair<Uri, Boolean> redirectDnsLink(@NonNull Uri uri, @NonNull String link,
                                              @NonNull Closeable closeable)
            throws ClosedException, InvalidNameException, ResolveNameException {

        List<String> paths = uri.getPathSegments();
        if (link.startsWith(Content.IPFS_PATH)) {
            String cid = link.replaceFirst(Content.IPFS_PATH, "");
            Uri.Builder builder = new Uri.Builder();
            builder.scheme(Content.IPFS)
                    .authority(cid);
            for (String path : paths) {
                builder.appendPath(path);
            }
            return redirect(builder.build(), cid, paths, closeable);
        } else if (link.startsWith(Content.IPNS_PATH)) {
            String cid = link.replaceFirst(Content.IPNS_PATH, "");
            if (!ipfs.decodeName(cid).isEmpty()) {
                Uri.Builder builder = new Uri.Builder();
                builder.scheme(Content.IPNS)
                        .authority(cid);
                for (String path : paths) {
                    builder.appendPath(path);
                }
                return redirect(builder.build(), cid, paths, closeable);
            } else {
                // is is assume like /ipns/<dns_link> = > therefore <dns_link> is url

                Uri dnsUri = Uri.parse(cid);
                if (dnsUri != null) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(Content.IPNS)
                            .authority(dnsUri.getAuthority());
                    for (String path : paths) {
                        builder.appendPath(path);
                    }
                    return redirectUri(builder.build(), closeable);
                }

            }
        } else {
            // is is assume that links is  <dns_link> is url

            Uri dnsUri = Uri.parse(link);
            if (dnsUri != null) {
                Uri.Builder builder = new Uri.Builder();
                builder.scheme(Content.IPNS)
                        .authority(dnsUri.getAuthority());
                for (String path : paths) {
                    builder.appendPath(path);
                }
                return redirectUri(builder.build(), closeable);
            }
        }
        return Pair.create(uri, false);
    }

    @NonNull
    public Pair<Uri, Boolean> redirectUri(@NonNull Uri uri, @NonNull Closeable closeable)
            throws ResolveNameException, InvalidNameException, ClosedException {


        if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                Objects.equals(uri.getScheme(), Content.IPFS)) {
            List<String> paths = uri.getPathSegments();
            String host = uri.getHost();
            Objects.requireNonNull(host);
            if (!ipfs.isValidCID(host)) {
                String link = DnsAddrResolver.getDNSLink(host);
                if (link.isEmpty()) {
                    // could not resolved, maybe no NETWORK
                    String dnsLink = books.getDnsLink(uri.toString());
                    if (dnsLink == null) {
                        if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                            throw new DOCS.ResolveNameException(uri.toString());
                        } else {
                            throw new DOCS.InvalidNameException(uri.toString());
                        }
                    } else {
                        return redirectDnsLink(uri, dnsLink, closeable);
                    }
                } else {
                    // try to store value
                    books.storeDnsLink(uri.toString(), link);
                    return redirectDnsLink(uri, link, closeable);
                }
            } else {
                String root = getRoot(uri, closeable);
                Objects.requireNonNull(root);
                return redirect(uri, root, paths, closeable);
            }
        }
        return Pair.create(uri, false);
    }

    @NonNull
    private Pair<Uri, Boolean> redirect(@NonNull Uri uri, @NonNull String root,
                                        @NonNull List<String> paths, @NonNull Closeable closeable)
            throws ClosedException {


        if (paths.isEmpty()) {

            if (isRedirectIndex && ipfs.isDir(root, closeable)) {
                boolean exists = ipfs.resolve(root, INDEX_HTML, closeable);

                if (exists) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(uri.getScheme())
                            .authority(uri.getAuthority());
                    for (String path : paths) {
                        builder.appendPath(path);
                    }
                    builder.appendPath(INDEX_HTML);
                    return Pair.create(builder.build(), true);
                }
            }


        } else {

            // check first paths
            // if like this .../ipfs/Qa..../
            // THIS IS A BIG HACK AND SHOULD NOT BE SUPPORTED
            if (paths.size() >= 2) {
                String protocol = paths.get(0);
                if (Objects.equals(protocol, Content.IPFS) ||
                        Objects.equals(protocol, Content.IPNS)) {
                    String authority = paths.get(1);
                    List<String> subPaths = new ArrayList<>(paths);
                    subPaths.remove(protocol);
                    subPaths.remove(authority);
                    if (ipfs.isValidCID(authority)) {
                        if (Objects.equals(protocol, Content.IPFS)) {
                            Uri.Builder builder = new Uri.Builder();
                            builder.scheme(Content.IPFS)
                                    .authority(authority);

                            for (String path : subPaths) {
                                builder.appendPath(path);
                            }
                            return Pair.create(builder.build(), false);
                        } else if (Objects.equals(protocol, Content.IPNS)) {
                            Uri.Builder builder = new Uri.Builder();
                            builder.scheme(Content.IPNS)
                                    .authority(authority);

                            for (String path : subPaths) {
                                builder.appendPath(path);
                            }
                            return Pair.create(builder.build(), false);
                        }
                    }
                }
            }

            if (isRedirectIndex) {
                String cid = ipfs.resolve(root, paths, closeable);

                if (!cid.isEmpty()) {
                    if (ipfs.isDir(cid, closeable)) {
                        boolean exists = ipfs.resolve(cid, INDEX_HTML, closeable);

                        if (exists) {
                            Uri.Builder builder = new Uri.Builder();
                            builder.scheme(uri.getScheme())
                                    .authority(uri.getAuthority());
                            for (String path : paths) {
                                builder.appendPath(path);
                            }
                            builder.appendPath(INDEX_HTML);
                            return Pair.create(builder.build(), true);
                        }
                    }
                }
            }
        }

        return Pair.create(uri, false);
    }

    public void storeRedirect(@NonNull Uri redirectUri, @NonNull Uri uri) {
        redirects.put(redirectUri, uri);
    }

    @NonNull
    public Uri getOriginalUri(@NonNull Uri redirectUri) {
        Uri original = recursiveUri(redirectUri);
        if (original != null) {
            return original;
        }
        return redirectUri;
    }

    @Nullable
    private Uri recursiveUri(@NonNull Uri redirectUri) {
        Uri original = redirects.get(redirectUri);
        if (original != null) {
            Uri recursive = recursiveUri(original);
            if (recursive != null) {
                return recursive;
            } else {
                return original;
            }
        }
        return null;
    }

    public void bootstrap() {

        try {
            ipfs.bootstrap();

            if (ipfs.numSwarmPeers() < IPFS.MIN_PEERS) {
                List<Page> bootstraps = pages.getBootstraps(5);
                List<String> addresses = new ArrayList<>();
                for (Page bootstrap : bootstraps) {
                    String address = bootstrap.getAddress();
                    if (!address.isEmpty()) {
                        addresses.add(address.concat(Content.P2P_PATH).concat(bootstrap.getPid()));
                    }
                }

                if (!addresses.isEmpty()) {
                    List<Callable<Boolean>> tasks = new ArrayList<>();
                    ExecutorService executor = Executors.newFixedThreadPool(addresses.size());
                    for (String address : addresses) {
                        tasks.add(() -> ipfs.swarmConnect(address, IPFS.TIMEOUT_BOOTSTRAP));
                    }
                    List<Future<Boolean>> result = executor.invokeAll(tasks,
                            IPFS.TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                    for (Future<Boolean> future : result) {
                        LogUtils.error(TAG, "Bootstrap done " + future.isDone());
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public void cleanupResolver(@NonNull Uri uri) {

        try {
            String host = getHost(uri);
            if (host != null) {
                String pid = ipfs.decodeName(host);
                if (!pid.isEmpty()) {
                    resolves.remove(pid);
                }
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    public void updateBookmarkTitle(@NonNull Uri uri, @NonNull String title) {
        books.setBookmarkTitle(uri.toString(), title);
    }

    public void updateBookmarkIcon(@NonNull Uri uri, @NonNull Bitmap icon) {
        Bookmark bookmark = books.getBookmark(uri.toString());
        if (bookmark != null) {
            bookmark.setBitmapIcon(icon);
            books.storeBookmark(bookmark);
        }
    }

    public static class ContentException extends Exception {

        public ContentException(@NonNull String name) {
            super("Content not found for " + name);
        }
    }

    public static class ResolveNameException extends Exception {

        public ResolveNameException(@NonNull String name) {
            super("Resolve name failed for " + name);
        }
    }

    public static class InvalidNameException extends Exception {

        public InvalidNameException(@NonNull String name) {
            super("Invalid name detected for " + name);
        }


    }
}
