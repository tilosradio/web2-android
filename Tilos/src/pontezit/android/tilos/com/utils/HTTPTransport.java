package pontezit.android.tilos.com.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;

import pontezit.android.tilos.com.utils.Utils;

import android.net.Uri;

public class HTTPTransport extends AbsTransport {

    private static final String PROTOCOL = "http";
    private static final int DEFAULT_PORT = 80;

    private HttpURLConnection conn = null;
    private InputStream is = null;
    private int mResponseCode = -1;
    private String mContentType = null;

    public HTTPTransport() {
        super();
    }

    public HTTPTransport(Uri uri) {
        super(uri);
    }

    public static String getProtocolName() {
        return PROTOCOL;
    }

    protected String getPrivateProtocolName() {
        return PROTOCOL;
    }

    /**
     * Encode the current transport into a URI that can be passed via intent calls.
     * @return URI to host
     */
    public static Uri getUri(String input) {
        return getUri(input, false);
    }

    /**
     * Encode the current transport into a URI that can be passed via intent calls.
     * @return URI to host
     */
    private static Uri getUri(String input, boolean scrubUri) {

        Uri uri = Uri.parse(input);

        return uri;
    }

    @Override
    public void connect() throws IOException {

        URL url = new URL(uri.toString());

        conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "ServeStream");

        mResponseCode = conn.getResponseCode();

        if (mResponseCode == -1) {
            mResponseCode = HttpURLConnection.HTTP_OK;
        }

        mContentType = conn.getContentType();
        is = conn.getInputStream();
    }

    @Override
    public void close() {
        Utils.closeInputStream(is);
        Utils.closeHttpConnection(conn);
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return is != null;
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public Uri createUri(Uri uri) {

        return uri;
    }

    @Override
    public String getContentType() {
        return mContentType;
    }

    @Override
    public boolean usesNetwork() {
        return true;
    }

    @Override
    public boolean shouldSave() {
        return true;
    }

    @Override
    public InputStream getConnection() {
        return is;
    }

    @Override
    public boolean isPotentialPlaylist() {
        return true;
    }
}