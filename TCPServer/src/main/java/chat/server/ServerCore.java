package chat.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** UI-independent server. Protocol: HELLO TAB name, MSG TAB text, USERS, BYE. */
public final class ServerCore implements AutoCloseable {
    private final String host;
    private final int port;
    private final Consumer<String> log;
    private final Consumer<List<String>> usersChanged;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm:ss");
    private volatile boolean running;
    private ServerSocket serverSocket;

    public ServerCore(String host, int port, Consumer<String> log, Consumer<List<String>> usersChanged) {
        this.host = host; this.port = port; this.log = log; this.usersChanged = usersChanged;
    }
    public void start() throws IOException {
        if (running) throw new IllegalStateException("Server already running");
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(host, port));
        running = true;
        log.accept("Server Started on " + host + ":" + serverSocket.getLocalPort());
        Thread.ofVirtual().name("chat-acceptor").start(() -> {
            while (running) {
                try {
                    log.accept("Waiting for Client");
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    Thread.ofVirtual().name("client-handler").start(() -> handle(socket));
                } catch (IOException ex) { if (running) log.accept("Accept error: " + ex.getMessage()); }
            }
        });
    }
    public int boundPort() { return serverSocket == null ? -1 : serverSocket.getLocalPort(); }
    private void handle(Socket socket) {
        ClientHandler handler = null;
        try (socket;
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String hello = input.readLine();
            if (hello == null || !hello.startsWith("HELLO\t")) return;
            String chosen = hello.substring(6).trim();
            if (chosen.length() > 32 || chosen.contains("\t") || chosen.contains("\n")) { output.write("ERROR\tInvalid username\n"); output.flush(); return; }
            boolean guest = chosen.isEmpty();
            // Unique identities, with optional read-only guests.
            synchronized (clients) {
                if (!guest && clients.values().stream().anyMatch(c -> c.username.equalsIgnoreCase(chosen))) {
                    output.write("ERROR\tUsername already in use\n"); output.flush(); return;
                }
                int id = nextId.incrementAndGet();
                String label = guest ? "Guest-" + id : chosen;
                handler = new ClientHandler(id, label, guest, output, socket);
                clients.put(id, handler);
            }
            handler.send("WELCOME\t" + handler.username + "\t" + (guest ? "READ_ONLY" : "ACTIVE"));
            log.accept("Welcome " + handler.username + (guest ? " (READ-ONLY MODE)" : ""));
            notifyUsers();
            broadcast("SYSTEM\t" + handler.username + " joined the chat.");
            String line;
            while (running && (line = input.readLine()) != null) {
                if (line.equals("BYE")) break;
                if (line.equals("USERS")) { handler.send("USERS\t" + String.join(", ", activeUsers())); continue; }
                if (!line.startsWith("MSG\t")) { handler.send("ERROR\tUnknown command"); continue; }
                if (guest) { handler.send("ERROR\tREAD-ONLY MODE: sending is disabled"); continue; }
                String message = line.substring(4).trim();
                if (message.isBlank()) continue;
                if (message.length() > 2000) { handler.send("ERROR\tMessage too long"); continue; }
                if (message.equalsIgnoreCase("allUsers")) { handler.send("USERS\t" + String.join(", ", activeUsers())); continue; }
                if (message.equalsIgnoreCase("end") || message.equalsIgnoreCase("bye")) break;
                String formatted = handler.username + " [" + LocalTime.now().format(clock) + "]: " + message;
                log.accept(formatted);
                broadcast("MSG\t" + formatted);
            }
        } catch (IOException ex) { if (running) log.accept("Connection ended: " + ex.getMessage()); }
        finally {
            if (handler != null) {
                clients.remove(handler.id);
                log.accept(handler.username + " disconnected");
                notifyUsers();
                broadcast("SYSTEM\t" + handler.username + " left the chat.");
            }
        }
    }
    private List<String> activeUsers() { return clients.values().stream().map(c -> c.username).sorted(String.CASE_INSENSITIVE_ORDER).toList(); }
    private void notifyUsers() { usersChanged.accept(activeUsers()); }
    private void broadcast(String message) { for (ClientHandler c : clients.values()) c.send(message); }
    @Override public void close() {
        running = false;
        if (serverSocket != null) try { serverSocket.close(); } catch (IOException ignored) {}
        for (ClientHandler c : clients.values()) try { c.socket.close(); } catch (IOException ignored) {}
        clients.clear(); notifyUsers(); log.accept("Server Stopped");
    }
    private static final class ClientHandler {
        final int id; final String username; final boolean readOnly; final BufferedWriter output; final Socket socket;
        ClientHandler(int id, String username, boolean readOnly, BufferedWriter output, Socket socket) {
            this.id=id; this.username=username; this.readOnly=readOnly; this.output=output; this.socket=socket;
        }
        synchronized void send(String message) {
            try { output.write(message); output.newLine(); output.flush(); }
            catch (IOException e) { try { socket.close(); } catch (IOException ignored) {} }
        }
    }
}
