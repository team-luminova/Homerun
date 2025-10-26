# Homerun

![Homerun banner](./assets/banner.png)

Homerun is a Minecraft server plugin that lets you reset the world under specific conditions. Though Homerun can reset
your entire server world, it can be set to keep specific chunks or regions intact. With Minecraft 1.18's chunk blending
functionality, Homerun can reset your world while keeping the borders with kept chunks smooth and natural.

Minecraft can run **up to Minecraft 1.21.4**. Later versions are supported only on a best-effort asis, as chunk blending
does not work properly.

> [!WARNING]
> Homerun **directly modified** your world files during resets. It is **highly recommended** to back up your worlds
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

Homerun is configured through a `config.yml` file (see the defaults [here](./src/main/resources/config.yml)). It
currently accepts one top-level key: `reset_rules`, which is a list of reset rules. Each reset rule has certain
conditions and parameters, and can have a name, be disabled, or have warnings.

#### Reset rules

| Key          | Default | Type                             | Description                                                                                              |
|--------------|---------|----------------------------------|----------------------------------------------------------------------------------------------------------|
| `name`       | *none*  | `string` (optional)              | An optional name for the reset rule.                                                                     |
| `disabled`   | `false` | `boolean` (optional)             | If true, this rule will be disabled. This can be used to easily disable rules.                           |
| `conditions` | *none*  | list of ResetCondition           | A set of conditions to be used for resetting the server. See [Reset conditions](#reset-conditions) below |
| `parameters` | *none*  | ResetParameters                  | A set of parameters for the reset. See [Reset parameters](#reset-parameters) below                       |
| `warnings`   | *none*  | list of ResetWarnings (optional) | A list of warnings to be issued before the reset occurs. See [Warnings](#warnings) below                 |

#### Reset conditions

Reset conditions are listed with the settings they need. The following reset conditions are available:

* `cron` – Reset the world on a cron schedule (see [crontab.guru](https://crontab.guru/) for help with cron syntax). The
  cron is based on the server's local time.
  ```yaml
  conditions:
    - cron: "0 0 * * *" # Reset every day at midnight
  ```
  ```yaml
  conditions:
    - cron: "0 0 1 * *" # Reset on the first of the month at midnight
  ```
  ```yaml
  conditions:
    - cron: "0 0 1 */2 *" # Reset on every second month (first of odd numbered months)
  ```
* `always` – Always reset the world when this rule is checked.
  > **WARNING:** Using this condition when your world requires a restart (i.e. `restart: true` in parameters) may cause
  > an infinite restart loop.
  ```yaml
  conditions:
    - always: true
  ```

#### Reset parameters

Reset parameters define how the reset should be performed. The following reset parameters are available:

| Key                        | Default  | Type                   | Description                                                                                                                                                                                                                                                                                                                                                                                                                               |
|----------------------------|----------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `retained_chunks`          | *none*   | list of ChunkSelectors | A list of chunk selectors defining which chunks to keep during the reset. See [Chunk selectors](#chunk-selectors) below.                                                                                                                                                                                                                                                                                                                  |
| `world`                    | *none*   | `string` (optional)    | The world to reset. If not specified, the main world (`level-name` in `server.properties`) will be reset.                                                                                                                                                                                                                                                                                                                                 |
| `target_world_pattern`     | *none*   | `string` (optional)    | A pattern for the target world name when creating a new world during reset. Available patterns include: `world` (current world name), `timestamp` (current UNIX timestamp), `source_seed` (seed of old world), `reset_count` (how many resets has this world been through)                                                                                                                                                                |
| `modify_server_properties` | `false`  | `boolean` (optional)   | If true, Homerun will modify the `server.properties` file to update the `level-name` to the new world name after a reset. If you are using this, `server.properties` must be writable by the server. Enabling `restart` is highly advised if you set this to `true`.                                                                                                                                                                      |
| `restart`                  | `false`  | `boolean` (optional)   | If true, the server will automatically restart after the reset. This requires that your server is set up to restart automatically when it stops. Most hosts allow this, though if you are locally hosting, you need to provide a [restart script](https://gist.github.com/Prof-Bloodstone/6367eb4016eaf9d1646a88772cdbbac5) in `spigot.yml`.                                                                                              |
| `outside_player_behavior`  | `spawn`  | OutsidePlayerBehavior  | Defines what to do with players outside of retained chunks after a reset. Available behaviors include: `spawn` (teleport player to their spawn point), `kill` (kills the player), `world_spawn` (teleport player to the world spawn), `ignore` (do nothing, player may suffocate), `highest` (teleport player to highest block at their X/Z pre-reset), and `closest` (teleport the player to the closest block at their X/Y/Z pre-reset) |
| `nether_behavior`          | `normal` | DimensionResetBehavior | Defines how to handle the Nether dimension during a reset. Available behaviors include: `normal` (reset the Nether like any other world), `wipe` (recreates the Nether based on the new world's seed), `copy` (copies the Nether from the previous world without resetting it), and `rename` (renames the previous Nether to match the new world's name)                                                                                  |
| `end_behavior`             | `normal` | DimensionResetBehavior | Defines how to handle The End dimension during a reset. Available options match the options for the Nether.                                                                                                                                                                                                                                                                                                                               |

##### Chunk selectors

Chunk selectors define which chunks should be kept during a reset. The following chunk selectors are available:

* `from_world_spawn` – Keep chunks within a certain radius from the world spawn.
  ```yaml
  retained_chunks:
    - from_world_spawn: 100 # Keep a square area of 100x100 chunks centered on the world spawn
  ```
  ```yaml
  retained_chunks:
    # Keep a rectangular area of 10x20 chunks centered on the world spawn
    - from_world_spawn:
        x: 10
        z: 20
  ```
  ```yaml
  retained_chunks:
    # Keep a rectangular area of radius 50 chunks centered on the world spawn
    - from_world_spawn:
        north: 50
        south: 50
        east: 50
        west: 50
  ```
* `specific_chunks` – Keep specific chunks by their coordinates. Note that this uses chunk coordinates, not block
  coordinates.
  ```yaml
  retained_chunks:
    - specific_chunks:
        - [ 0, 0 ] # Keep chunk (0, 0)
        - x: 1
          z: -1 # Keep chunk (1, -1)
  ```

#### Reset warnings

Warnings provide a way to notify players of an upcoming reset. The following warning types are available:

* `type: boss_bar` – Displays a boss bar to all players with a custom message and countdown.
  ```yaml
  warnings:
    - type: boss_bar
      message: "World reset in {time}!"
      countdown: 60 # Show the boss bar for 60 seconds before the reset
  ```
* `type: player_list` – Displays a message in the player list header and footer with a custom message and countdown.
  ```yaml
  warnings:
    - type: player_list
      position: header # Can be 'header' or 'footer'
  ```
* `type: chat_message` – Sends a chat message to all players at specified intervals (in seconds before reset).
  ```yaml
  warnings:
    - type: chat
      # 1 day, 12 hours, 6 hours, 1 hour, 10 minutes, 5 minutes, 1 minute, 30 seconds, 10 seconds, 5 seconds, 4 seconds, 3 seconds, 2 seconds, 1 second
      intervals: [ 86400, 43200, 21600, 3600, 600, 300, 60, 30, 10, 5, 4, 3, 2, 1 ]
  ```

### Commands

Though Homerun mostly works through configuration files, it also provides some commands for server operators:

* `/reset <rule>` – Immediately reset the world using the specified rule
* `/tpworld <world>` – Teleport to the specified world

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