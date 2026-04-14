# Homerun

![Homerun banner](./assets/banner.png)

Homerun is a Minecraft server plugin that lets you reset the world under specific conditions. Though Homerun can reset
your entire server world, it can be set to keep specific chunks or regions intact. With Minecraft 1.18's chunk blending
functionality, Homerun can reset your world while keeping the borders with kept chunks smooth and natural.

Homerun works for 1.21.4 and above. Updates for the plugin are provided for servers running Minecraft 1.21.11. More
information on supported versions can be found in the [Development](#development)
section. [MCKotlin](https://modrinth.com/plugin/mckotlin) ([GitHub](https://github.com/4drian3d/MCKotlin)) is required
for Homerun to work.

> [!WARNING]
> Homerun **directly modifies** your world files during resets. It is **highly recommended** to back up your worlds
> in some other way before using Homerun. Homerun **will persist old worlds** and avoid deleting worlds, but it is still
> a good idea to have backups in case something goes wrong.

## Features

![Screenshot of Minecraft with three worlds overlayed on top of each other. The bottom section of the image, which shows chunks close to the spawn point, remains the same.](./assets/example.png)

* Reset world based on time intervals
* Keep specific chunks intact during resets
* Smooth chunk blending for kept chunks (Minecraft 1.18+)
* Reset the Nether and The End (and keep chunks there too)
* Warnings and countdowns for upcoming resets

### Configuration

Homerun is configured through the `config.yml` file in the `plugins/Homerun` directory. The configuration file itself
has most of the commonly-used features, but a full configuration reference can be found at https://homerun.lmnv.net/.

### Commands

Though Homerun mostly works through configuration files, it also provides some commands for server operators:

* `/homerun reset <rule>` – Immediately reset the world using the specified rule
* `/homerun tpworld <world>` – Teleport to the specified world, keeping position and rotation of the player
* `/homerun reload` – Reload the configuration. This immediately re-processes all reset rules.
* `/homerun reloadcachedchunks` – Reload the chunk cache, which powers reset borders and entry/exit notifications
* `/homerun lockout <enable/disable> <world>` – Enable or disable lockouts for a world. Lockouts prevent a player from
  joining or teleporting into a world that is currently being reset.

### Development

> [!WARNING]
> Due to how Homerun works, it **integrates strongly with Minecraft internals** and directly accesses Minecraft's code,
> [against the suggestion](https://docs.papermc.io/paper/dev/internals/) of PaperMC maintainers.

Because programming against Minecraft internals very frequently causes incompatibilities with Homerun's source code, we
remain committed to supporting only **Minecraft versions released in the last 12 months** available for general use with
Paper. Sometimes, when there are no significant changes to the rest of Minecraft's internal code, a Homerun version may
support multiple Minecraft versions.

Homerun uses semantic versioning. The format, as usual, is `<MAJOR>.<MINOR>.<PATCH>`. Homerun will always increment
major versions whenever the list of supported Minecraft versions changes. Always pick the appropriate major version for
your Minecraft server version.

You can help us out at https://github.com/team-luminova/Homerun. Pull requests are appreciated, and we'll try to give
reviews within a reasonable amount of time. To being developing for Homerun, just clone the repository and open it with
your IDE. Gradle should take care of the rest.

You can find more information on the development of Homerun in the [DEVELOPMENT.md](./DEVELOPMENT.md) file.

## License

```
Copyright 2025 Chlod Alejandro and contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```