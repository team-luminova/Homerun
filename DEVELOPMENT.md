# Development

Some development information for Homerun.

Skip to [development](#development) if you just want to contribute code. Otherwise, read on for some general information
about how Homerun works and how it's developed.

## Minecraft internals (`net.minecraft.server`; "NMS")

Homerun uses Minecraft internals (NMS) to implement the main meat of the program: world resets and NBT data editing.
This is done because chunk copying is an offline operation, which cannot happen while the server is actively running and
ticking a world. The same applies to editing the NBT data of the `level.dat` file and other related player data files.
In addition, it helps keep the overall plugin size down, as there's no need to import some other NBT editing library
(as Spigot/Paper does not come with any of its own).

This comes with a big caveat, however: Minecraft internals are not guaranteed to be stable across versions, and in fact
are often changed in ways that break plugins that rely on them. This means that Homerun may require updates to work with
new Minecraft versions, even between major versions (e.g. NBT editing code from 1.21.4 is not compatible with 1.21.5).
For this reason, PaperMC maintainers [do not give any support](https://docs.papermc.io/paper/dev/internals/) for plugins
that directly use NMS.

## Versioning

Homerun uses semantic versioning. The format, as usual, is `<MAJOR>.<MINOR>.<PATCH>`. Homerun will always increment
major versions whenever the minimum supported Minecraft version changes. Whether the NMS code that Homerun relies on is
compatible between versions is the main determinant on whether a new "major" version of Homerun is needed or if an
existing version can simply be updated to support the new Minecraft version.

Currently, versioning is based on the following supported Minecraft versions:

| Homerun version | Minecraft version | Type  | Notes                                                                                                                                                      |
|-----------------|-------------------|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0.1.0           | 1.21.4            | Alpha | Only ever tested for 1.21.4. Never got tested for any earlier versions.                                                                                    |
| 0.2.0           | 1.21.5 – 1.21.10  | Alpha | Changes to NBT tags from using `null` to `Optional` make earlier Minecraft versions incompatible with 1.21.5 code.                                         |
| 0.3.0           | 1.21.11           | Alpha | Changes to `CompoundTag`'s `remove` method signature (now returns `Tag` instead of `void`) make earlier Minecraft versions incompatible with 1.21.11 code. |

Each major version of Homerun is found in a branch that matches the minimum Minecraft version it supports. For example,
the `mc/1.21.5` branch contains the code for Homerun 0.2.0, which supports Minecraft versions 1.21.5 to 1.21.10. The
`main` branch always tracks the latest Minecraft version supported. A new branch will be created as soon as support for
a new Minecraft version is added that requires a new major version of Homerun.

## Support

**Only Minecraft versions released in the last 12 months are supported.** This is to ensure that Homerun remains
maintainable and does not require constant updates to support older Minecraft versions, which may have different NMS
code that is incompatible with newer versions. Code which causes significant data loss or security issues may
occasionally be backported to older versions, but this is not guaranteed and will be done on a case-by-case basis.

There are no commitments on how early support for a new Minecraft version will be added. If a new Minecraft version is
released and a new Homerun version isn't available within a month after the respective PaperMC version goes live, feel
free to file a [version request](https://github.com/luminova-osu/Homerun/issues).

Minecraft versions older than the last 12 months will not get official support. If you need a feature only available in
newer Minecraft versions but are stuck on an older version, you can try backporting the code yourself. If you want to
request official support for an older Minecraft version, please reach out to us at
[`homerun@lmnv.net`](mailto:homerun@lmnv.net).

Loaders that aren't Paper may work with Homerun, but there is currently no official support for them. If you're
willing to write tests and fix any issues that arise from using a different loader, feel free to submit a pull request
with the necessary changes to support it. Theoretically, forks or extensions of Paper (
e.g. [Purpur](https://purpurmc.org/)) should work, but loaders that Paper forks (i.e. Bukkit
and [Spigot](https://www.spigotmc.org/)) will not work, as Homerun was developed and tested with Paper's API in mind.

## Development

Homerun is developed with Kotlin. All new code must be written in Kotlin, unless it is infeasible to write it in Kotlin.

The project uses Gradle as its build system, and the `build.gradle.kts` file contains all the necessary configuration
for building and testing the plugin. The `src/main/kotlin` directory contains the main source code for the plugin, while
the `src/test/kotlin` directory contains unit tests.

### Building

No prior setup should be required to build the plugin from scratch. Just clone the repository, run `./gradlew build`,
and the plugin JAR will be found in `build/libs` as `homerun-<version>-all.jar`. Versions which are not tagged with a
specific version number have a "SNAPSHOT" suffix, which indicates that they are not stable and may contain breaking
changes.

### Running a test server

When testing the plugin via `./gradlew runServer`, [MCKotlin](https://github.com/4drian3d/MCKotlin) (which is a hard
dependency for the plugin) will automatically be downloaded and loaded during server
startup. [ViaVersion](https://github.com/ViaVersion/ViaVersion) will also be automatically downloaded and loaded, not as
a dependency but as a development aid (especially when you have to switch between Minecraft versions).

### Pull requests

Pull request should almost always be attached to a related issue, unless the change is simple enough as to not require
one. All code should be well-documented and follow the existing code style. If you're anything new, please also add unit
tests for it, if possible.

Pull requests will be reviewed in due time by maintainers. Feel free to add any one of them as a reviewer for your PR.
If it's been a few days and no one has gotten back to you yet, feel free to ping one of them in a comment on the PR.

## Code of Conduct

This repository follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating in this
project, you are expected to uphold this code. Please report any unacceptable behavior to the maintainers via email at
[`homerun@lmnv.net`](mailto:homerun@lmnv.net).
