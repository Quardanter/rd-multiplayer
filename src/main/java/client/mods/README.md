# rd-multiplayer modding
Status: prototyping, expect frequent API changes

A mod is a JAR in the mods/ directory containing a class that implements client.mods.Mod. From onEnable(), your mod receives a ModContext with which it can register:

Surface | Method | Notes
Chat command | registercommand(name, handler) | /name args..., returns true to consume
HUD overlay | registerHud(renderer) | Per-frame, ortho 2D set up by host
Tick listener | registerTick(runnable) | ~60 hz, game thread
Keybind | registerKeybind(key, runnable) | Trigger which fires when chat/GUI not capturing input
Event | addListener(class, consumer) | Scroll down to see available types

### Events
* BlockBreak
* BlockPlace
* ChatReceived
* PlayerConnection
* ServerConnected
* ServerDisconnected

### Manifest
Every mod JAR must have mod.properties at the JAR root:
```
id=example
name=Example Mod
version=0.1.0
entry=com.example.examplemod.ExampleMod
```
