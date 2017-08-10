
package com.maplecloudy.distribute.engine.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

// Taken from Elasticsearch core
public class HttpDownloader {

    private boolean useTimestamp = false;
    private boolean skipExisting = false;

    public HttpDownloader() {

        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }
        } };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public boolean download(URL source, File dest, DownloadProgress progress, long timeout) {

        if (dest.exists() && skipExisting) {
            return true;
        }

        //don't do any progress, unless asked
        if (progress == null) {
            progress = new NullProgress();
        }

        //set the timestamp to the file date.
        long timestamp = 0;

        boolean hasTimestamp = false;
        if (useTimestamp && dest.exists()) {
            timestamp = dest.lastModified();
            hasTimestamp = true;
        }

        // create file if needed
        if (!dest.exists()) {
            try {
                File parent = dest.getAbsoluteFile().getParentFile();
                parent.mkdirs();
                dest.createNewFile();
            } catch (IOException ex) {
                throw new IllegalStateException(String.format("Cannot write file %s", dest), ex);
            }
        }

        GetThread getThread = new GetThread(source, dest, hasTimestamp, timestamp, progress);

        try {
            getThread.setDaemon(true);
            getThread.start();
            getThread.join(timeout);

            if (getThread.isAlive()) {
                throw new IllegalStateException("The GET operation took longer than " + timeout + ", stopping it.");
            }
        } catch (InterruptedException ie) {
            return false;
        } finally {
            getThread.closeStreams();
        }

        return getThread.wasSuccessful();
    }

    public interface DownloadProgress {
        void beginDownload();

        void onTick();

        void endDownload();
    }

    public static class NullProgress implements DownloadProgress {

        public void beginDownload() {

        }

        public void onTick() {
        }

        public void endDownload() {

        }
    }

    public static class VerboseProgress implements DownloadProgress {
        private int dots = 0;
        PrintStream out;

        public VerboseProgress(PrintStream out) {
            this.out = out;
        }

        public void beginDownload() {
            out.print("Downloading ");
            dots = 0;
        }

        public void onTick() {
            out.print(".");
            if (dots++ > 50) {
                out.flush();
                dots = 0;
            }
        }

        public void endDownload() {
            out.println("DONE");
            out.flush();
        }
    }

    private class GetThread extends Thread {

        private final URL source;
        private final File dest;
        private final boolean hasTimestamp;
        private final long timestamp;
        private final DownloadProgress progress;

        private boolean success = false;
        private RuntimeException ioexception = null;
        private InputStream is = null;
        private OutputStream os = null;
        private URLConnection connection;
        private int redirections = 0;

        GetThread(URL source, File dest, boolean h, long t, DownloadProgress p) {
            this.source = source;
            this.dest = dest;
            hasTimestamp = h;
            timestamp = t;
            progress = p;
        }

        public void run() {
            try {
                success = get();
            } catch (IOException ioex) {
                ioexception = new IllegalStateException(ioex);
            }
        }

        private boolean get() throws IOException {

            connection = openConnection(source);

            if (connection == null) {
                return false;
            }

            boolean downloadSucceeded = downloadFile();

            //if (and only if) the use file time option is set, then
            //the saved file now has its timestamp set to that of the
            //downloaded file
            if (downloadSucceeded && useTimestamp) {
                updateTimeStamp();
            }

            return downloadSucceeded;
        }


        private boolean redirectionAllowed(URL aSource, URL aDest) throws IOException {

            redirections++;
            if (redirections > 5) {
                String message = "More than " + 5 + " times redirected, giving up";
                throw new IOException(message);
            }


            return true;
        }

        private URLConnection openConnection(URL aSource) throws IOException {

            // set up the URL connection
            URLConnection connection = aSource.openConnection();
            // modify the headers
            // NB: things like user authentication could go in here too.
            if (hasTimestamp) {
                connection.setIfModifiedSince(timestamp);
            }

            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).setInstanceFollowRedirects(false);
                ((HttpURLConnection) connection).setUseCaches(true);
                ((HttpURLConnection) connection).setConnectTimeout(5000);
            }
            // connect to the remote site (may take some time)
            connection.connect();

            // First check on a 301 / 302 (moved) response (HTTP only)
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                int responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newLocation = httpConnection.getHeaderField("Location");
                    String message = aSource
                            + (responseCode == HttpURLConnection.HTTP_MOVED_PERM ? " permanently" : "") + " moved to "
                            + newLocation;
                    URL newURL = new URL(newLocation);
                    if (!redirectionAllowed(aSource, newURL)) {
                        return null;
                    }
                    return openConnection(newURL);
                }
                // next test for a 304 result (HTTP only)
                long lastModified = httpConnection.getLastModified();
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED
                        || (lastModified != 0 && hasTimestamp && timestamp >= lastModified)) {
                    // not modified so no file download. just return
                    // instead and trace out something so the user
                    // doesn't think that the download happened when it
                    // didn't
                    return null;
                }
                // test for 401 result (HTTP only)
                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    String message = "HTTP Authorization failure";
                    throw new IOException(message);
                }
            }

            //REVISIT: at this point even non HTTP connections may
            //support the if-modified-since behaviour -we just check
            //the date of the content and skip the write if it is not
            //newer. Some protocols (FTP) don't include dates, of
            //course.
            return connection;
        }

        private boolean downloadFile() throws FileNotFoundException, IOException {
            IOException lastEx = null;
            for (int i = 0; i < 3; i++) {
                // this three attempt trick is to get round quirks in different
                // Java implementations. Some of them take a few goes to bind
                // property; we ignore the first couple of such failures.
                try {
                    is = connection.getInputStream();
                    break;
                } catch (IOException ex) {
                    lastEx = ex;
                }
            }
            if (is == null) {
                throw new IOException("Can't get " + source + " to " + dest, lastEx);
            }

            os = new FileOutputStream(dest);
            progress.beginDownload();
            boolean finished = false;
            try {
                byte[] buffer = new byte[1024 * 512];
                int length;
                while (!isInterrupted() && (length = is.read(buffer)) >= 0) {
                    os.write(buffer, 0, length);
                    progress.onTick();
                }
                finished = !isInterrupted();
            } finally {
                if (!finished) {
                    // we have started to (over)write dest, but failed.
                    // Try to delete the garbage we'd otherwise leave
                    // behind.
                    IOUtils.close(os);
                    IOUtils.close(is);
                    dest.delete();
                }
                else {
                    IOUtils.close(os);
                    IOUtils.close(is);
                }
            }
            progress.endDownload();
            return true;
        }

        private void updateTimeStamp() {
            long remoteTimestamp = connection.getLastModified();
            if (remoteTimestamp != 0) {
                dest.setLastModified(remoteTimestamp);
            }
        }

        boolean wasSuccessful() {
            if (ioexception != null) {
                throw ioexception;
            }
            return success;
        }

        void closeStreams() {
            interrupt();
            if (success) {
                IOUtils.close(is);
                IOUtils.close(os);
            }
            else {
                IOUtils.close(is);
                IOUtils.close(os);
                if (dest != null && dest.exists()) {
                    dest.delete();
                }
            }
        }
    }
}