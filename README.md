# Real-Time Chat — Java Socket Programming

A console-based multi-user chat system using Java Socket Programming and multithreading.

## Roles & Devlopement:-
Divyanshu walia - chat client (Connects to ChatServer, sends a username,and allow to chat)
Mehul gupta - chat server (Accepts multiple client connections and broadcasts each message to every other connected client.)
Manav Kaushik - test phase (test whether server and client chats are working properly or not and review them.)

## Files

- `ChatServer.java` — accepts multiple client connections, one thread per client, broadcasts messages to everyone else
- `ChatClient.java` — connects to the server, listens for incoming messages on a background thread while your typed messages go out on the main thread

## What features does it have:-

- With the help of multithreading multiple users can connect and chat at the same time 
- Usernames — each message is tagged with who sent it
- Join / leave notifications ("SERVER: Alice has joined the chat")
- Clean disconnect with the `quit` command

## Requirements

- JDK 8 or newer. Check with:
  ```
  java -version
  javac -version
  ```

## How to compile

```
javac ChatServer.java ChatClient.java
```

## How to run — same computer (quick test)

Open three terminals in this folder.

**Terminal 1 — start the server:**
```
  java ChatServer
```
(starts on port 12345 by default; pass a different port as an argument if you like, e.g. `java ChatServer 5000`)

**Terminal 2 and 3 — start two clients:**
```
java ChatClient
```
Each will ask for a username, then anything you type gets sent to everyone else connected. Type `quit` to leave.

## How to run — across multiple devices on the same Wi-Fi

This is the "Intermediate Architecture" (multiple clients, one server) — no internet hosting needed, just the same Wi-Fi network.

1. Pick one laptop to run the server on. Find its local IP address:
   - Windows: `ipconfig` → look for "IPv4 Address"
2. On that machine, run `java ChatServer`
3. On every other device (same Wi-Fi), run:
   ```
   java ChatClient 192.168.1.5 12345
   ```
   using the server machine's actual IP address in place of `192.168.1.5`
4. Make sure the server machine's firewall allows incoming connections on port 12345

If a device is on a different network entirely (not the same Wi-Fi), local IPs won't reach it — that needs cloud hosting, port forwarding, or a tool like ngrok instead.

## Next steps (matching your remaining milestones)

- **Milestone 5 — Testing**: try 3+ clients at once, disconnect one mid-conversation, send messages back-to-back
- **Milestone 6 — Final improvements**, roughly in order of effort:
  1. Timestamps on each message
  2. Private/direct messaging (`/msg username text`)
  3. Chat history (log messages to a file, or a simple database)
  4. Swing or JavaFX GUI in place of the console
  5. Audio/video calling 