# Multi-Client Chat Application (Java Sockets + JavaFX)

Two **independent Maven projects** implement a TCP group chat with a JavaFX client and administration window. Requires **JDK 21**, Maven 3.9+, and a desktop graphical environment. Designed for Windows by default (`javafx.platform=win`).

## Build

Open each folder (`TCPServer`, `TCPClient`) as a Maven project in IntelliJ IDEA, then run the `package` lifecycle, or use:

```bash
cd TCPServer
mvn clean package
cd ../TCPClient
mvn clean package
```

For **macOS**, add `-Djavafx.platform=mac` (Apple Silicon: `mac-aarch64`); for **Linux**, add `-Djavafx.platform=linux`. Build on the OS on which you plan to run. The shaded JARs in each `target/` are the executable artifacts (`tcp-server-1.0.0.jar`, `tcp-client-1.0.0.jar`). JavaFX platform native libraries must match the target OS and CPU.

## Run

```bash
# Terminal A
java -jar TCPServer/target/tcp-server-1.0.0.jar
# Terminals B, C, D
java -jar TCPClient/target/tcp-client-1.0.0.jar localhost 3000
```

The code also supports `java TCPServer` and `java TCPClient localhost 3000` when classes and dependencies are on the Java classpath, e.g. via IntelliJ run configurations. Server startup defaults to `0.0.0.0:3000`.

## Configuration

Copy `server.properties` from `TCPServer/src/main/resources/` to the working directory of the server to override `server.host` or `server.port` at runtime. Copy `client.properties` from `TCPClient/src/main/resources/` to the client working directory for its default host and port. Client command-line host and port override the client config. An external properties file is optional.

## Features

- Central server broadcasts `username [HH:mm:ss]: message` to connected clients.
- Each connection is processed on a Java 21 **virtual thread**.
- Blank username means **READ-ONLY MODE**; client message controls are disabled and the server also enforces it.
- Usernames must be unique (case-insensitive); guest users receive unique `Guest-n` labels.
- Enter `allUsers` or press **ALL USERS** for the active list (private response).
- Enter `end`, `bye`, or press **LEAVE** to disconnect.
- Live green status indicator in client and log + user list in server; each server list user has a random pastel background.
- Transport and logic are separated from the JavaFX views (`ServerCore` and `ClientConnection` do not import JavaFX).

## Wire protocol

UTF-8 newline-delimited messages. Client-to-server: `HELLO\\tusername`, `MSG\\tmessage`, `USERS`, `BYE` (where `\\t` here describes an actual tab character). Server-to-client: `WELCOME\\tusername\\tACTIVE|READ_ONLY`, `MSG\\tformatted-message`, `SYSTEM\\tevent`, `USERS\\tcomma-separated names`, `ERROR\\treason`.

## Suggested testing checklist

1. Start the server; verify *Server Started* and *Waiting for Client* in log.
2. Launch two clients with distinct names, confirm both are listed.
3. Send messages each way and verify username + time formatting.
4. Type `allUsers` and ensure only requester receives the list.
5. Join with blank username and verify read-only: it can see messages but cannot send.
6. Try duplicate username; verify error response.
7. Leave with `bye` or LEAVE; confirm user list updates.
8. Close server and clients, then restart to verify clean reconnect.

## Artifacts & submission

- `TCPServer/` and `TCPClient/` Maven source folders.
- `docs/class-diagram.png` and `docs/deployment-diagram.png` (ready-to-view diagrams); editable `.puml` and `.dot` source files are included.
- Push entire repository to GitHub preserving commits. The actual GitHub URL and commit history must be created by the student.
- Record the required 3-minute live demo on your computer; see `docs/demo-script.md`.

**Note:** The project is source-complete, but jars must be built using Maven with dependencies downloaded. No claim of GUI execution is made unless you test it on your desktop.
