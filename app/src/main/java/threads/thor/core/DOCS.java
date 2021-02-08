package threads.thor.core;

import android.content.Context;
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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.core.pages.PAGES;
import threads.thor.core.pages.Page;
import threads.thor.ipfs.Closeable;
import threads.thor.ipfs.DnsAddrResolver;
import threads.thor.ipfs.IPFS;
import threads.thor.ipfs.Link;
import threads.thor.ipfs.LinkInfo;
import threads.thor.magic.ContentInfo;
import threads.thor.magic.ContentInfoUtil;
import threads.thor.services.MimeTypeService;
import threads.thor.utils.MimeType;
import threads.thor.work.PageConnectWorker;

public class DOCS {

    private static final String INDEX_HTML = "index.html";
    private static final String TAG = DOCS.class.getSimpleName();
    private static DOCS INSTANCE = null;
    private final IPFS ipfs;
    private final PAGES pages;
    private final ContentInfoUtil util;
    private final Hashtable<Uri, Uri> redirects = new Hashtable<>();
    private final Hashtable<String, String> resolves = new Hashtable<>();

    private DOCS(@NonNull Context context) {
        long timestamp = System.currentTimeMillis();
        ipfs = IPFS.getInstance(context);
        pages = PAGES.getInstance(context);
        util = ContentInfoUtil.getInstance(context);
        LogUtils.error(TAG, "Time : " + (System.currentTimeMillis() - timestamp));
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

    @NonNull
    private String getMimeType(@NonNull String doc, @NonNull Closeable closeable) {

        if (ipfs.isEmptyDir(doc) || ipfs.isDir(doc, closeable)) {
            return MimeType.DIR_MIME_TYPE;
        }

        String mimeType = MimeType.OCTET_MIME_TYPE;
        if (!closeable.isClosed()) {
            try (InputStream in = ipfs.getLoaderStream(doc, closeable, Settings.IPFS_READ_TIMEOUT)) {

                ContentInfo info = util.findMatch(in);

                if (info != null) {
                    mimeType = info.getMimeType();
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
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


    @NonNull
    public String resolveName(@NonNull Uri uri, @NonNull String name,
                              @NonNull Closeable closeable) throws ResolveNameException {
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


    public String generateDirectoryHtml(@NonNull Uri uri, @NonNull String root, List<String> paths, @Nullable List<LinkInfo> links) {
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
                for (LinkInfo linkInfo : links) {

                    String mimeType = MimeType.DIR_MIME_TYPE;
                    if (!linkInfo.isDirectory()) {
                        mimeType = MimeTypeService.getMimeType(linkInfo.getName());
                    }
                    String linkUri = uri + "/" + linkInfo.getName();

                    answer.append("<tr>");

                    answer.append("<td>");
                    answer.append(MimeTypeService.getSvgResource(mimeType));
                    answer.append("</td>");

                    answer.append("<td width=\"100%\" style=\"word-break:break-word\">");
                    answer.append("<a href=\"");
                    answer.append(linkUri);
                    answer.append("\">");
                    answer.append(linkInfo.getName());
                    answer.append("</a>");
                    answer.append("</td>");

                    answer.append("<td>");
                    if (!linkInfo.isDirectory()) {
                        answer.append(getFileSize(linkInfo.getSize()));
                    }
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
            return fileSize.concat("B");
        } else if (size < 1000 * 1000) {
            fileSize = String.valueOf((double) (size / 1000));
            return fileSize.concat("KB");
        } else {
            fileSize = String.valueOf((double) (size / (1000 * 1000)));
            return fileSize.concat("MB");
        }
    }


    @Nullable
    public Link getLink(@NonNull Uri uri, @NonNull String root, @NonNull Closeable progress) {
        List<String> paths = uri.getPathSegments();
        String host = uri.getHost();
        Objects.requireNonNull(host);
        return ipfs.link(root, paths, progress);
    }

    public void connectUri(@NonNull Context context, @NonNull Uri uri) {
        try {
            String host = getHost(uri);
            if (host != null) {
                String pid = ipfs.decodeName(host);
                if (!pid.isEmpty()) {
                    PageConnectWorker.connect(context, pid);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    @NonNull
    public WebResourceResponse getResponse(@NonNull Uri uri, @NonNull String root,
                                           @NonNull List<String> paths,
                                           @NonNull Closeable closeable) throws Exception {

        if (paths.isEmpty()) {
            if (ipfs.isEmptyDir(root)) {
                String answer = generateDirectoryHtml(uri, root, paths, new ArrayList<>());
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else if (ipfs.isDir(root, closeable)) {
                List<LinkInfo> links = ipfs.getLinks(root, closeable);
                String answer = generateDirectoryHtml(uri, root, paths, links);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else {
                String mimeType = getMimeType(root, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }

                long size = ipfs.getSize(root, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }
                return getContentResponse(uri, root, mimeType, size, closeable);
            }


        } else {
            String cid = ipfs.resolve(root, paths, closeable);
            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }
            if(cid == null){
                throw new ContentException(uri.toString());
            }
            if (ipfs.isEmptyDir(cid)) {
                String answer = generateDirectoryHtml(uri, root, paths, new ArrayList<>());
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else if (ipfs.isDir(cid, closeable)) {
                List<LinkInfo> links = ipfs.getLinks(cid, closeable);
                String answer = generateDirectoryHtml(uri, root, paths, links);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));

            } else {
                String mimeType = getMimeType(uri, cid, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }
                long size = ipfs.getSize(cid, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }
                return getContentResponse(uri, cid, mimeType,
                        size, closeable);
            }


        }
    }

    @NonNull
    private FileInfo getDataInfo(@NonNull Uri uri, @NonNull String root, @NonNull Closeable closeable) throws TimeoutException {
        String host = uri.getHost();
        Objects.requireNonNull(host);

        try {
            String mimeType = getMimeType(root, closeable);

            return new FileInfo(root, mimeType, root);
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }
            throw new RuntimeException(throwable);
        }
    }

    @NonNull
    private WebResourceResponse getContentResponse(@NonNull Uri uri,
                                                   @NonNull String content,
                                                   @NonNull String mimeType, long size,
                                                   @NonNull Closeable closeable) throws TimeoutException {

        try {

            InputStream in = ipfs.getLoaderStream(content, closeable, Settings.IPFS_READ_TIMEOUT);


            Map<String, String> responseHeaders = new HashMap<>();
            if (size > 0) {
                responseHeaders.put("Content-Length", "" + size);
            }
            responseHeaders.put("Content-Type", mimeType);

            return new WebResourceResponse(mimeType, Content.UTF8, 200,
                    "OK", responseHeaders, new BufferedInputStream(in));
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }
            throw new RuntimeException(throwable);
        }


    }

    @NonNull
    private String getMimeType(@NonNull Uri uri,
                               @NonNull String element,
                               @NonNull Closeable closeable) {

        List<String> paths = uri.getPathSegments();
        if (!paths.isEmpty()) {
            String name = paths.get(paths.size() - 1);
            String mimeType = MimeTypeService.getMimeType(name);
            if (!mimeType.equals(MimeType.OCTET_MIME_TYPE)) {
                return mimeType;
            } else {
                return getMimeType(element, closeable);
            }
        } else {
            return getMimeType(element, closeable);
        }

    }

    @NonNull
    public FileInfo getFileInfo(@NonNull Uri uri, @NonNull Closeable closeable)
            throws InvalidNameException, ResolveNameException, TimeoutException {

        String host = uri.getHost();
        Objects.requireNonNull(host);

        String root = getRoot(uri, closeable);
        Objects.requireNonNull(root);


        Link linkInfo = getLink(uri, root, closeable);
        if (linkInfo != null) {
            String filename = linkInfo.getName();
            if (ipfs.isDir(linkInfo.getContent(), closeable)) {
                return new FileInfo(filename, MimeType.DIR_MIME_TYPE,
                        linkInfo.getContent());
            } else {
                String mimeType = getMimeType(uri, linkInfo.getContent(), closeable);
                return new FileInfo(filename, mimeType, linkInfo.getContent());
            }

        } else {
            return getDataInfo(uri, root, closeable);
        }
    }

    @Nullable
    public String getRoot(@NonNull Uri uri, @NonNull Closeable closeable)
            throws ResolveNameException, InvalidNameException {
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
    public WebResourceResponse getResponse(@NonNull Uri uri, @NonNull Closeable closeable) throws Exception {


        List<String> paths = uri.getPathSegments();

        String root = getRoot(uri, closeable);
        Objects.requireNonNull(root);

        if (closeable.isClosed()) {
            throw new TimeoutException(uri.toString());
        }

        return getResponse(uri, root, paths, closeable);

    }

    @NonNull
    public Pair<Uri, Boolean> redirectUri(@NonNull Uri uri, @NonNull Closeable closeable) {


        if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                Objects.equals(uri.getScheme(), Content.IPFS)) {
            List<String> paths = uri.getPathSegments();
            String host = uri.getHost();
            Objects.requireNonNull(host);
            if (!ipfs.isValidCID(host)) {
                String link = DnsAddrResolver.getDNSLink(host);
                if (link == null) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(Content.HTTPS)
                            .authority(host);
                    for (String path : paths) {
                        builder.appendPath(path);
                    }
                    return Pair.create(builder.build(), false);
                } else {
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
                            try {
                                Uri dnsUri = Uri.parse(cid);
                                if (dnsUri != null) {
                                    Uri.Builder builder = new Uri.Builder();
                                    builder.scheme(Content.IPNS)
                                            .authority(dnsUri.getAuthority());
                                    for (String path : paths) {
                                        builder.appendPath(path);
                                    }
                                    return redirectUri(dnsUri, closeable);
                                }
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    } else {
                        // is is assume that links is  <dns_link> is url
                        try {
                            Uri dnsUri = Uri.parse(link);
                            if (dnsUri != null) {
                                Uri.Builder builder = new Uri.Builder();
                                builder.scheme(Content.IPNS)
                                        .authority(dnsUri.getAuthority());
                                for (String path : paths) {
                                    builder.appendPath(path);
                                }
                                return redirectUri(dnsUri, closeable);
                            }
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }
                }

            } else {
                try {
                    String root = getRoot(uri, closeable);
                    Objects.requireNonNull(root);
                    return redirect(uri, root, paths, closeable);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        }
        return Pair.create(uri, false);
    }

    @NonNull
    public Pair<Uri, Boolean> redirect(@NonNull Uri uri, @NonNull String root,
                                       @NonNull List<String> paths, @NonNull Closeable closeable) {

        if (paths.isEmpty()) {

            if (!ipfs.isEmptyDir(root)) {
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

            String cid = ipfs.resolve(root, paths, closeable);

            if (cid != null) {
                if (!ipfs.isEmptyDir(cid)) {
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
        if (ipfs.swarmPeers() < 10) {
            ipfs.bootstrap(10, 10);
        }

        try {
            if (ipfs.swarmPeers() < 5) {
                List<Page> bootstraps = pages.getBootstraps(5);
                List<String> addresses = new ArrayList<>();
                for (Page bootstrap : bootstraps) {
                    String address = bootstrap.getAddress();
                    if (!address.isEmpty()) {
                        addresses.add(address.concat(Content.P2P_PATH).concat(bootstrap.getPid()));
                    }
                }

                List<Callable<Boolean>> tasks = new ArrayList<>();
                ExecutorService executor = Executors.newFixedThreadPool(addresses.size());
                for (String address : addresses) {
                    tasks.add(() -> ipfs.swarmConnect(address, 5));
                }
                List<Future<Boolean>> result = executor.invokeAll(tasks, 5, TimeUnit.SECONDS);
                for (Future<Boolean> future : result) {
                    LogUtils.error(TAG, "Bootstrap done " + future.isDone());
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public static class FileInfo {
        @NonNull
        private final String filename;
        @NonNull
        private final String mimeType;
        @NonNull
        private final String content;


        public FileInfo(@NonNull String filename, @NonNull String mimeType,
                        @NonNull String content) {
            this.filename = filename;
            this.mimeType = mimeType;
            this.content = content;

        }

        @NonNull
        public String getFilename() {
            return filename;
        }

        @NonNull
        public String getMimeType() {
            return mimeType;
        }

        @NonNull
        public String getContent() {
            return content;
        }

    }

    public static class TimeoutException extends Exception {

        public TimeoutException(@NonNull String name) {
            super("Timeout for " + name);
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

    public static class InvalidNameException extends Exception {

        public InvalidNameException(@NonNull String name) {
            super("Invalid name " + name);
        }


    }
}
