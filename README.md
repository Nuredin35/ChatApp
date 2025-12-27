# ChatApp - Java Swing Chat Application

A single-file Java Swing chat application that can operate as both a server and a client. This application supports real-time messaging, file transfers, chat rooms, private messages, and more.

## Features

- **Dual Mode Operation**: Run as a server or connect as a client from the same application
- **Chat Rooms**: Create and join multiple chat rooms
- **Private Messaging**: Send private messages using `/pm` command
- **File Transfer**: Send files to users or broadcast to rooms with progress tracking
- **Emoji Support**: Built-in emoji picker and paste support
- **AES Encryption**: Optional encryption for messages and files
- **User Management**: 
  - Online user list
  - Admin controls (kick, ban, announce)
  - Username management
- **Typing Indicators**: Real-time typing status
- **Chat Logs**: Automatic logging per room with save functionality
- **Delivery Receipts**: Message acknowledgment system
- **Heartbeat System**: PING/PONG for connection monitoring
- **Themes**: Light and Dark theme support
- **Settings**: Persistent configuration via properties file

## Requirements

- **JDK 8 or higher** (Java 8+)
- No external dependencies - uses only standard Java libraries

## Getting Started

### Compilation

Compile the application using:

```bash
javac ChatApp.java
```

### Running the Application

Run the compiled class:

```bash
java ChatApp
```

## Usage

### Starting as Server

1. Launch the application
2. Click **"Start Server"**
3. Enter the port (default: 5000) and AES key
4. Click **"Start Server"**
5. Note the server IP address displayed (use this to connect from other devices)

### Connecting as Client

1. Launch the application (or another instance)
2. Click **"Connect as Client"**
3. Enter:
   - **Host/IP**: Server IP address (use `localhost` for same device)
   - **Port**: Server port (default: 5000)
   - **Name**: Your username
   - **AES Key**: Must match server's AES key
4. Optionally check **"Encrypt"** to encrypt outgoing messages/files
5. Click **"Connect"**

## Commands

### Client Commands

- `/pm <username> <message>` - Send a private message
- `/join <room>` - Join or create a chat room
- `/rename <newName>` - Change your username
- `/users` - Refresh the online user list

### Admin Commands (First connected user is admin)

- `/kick <username>` - Kick a user from the server
- `/ban <username>` - Ban a user
- `/announce <message>` - Broadcast an announcement to all users

## File Transfer

1. Click **"Send File"** button
2. Select a file from your system
3. Enter target:
   - Username for private file transfer
   - Room name to send to all in that room
   - `ALL` to broadcast to current room
4. File transfer progress will be displayed
5. Received files are saved as `received_<filename>` in the application directory

## Settings

Access settings via the **"Settings"** button to configure:

- Default port
- Default AES encryption key
- Default encryption toggle
- Username
- Theme (Light/Dark)
- Notifications
- Chat UI style (Comfortable/Compact)
- Chat logs directory
- Timestamp display

Settings are saved to `chatapp.properties` file.

## Chat Logs

- Chat logs are automatically saved per room
- Server can manually save logs using **"Save Current Room Log"** button
- Logs are stored in the `chatlogs` directory (configurable)
- Format: `<roomname>.log`

## Developer Information

**Developer**: Nuredin Wario  
**ID**: UGR/35196/16  
**Section**: 3  
**Group**: 6  
**Department**: Computer Science and Engineering  
**Course**: Advanced Java Programming  
**Institution**: Adama Science and Technology University

## Technical Details

- **Protocol**: Custom text-based protocol over TCP sockets
- **Threading**: Multi-threaded server with thread pool for client handling
- **Encryption**: AES-128 encryption using SHA-256 key derivation
- **UI Framework**: Java Swing
- **Network**: Java Socket API

## File Structure

```
ChatApp/
├── ChatApp.java          # Main application file (single-file application)
├── chatapp.properties    # Settings file (auto-generated)
├── chatlogs/             # Chat log directory (auto-generated)
│   ├── lobby.log
│   └── <room>.log
└── README.md             # This file
```

## Notes

- The first user to connect to the server becomes the admin
- Usernames are automatically made unique if duplicates exist
- Server binds to `0.0.0.0` to accept connections from any network interface
- Heartbeat system helps detect disconnected clients
- Typing indicators are sent automatically while typing

## License

This project is developed for educational purposes as part of the Advanced Java Programming course.
