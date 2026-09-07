# Disclaimer

This mod might be considered as cheat in public server, use on your own risk.
I made this 'cause I am using it on a private server. 

# Lootr Chest ESP

A client-only Minecraft Forge 1.20.1 mod that highlights only unopened
`lootr:lootr_chest` blocks through walls.

This mod is made specificly for CTE2 as the chests are so hidden. Making sure I opened every loot chest :)

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- Lootr 0.7.35.94 (compatible 0.7.x versions are accepted by metadata)

Lootr is a compile/runtime dependency and is not embedded in this mod's jar.

## Controls and configuration

- `H`: toggle the ESP
- `J`: open the Lootr Chest ESP settings screen
- `config/lootrchestesp.json`: enabled state, scan radius, scan interval,
  outline and filled-face toggles, face opacity, line width, and global ESP color

## Build

Run `gradlew.bat clean build`. The jar is written to `build/libs/`.

## Credits

This mod is based on BlockESP by mellytimes.

## License

MIT
