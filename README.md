# Real-Time Chat — Java Socket Programming

A console-based multi-user chat system using Java Socket Programming and multithreading, built per the project proposal's Milestones 2–4.

## Roles & Devlopement:-
Divyanshu walia - chat client (Connects to ChatServer, sends a username,and allow to chat)
Mehul gupta - chat server (Accepts multiple client connections and broadcasts each message to every other connected client.)
Manav Kaushik - built gui interface with live activity feed 

## Files

- `ChatServer.java` — accepts multiple client connections, one thread per client, broadcasts messages to everyone else
- `ChatClient.java` — console client: connects to the server, listens for incoming messages on a background thread while your typed messages go out on the main thread
- `ChatClientGUI.java` — Swing GUI client: same protocol as the console client, with a chat window, a live join/leave activity feed, and a "coming soon" panel for upcoming features


## What it already does

- Multiple users can connect and chat at the same time (multithreading)
- Usernames — each message is tagged with who sent it
- Join / leave notifications ("SERVER: Alice has joined the chat")
- Clean disconnect with the `quit` command
- GUI client (`ChatClientGUI`) additionally has:
  - A chat window with your own messages in blue, others' in black, system messages in italic gray
  - A live **Activity** panel listing join/leave events with timestamps
  - A **Coming soon** panel previewing features not built yet (private chat, timestamps in-chat, chat history, login, online status, file sharing, audio/video)

## Requirements

- JDK 8 or newer. Check with:
  ```
  java -version
  javac -version
  ```

## How to compile

```
javac ChatServer.java ChatClient.java ChatClientGUI.java
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

## How to run — GUI version

Once compiled, start the server as above, then launch the GUI instead of the console client:

```
java ChatClientGUI
```

It will pop up a small dialog asking for your username, then open the main chat window. You can also skip the dialog by passing the server address, port, and username directly — handy for testing or for quickly opening a few windows at once:

```
java ChatClientGUI 192.168.1.5 12345 Alice
```

## How to run — across multiple devices on the same Wi-Fi

This is the "Intermediate Architecture" from the proposal (multiple clients, one server) — no internet hosting needed, just the same Wi-Fi network.

1. Pick one laptop to run the server on. Find its local IP address:
   - Windows: `ipconfig` → look for "IPv4 Address"
   - Mac/Linux: `ifconfig` or `ip addr` → look for something like `192.168.1.5`
2. On that machine, run `java ChatServer`
3. On every other device (same Wi-Fi), run:
   ```
   java ChatClient 192.168.1.5 12345
   ```
   using the server machine's actual IP address in place of `192.168.1.5`
4. Make sure the server machine's firewall allows incoming connections on port 12345

If a device is on a different network entirely (not the same Wi-Fi), local IPs won't reach it — that needs cloud hosting, port forwarding, or a tool like ngrok instead.

## Next steps:-

- **Milestone 5 — Testing**: try 3+ clients at once, disconnect one mid-conversation, send messages back-to-back
- **Milestone 6 — Final improvements**, roughly in order of effort — these are exactly what's listed as "Coming soon" in the GUI:
  1. Timestamps shown on each chat message (the activity feed already has them, the chat itself doesn't yet)
  2. Private/direct messaging (`/msg username text`)
  3. Chat history (log messages to a file, or a simple database)
  4. User login / authentication
  5. Online status (who's currently online, not just join/leave events)
  6. File sharing
  7. Audio/video calling (a much bigger jump — would use WebRTC rather than plain sockets)

