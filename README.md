# Real-Time Chat in Java

A multi-user chat system built with Java sockets and multithreading: one **server**, a **console client** and a **Swing GUI client**. Both clients speak the same protocol, so console and GUI users can chat together.

## Roles and Development

### 1. Divyanshu Walia — Chat Client & Core Planning
- Planned the project and studied how chat applications work.
- Developed the console-based chat client.
- Implemented support for multiple users through multithreading.

### 2. Mehul Gupta — Chat Server & Authentication
- Developed the chat server using Java Socket Programming.
- Implemented the login and registration system.
- Added group chat and private messaging features.

### 3. Manav Kaushik — GUI & User Interaction
- Developed the graphical user interface (GUI) using Java Swing.
- Implemented the online-user list and join/leave notifications.
- Added chat history and file-sharing features.
- Built the live activity feed to display user activity.

## Features

| Feature | Status |
|---|---|
| Real-time group chat for many users at once (one server thread per user) | Done |
| Timestamps on every message | Done |
| Accounts: register and log in (passwords stored as salted hashes, never as text) | Done |
| Private messages: `/msg name text`, or double-click a name in the GUI | Done |
| Live **Online** list, plus join/leave notifications (chat and Activity panel) | Done |
| Chat history: the newest 50 public messages are shown when you log in; all are saved to a file | Done |
| File sharing up to 2 MB (button in the GUI, `/sendfile` in the console) | Done |
| Audio & video calls | **Coming soon** |


## Requirements

JDK 8 or newer (`java -version` and `javac -version` should both work). Written to compile as Java 8; tested with JDK 21.

## Compile

```
javac *.java
```

## Run

**1. Start the server** (one computer):
```
java ChatServer
```
It prints the addresses other devices can use to reach it. The default port is 12345 (`java ChatServer 5000` to change it).

**2. Start one or more clients**, either kind, in any mix:
```
java ChatClientGUI                  (opens the login window)
java ChatClient                     (console; asks login or register, username, password)
```
The first time, choose **Create account** (GUI) or `r` (console). After that, log in with the same username and password. Usernames are 2-20 letters, numbers or underscores and are not case-sensitive; passwords are 4-64 characters.

To connect to a server on another computer, give its address (and port): `java ChatClientGUI 192.168.1.5 12345`, or `java ChatClient 192.168.1.5 12345`. In the GUI you can also just type it in the login window.

## Using the chat

| Do this | How |
|---|---|
| Message everyone | Type and press Enter |
| Private message | `/msg Bob see you at 5`, or double-click Bob in the Online list (GUI) |
| See who is online | The Online list (GUI) or `/users` (console) |
| Share a file | **Attach file** button (GUI) or `/sendfile path/to/file` (console) |
| Leave | Close the window, or type `/quit` |
| List the commands | `/help` |

Files you receive are saved automatically into a `received_files` folder next to the client, and never overwrite an existing file.

## Using it on two or more devices (same Wi-Fi or phone hotspot)

1. Run the server on one computer and note an address it prints, such as `192.168.1.5`.
2. On every other device, start a client and connect to that address and port 12345.
3. If a device can't connect, allow Java through the server computer's firewall (or allow incoming connections on port 12345).

Devices on *different* networks can't reach a `192.168.x.x` address; that would need cloud hosting, port forwarding or a tunnelling tool.

## Where things are saved

| File / folder | Where | What |
|---|---|---|
| `users.txt` | Folder the server runs in | Accounts (username, salt, password hash) |
| `chat_history.txt` | Folder the server runs in | Every public message |
| `received_files/` | Folder each client runs in | Files other people shared with you |

To reset everything, stop the server and delete `users.txt` and `chat_history.txt`.

## How it fits together

```
 ChatClientGUI / ChatClient --> ChatConnection --> TCP socket --> ChatServer (one thread per client)
                                                                    |-- UserStore   (users.txt)
                                                                    '-- ChatHistory (chat_history.txt)
```

Every message is one line of UTF-8 text such as `MSG|1760000000000|Alice|hello`. The full list of messages is in the comment at the top of `Protocol.java`.

## Good to know

- **Passwords are hashed on the server, but travel over the network unencrypted** (no TLS). That is fine for a demo on your own Wi-Fi; encrypting the connection (`SSLSocket`) would be a natural future improvement.
- **Private messages** go only to the recipient and are never saved or logged, but they are not end-to-end encrypted.
- **Logging in again** with an account that is already online logs the old session out and takes over. This avoids being locked out after a laptop sleeps or Wi-Fi drops.
- **If the server stops**, the GUI turns red and shows a **Reconnect** button; the console client exits with a message. Everyone has to log in again after a server restart.
- **Shared files** go to everyone (not privately), up to 2 MB.
- Only the newest 50 messages are replayed at login; older ones stay in `chat_history.txt`.

## Troubleshooting

| Problem | Likely cause |
|---|---|
| "Could not connect ... Is the server running?" | Server not started, wrong address or port, or a firewall is blocking it |
| "Port 12345 is already in use" | Another server is already running (close it, or use another port) |
| "Invalid username or password" | Wrong details, or you haven't created the account yet |
| Accents / other languages look wrong in the console | Your terminal's font or code page; the chat itself handles Unicode correctly |

## Next steps:- (future ideas)
1. Enhancing the gui for better expierience.
2. currently user need to type "\msg" for the private chat going to remove that and make it like user is on the particular window of the another user with whom user 1 wants to talk.
3. fixing minor bugs .
4. quit a bit complex technology to be used to obtain audio and video call functionality and make it user friendly .
  