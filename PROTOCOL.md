
# Protocol
The main socket protocol. Provides more context than the [global/Packets.java](https://github.com/9aze/rd-multiplayer/blob/main/src/main/java/global/Packets.java).

| ID   | Name          | Direction | Payload                                                             | Notes                                                                       |
|------|---------------|-----------|---------------------------------------------------------------------|-----------------------------------------------------------------------------|
| 0x01 | AUTH_REQUEST  | C2S       | `utf8 username`                                                     | Send an authentication request.                                             |
| 0x02 | AUTH_SUCCESS  | S2C       | _none_                                                              | Authentication successful.                                                  |
| 0x03 | AUTH_FAILED   | S2C       | `utf8 reason`                                                       | Authentication failed because of reason provided.                           |
| 0x04 | BLOCK_BREAK   | C2S / S2C | `int x, int y, int z`                                               | Break a block. Server broadcasts to all clients.                            |
| 0x05 | BLOCK_PLACE   | C2S / S2C | `int x, int y, int z`                                               | Place a block. Server broadcasts to all clients.                            |
| 0x06 | REQUEST_LEVEL | C2S       | _none_                                                              | Client requests for level data.                                             |
| 0x07 | LEVEL_DATA    | S2C       | `int width, int height, int depth, int length, byte[length] blocks` | Server sends full level data.                                               |
| 0x08 | CHAT          | C2S / S2C | `utf8 author, utf8 message`                                         | Send a chat message. Server broadcasts to all clients.                      |
| 0x09 | KEEPALIVE     | C2S / S2C | `long timestamp`                                                    | Measure ping & keep connection alive.                                       |
| 0x10 | CONNECTION    | S2C       | `int type (0: join, 1: leave), utf8 username`                       | Server sends connection messages.                                           |
| 0x11 | POS           | C2S / S2C | `utf8 username, double x, double y, double z`                       | Client's position after move and server broadcasts.                         |
| 0x11 | SET_POS       | S2C       | `double x, double y, double z`                       | Server force-sets the client position. Usually during AntiCheat violations. |
