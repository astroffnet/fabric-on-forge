# Fabric-on-Forge (Minecraft 1.20.1, Forge 47.1.3)

## Build
GitHub -> Actions -> Build -> Run workflow -> download the `fabric-on-forge` artifact (jar).

## Use
1. Put the built jar into `mods/` (Forge 1.20.1).
2. Put Fabric mods into `<game dir>/fabric-mods/` (NOT into `mods/`).
3. First launch needs internet (downloads intermediary + MCPConfig mappings, cached in `.fabric-on-forge/`).
4. Do NOT put the official fabric-api.jar there: it is skipped (it is built from nested jar-in-jar modules that need the real
   fabric-loader internals, which this project does not emulate). A small Fabric API subset is built in instead.

## Implemented
Fabric mod discovery, intermediary->SRG remapping, FabricLoader emulation, entrypoints (main/client/server),
mixin registration (refmaps rewritten), access wideners -> access transformers,
Fabric API subset: Event/EventFactory, ServerLifecycleEvents, ServerTickEvents, CommandRegistrationCallback.

## Not implemented
The rest of Fabric API (registries, networking, rendering, resources, transfer, ...), MixinExtras,
Registry.register() bridging to Forge registries, Kotlin/other language adapters.
