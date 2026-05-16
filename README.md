# rd-multiplayer
The first version of Minecraft, but multiplayer!

Full credits to Notch and the decompiled codebase at: [thecodeofnotch/rd-131122](https://github.com/thecodeofnotch/rd-132211).

Preview video as of <code>58a7142757+</code>:
<br>
<a href="https://www.youtube.com/watch?v=5qlTXX0Mn8U">
  <img src="https://img.youtube.com/vi/5qlTXX0Mn8U/hqdefault.jpg" width="240">
</a>

## installation

### run/build from source
1. Make sure you have Java 8 installed
2. Clone this repository
3. Building: `./gradlew buildServer`and `./gradlew buildClient`
5. Running: `./gradlew runServer` and `./gradlew runClient`

### pre-built binaries
1. Make sure you have Java installed
2. Download the [latest release](https://github.com/9aze/rd-multiplayer/releases/latest/)
3. Run the jars by either double-clicking them or `java -jar rd-*.jar`

## protocol
Read the socket protocol [here](https://github.com/9aze/rd-multiplayer/blob/main/PROTOCOL.md).

## features
- Breaking/Placing blocks
- Moving around, jumping and flying
- Multiplayer (obviously)
- Chat with connection messages
- Coordinates, Fps and Tablist HUD
- Player rendering
- Server-side world saving/loading
- A server-side AntiCheat
- *And more....*