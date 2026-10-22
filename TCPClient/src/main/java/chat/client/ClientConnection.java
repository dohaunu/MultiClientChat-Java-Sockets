package chat.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** UI-independent TCP client; listener receives protocol lines. */
public final class ClientConnection implements AutoCloseable {
    private Socket socket;
    private BufferedWriter out;
    private volatile boolean connected;
    public void connect(String host, int port, String username, Consumer<String> events) throws IOException {
        socket = new Socket(); socket.connect(new InetSocketAddress(host,port),5000);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        connected = true;
        sendRaw("HELLO\t" + (username == null ? "" : username.strip()));
        Thread.ofVirtual().name("client-listener").start(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) events.accept(line);
            } catch (IOException e) { if (connected) events.accept("ERROR\tConnection lost: " + e.getMessage()); }
            finally { connected=false; events.accept("CLOSED\tDisconnected"); }
        });
    }
    private synchronized void sendRaw(String text) throws IOException {
        if (!connected || out == null) throw new IOException("Not connected");
        out.write(text); out.newLine(); out.flush();
    }
    public void sendMessage(String text) throws IOException { sendRaw("MSG\t" + text.replace('\n',' ').replace('\r',' ')); }
    public void requestUsers() throws IOException { sendRaw("USERS"); }
    public boolean isConnected() { return connected; }
    @Override public void close() {
        if (connected) try { sendRaw("BYE"); } catch (IOException ignored) {}
        connected=false;
        if (socket != null) try { socket.close(); } catch (IOException ignored) {}
    }
}
