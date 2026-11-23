# Ambassador

This is a Velocity plugin that makes it possible to host a modern Forge server behind a Velocity proxy!

Unlike other solutions, this plugin does not require any special modifications to the backend server nor the client. (The player doesn't need to do anything)
## Only for 1.13-1.20.1
Velocity has now added built-in support for newer mc versions and therefore don't need Ambassador. You might still need PCF mod on the server-side, for more info please visit: https://github.com/adde0109/Proxy-Compatible-Forge
## How to get started:
1. Download and install this plugin to your proxy.
2. After starting the server, configure the plugin it to your liking using the config file found in the folder "Ambassador".
3. If you want to use player-information forwarding you can use any of these mods on the 1.13+ forge server:
- https://github.com/adde0109/Proxy-Compatible-Forge (Modern forwarding) (Magma 1.18.2 and higher includes this)

- https://github.com/caunt/BungeeForge (Legacy forwarding)

## Features
* Server switching without any client side mod when the servers are similar. (Mods must match)
* ServerRedirect support for server switching.
* Server switching using Client Reset Packet Mod for instant server switching:
  
1.16.5: https://github.com/Just-Chaldea/Forge-Client-Reset-Packet

1.18.2+: https://www.curseforge.com/minecraft/mc-mods/forge-client-reset-packet-forward

## Stuck on "Negotiating":
This is an issue with Client Reset Packet Mod being partly incompatible with certain mods on the client. Please remove incompatible mods on the client if you have this issue. (Yes, this also includes client-side only mods.)

## Fork notes (Masuary/Ambassador, branch non-api)
This fork runs the Forge 1.18.2 proxy of a Vault Hunters network on Velocity 3.4.0. Changes against upstream `non-api`:
* Handshake state is kept per player session instead of in shared enum fields, so concurrent logins and switches no longer overwrite each other.
* One session per account during the Forge login handshake: a second login of the same account is refused (or, with `kick-existing-players`, the older session is kicked) instead of slipping past Velocity's duplicate check while the first session is out of its player list.
* With `bypass-registry-checks = true`, switching no longer waits for the target's registries, and a target whose registry ids differ from the ones the client got at login is logged once per server pair (`Registry drift: ...`). Switches are not refused.
* `serverRedirectTimeout` is applied (it was always 30); `bypass-mod-checks` has no effect and is documented as such.
* Load and reload failures are logged with their stack trace.

Build: `git submodule update --init` (Velocity at the pinned commit), then `./gradlew build` with Java 21. The jar is `build/libs/Ambassador-Velocity-<version>-all.jar`. Java warnings fail the build (`-Xlint:all -Werror`).

Settings the network uses (`plugins/ambassador/Ambassador.toml`): `bypass-registry-checks = true` (every backend runs the same mods with identical registry ids), `enable-kick-reset = false`, `reconnect-message = "<gold>Please reconnect</gold>"` (MiniMessage, not `&` codes). Proxy-Compatible-Forge runs on every backend.

## Discord
https://discord.gg/Vusz9pBNyJ
