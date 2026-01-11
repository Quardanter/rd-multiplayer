# rd-multiplayer
Work in progress...

### Protocol

| ID   | Name          | Direction | Payload                                                             | Notes                                                  |
|------|---------------|-----------|---------------------------------------------------------------------|--------------------------------------------------------|
| 0x01 | AUTH_REQUEST  | C2S       | `utf8 username`                                                     | Send an authentication request.                        |
| 0x02 | AUTH_SUCCESS  | S2C       | _none_                                                              | Authentication successful.                             |
| 0x03 | AUTH_FAILED   | S2C       | `utf8 reason`                                                       | Authentication failed because of reason provided.      |
| 0x04 | BLOCK_BREAK   | C2S / S2C | `int x, int y, int z`                                               | Break a block. Server broadcasts to all clients.       |
| 0x05 | BLOCK_PLACE   | C2S / S2C | `int x, int y, int z`                                               | Place a block. Server broadcasts to all clients.       |
| 0x06 | REQUEST_LEVEL | C2S       | _none_                                                              | Client requests for level data.                        |
| 0x07 | LEVEL_DATA    | S2C       | `int width, int height, int depth, int length, byte[length] blocks` | Server sends full level data.                          |
| 0x07 | CHAT          | C2S / S2C | `utf8 author, utf8 message`                                         | Send a chat message. Server broadcasts to all clients. |