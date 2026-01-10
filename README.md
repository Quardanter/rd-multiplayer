# rd-multiplayer
Work in progress...

### Protocol

| Packet ID | Name           | Direction | Payload                                     | Notes |
|-----------|----------------|-----------|-----------------------------------------------------|-------|
| 0x01      | BLOCK_BREAK    | C2S / S2C | `int x, int y, int z`                               | Break a block. Server broadcasts to all clients. |
| 0x02      | BLOCK_PLACE    | C2S / S2C | `int x, int y, int z`                               | Place a block. Server broadcasts to all clients. |
| 0x03      | REQUEST_LEVEL  | C2S       | _none_                                              | Client requests full level data. |
| 0x04      | LEVEL_DATA     | S2C       | `int width, int height, int depth, int length, byte[length] blocks` | Server sends full level data. |
| 0x05      | KEEPALIVE      | C2S / S2C | `long timestamp`                                    | Measure ping & keep connection alive. |