package pontezit.android.tilos.com.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import android.net.Uri;

public abstract class AbsTransport {
    Uri uri;

    String emulation;

    public AbsTransport() {}

    public AbsTransport(Uri uri) {
        this.uri = uri;
    }

    /**
     * @return protocol part of the URI
     */
    public static String getProtocolName() {
        return "unknown";
    }

    /**
     * @return protocol part of the URI
     */
    protected abstract String getPrivateProtocolName();

    /**
     * Encode the current transport into a URI that can be passed via intent calls.
     * @return URI to host
     */
    public static Uri getUri(String input) {
        return null;
    }

    /**
     * Causes transport to connect to the target host. After connecting but before a
     * session is started, must call back toTerminalBridge#onConnected().
     * After that call a session may be opened.
     */
    public abstract void connect() throws IOException;

    /**
     * Closes the connection to the terminal. Note that the resulting failure to read
     * should call TerminalBridge#dispatchDisconnect(boolean).
     */
    public abstract void close();

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public abstract InputStream getConnection();

    public abstract boolean exists();

    public abstract boolean isConnected();

    /**
     * @return int default port for protocol
     */
    public abstract int getDefaultPort();



    /**
     * @param uri
     * @return
     */
    public abstract Uri createUri(Uri uri);

    public abstract String getContentType();

    /**
     * @return
     */
    public abstract boolean usesNetwork();

    public abstract boolean shouldSave();

    public abstract boolean isPotentialPlaylist();
}