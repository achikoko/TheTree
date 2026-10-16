# The Tree

> **Disclaimer — this project is mostly written by AI.**
> The bulk of the code, the configuration format and this documentation were produced with an AI
> coding assistant, then reviewed and tested by hand. Treat it accordingly: it works, but it has
> not had the mileage of a human-written plugin. Bug reports and patches are welcome.

An IntelliJ-platform plugin that reshapes the **Project** tool window. It is a
ground-up rework of [luzuojin/FolderCompact](https://github.com/luzuojin/FolderCompact),
extended with a per-module configuration file, arbitrary ordering, and hiding — and made to run across IDEs rather than only IntelliJ IDEA.

## What it does

- **Compacts source folders.** A source root such as `src/main/java` shows as one node
  labelled with its relative path, instead of a deep chain of single-child folders.
- **Compacts declared folders.** Non-source folders you list in `.thetree.yaml` with
  `/compacted: true` (for example `src/main/protobuf`) are surfaced the same way, as a
  single top-level node.
- **Orders arbitrarily by weight.** Every node has a `/weight`. Lower weights render **above** higher
  ones, and the order crosses the folder/file divide whether *Folders Always on Top* is on or off.
- **Hides nodes.** `/hidden: true` removes a node from the tree.
- **Optionally shows module libraries.** A virtual **Libraries** node per module can be
  switched on. (Toggle: *Show Module Libraries*, off by default.)

All behavior is toggleable from the Project view's *Appearance* / options menu
(the same gear/⚙ menu that hosts "Compact Directories"):
*Compact Source Folders*, *Use .thetree.yaml*, *Show Module Libraries*.

## Cross-IDE compatibility

The plugin depends only on `com.intellij.modules.platform` — **not** on
`com.intellij.java`. Every API it uses (project roots, `PsiDirectoryNode`,
`ProjectFileIndex`, `ModuleRootManager`, the library element nodes) is part of the
platform. That is what lets it load in **IntelliJ IDEA, PyCharm, PhpStorm**, and other
IntelliJ-based IDEs from the same build artifact.

SnakeYAML is bundled with the plugin (see `build.gradle`) rather than relying on any
IDE-bundled YAML support, so `.thetree.yaml` parsing works identically everywhere.

## Building

You need a JDK **17 or newer** and network access to the JetBrains/IntelliJ repositories
(the Gradle IntelliJ plugin downloads the IDE SDK on first build — expect a few minutes and
a few hundred MB the first time).

```bash
./gradlew assemble        # or: ./gradlew buildPlugin — same artifact
```

Either task writes the installable plugin to:

```
build/distributions/TheTree-<version>.zip
```

`<version>` comes from `version` in `build.gradle` (`0.1.2` at the time of writing). That zip
**is** the plugin — do not unpack it, the IDE takes it as-is. Inside it is the usual layout:

```
TheTree/lib/instrumented-TheTree-<version>.jar   the plugin itself
TheTree/lib/snakeyaml-2.2.jar                    bundled YAML parser
TheTree/lib/searchableOptions-<version>.jar      generated settings-search index
```

The bare jar in `build/libs/` is **not** installable on its own: it has no SnakeYAML and no
plugin directory layout. Always install the zip from `build/distributions/`.

To wipe previous output before a release build:

```bash
./gradlew clean assemble
```

### Build troubleshooting

The wrapper pins **Gradle 8.10.2**, which runs on Java 17–23 and is compatible with the
`org.jetbrains.intellij` 1.x plugin used here. If you build from inside the IDE, make sure
the **Gradle JVM** is set to a JDK 17+ (Settings → Build, Execution, Deployment → Build
Tools → Gradle). The IDE's bundled JBR is fine. Older Gradle versions cannot run on a
modern JDK and fail with `Unsupported class file major version …` before compiling
anything — that is a Gradle-vs-JVM mismatch, not a problem with the plugin code.

## Installing into an IDE

1. Open **Settings/Preferences → Plugins** (`Ctrl+Alt+S` / `⌘,`).
2. Click the **⚙ gear** icon next to the search field → **Install Plugin from Disk…**
3. Select `build/distributions/TheTree-<version>.zip`.
4. Click **Restart IDE** when prompted. The plugin only takes effect after the restart.
5. Check it landed: **Settings → Plugins → Installed** should list **The Tree**, and the
   Project tool window's **⚙ → Appearance** menu should now offer *Compact Source Folders*,
   *Use .thetree.yaml* and *Show Module Libraries*.

The same zip installs in any IntelliJ-platform IDE (IDEA, PyCharm, PhpStorm, WebStorm, GoLand,
CLion, RubyMine, Rider) on build **222** (2022.2) or newer.

**Updating:** repeat the steps above with the new zip — installing over an existing version
replaces it, no need to uninstall first. **Uninstalling:** Settings → Plugins → Installed →
The Tree → ⚙ → Uninstall, then restart.

### Trying it without installing

`runIde` launches a throwaway sandbox IDE with the plugin already loaded, leaving your real IDE
untouched:

```bash
./gradlew runIde
```

## `.thetree.yaml`

Place the file in a **module content root**. Its structure mirrors the folder tree.

- A key that begins with `/` sets a **property** of the current node.
- Any other key names a **child** folder or file, and may be a
  [gitignore-style pattern](#pattern-matching); its value is a nested mapping.
- A key containing `/` is **syntactic sugar** for nested nodes — `src/main/protobuf:` is
  the same as `src: { main: { protobuf: { … } } }`. Properties apply to the deepest segment.
- If the same node is described more than once, **later properties win**, and children
  merge recursively. This holds even for the very same key repeated, so you can split a
  node's settings into blocks and comment one out without touching the others:
  ```yaml
  src/main: { /compacted: true }
  # src/main: { /hidden: true }
  ```
- `.` segments are ignored: `./src/main` is the same as `src/main`.

### Properties

| Property     | Type      | Default       | Meaning                                                                 |
|--------------|-----------|---------------|-------------------------------------------------------------------------|
| `/compacted` | boolean   | `false`       | Surface this folder at the top level as a single compacted node.        |
| `/weight`    | integer   | `20` / `30`   | Ordering key. **Lower renders above higher**; crosses the folder/file divide. Unset means the IDE default: `20` for folders, `30` for files. |
| `/hidden`    | boolean   | `false`       | Do not display this node.                                               |

Unknown `/`-properties are ignored, so the format is forward-compatible.

### Example

```yaml
# Treat a non-source folder as a compacted top-level node, pulled to the top.
src/main/protobuf:
  /compacted: true
  /weight: -10

# Another declared compacted folder, pushed below default-weight nodes.
generated:
  /compacted: true
  /weight: 20

# Hide a folder entirely.
build:
  /hidden: true

# Order individual files.
README.md:
  /weight: -100
CHANGELOG.md:
  /weight: -99

# Deep nesting (weight applies to docs/internal only when that branch is shown).
docs:
  /weight: 5
  internal:
    /hidden: true

# Patterns. Quote anything starting with '*' — YAML would otherwise choke on it.
'*.iml':
  /hidden: true
'**/*.log':
  /hidden: true
```

The file is re-read automatically when you save it; the project view refreshes.

## The weight scale (Folders Always on Top / Sort by Type)

Ordering behaves the same whether **"Folders Always on Top"** is on or off. The plugin overrides
`getTypeSortWeight()` — the hook the IDE uses when that option is on — to return the same value as
`getWeight()`: the configured `/weight` when one is declared, and the platform's own default
otherwise. Both sort modes therefore read one single scale.

That default scale is the IDE's: **`20` for folders, `30` for files**. So without any configuration
folders still come first, and you position a node relative to them by picking a weight:

| `/weight` | Where the node lands             |
|-----------|----------------------------------|
| `< 20`    | above every folder               |
| `20`–`30` | between the folders and the files|
| `> 30`    | below every file                 |

Nodes sharing a weight keep the IDE's usual order (alphabetical, or by type under *Sort by Type*).

## Pattern matching

Child keys are not limited to exact names — they may be gitignore-style patterns.

| Pattern    | Matches                                                        |
|------------|----------------------------------------------------------------|
| `src`      | exactly the segment `src` (case-sensitive)                      |
| `*`        | any single segment                                              |
| `*.iml`    | any single segment ending in `.iml`                             |
| `file?.txt`| `?` stands for exactly one character                            |
| `**`       | zero or more nested segments                                    |

`*` and `?` never cross a `/`: they match **within one segment**. Only `**` spans levels, and it
can consume zero segments, so `'**/*.log':` matches `a.log` as well as `build/tmp/a.log`.

Patterns compose with the slash sugar, so `'src/**/generated':` is a valid key.

### Precedence

For each path segment the resolver tries, in order:

1. the **exact literal** child, if one is declared;
2. **glob** children, most specific first — the pattern with more literal (non-`*`, non-`?`)
   characters wins, ties broken alphabetically for determinism;
3. the `**` child last, trying every possible number of consumed segments.

The first branch that resolves all the way to the end of the path wins. That is what lets a
targeted literal re-enable something a broad pattern hid:

```yaml
'*gradle*':          # hides gradle/, gradlew, gradlew.bat, gradle.properties, build.gradle …
  /hidden: true
'gradle.properties': # … except these two, which the literal match rescues
  /hidden: false
'build.gradle':
  /hidden: false
```

### YAML quoting

A bare `*` at the start of a scalar is a YAML alias, so patterns generally need quotes:
`'*.iml':`, `'**/*.log':`. Quoting keys that do not need it is harmless.

### Implementation

Matching lives in `io.github.achikoko.thetree.config.NodeMatcher`: `NodeMatcher.of(key)` returns a
`Literal`, a `Glob` (compiled to an anchored regex) or `Deep` (`**`). `NodeConfig` keeps
`patternChildren` separate from `literalChildren`, and `TheTreeConfig#resolve` performs the
backtracking walk that gives `**` its multi-segment behavior.

## Project layout

```
src/main/java/io/github/achikoko/thetree/
  PathUtil.java                     path helpers + segments()
  NodeWeights.java                  weight lookup straight from a VirtualFile
  TheTreeStructureProvider.java     the TreeStructureProvider (entry point)
  config/
    NodeMatcher.java                literal / glob / ** segment matchers
    NodeConfig.java                 one node's properties + children
    TheTreeConfig.java              root config + backtracking resolution + compacted scan
    TheTreeConfigParser.java        SnakeYAML → NodeConfig
    TheTreeConfigService.java       project service, caches by modification stamp
    TheTreeVfsListener.java         refreshes the view when the file (or the tree) changes
  nodes/
    Ordering.java                   weight math for the virtual nodes
    CompactedFolderNode.java        compacted folder (relative-path label)
    CompactedFolderDecorator.java   relabels roots the platform builds itself
    OrderedDirectoryNode.java       weight-carrying directory wrapper
    OrderedFileNode.java            weight-carrying file wrapper
    UnorderedDirectoryNode.java     directory wrapper that overrides `getTypeSortWeight`
    UnorderedFileNode.java          file wrapper that overrides `getTypeSortWeight`
    ModuleLibrariesNode.java        virtual "Libraries" node
  settings/
    TheTreeConfigState.java         persistent toggles
    TheTreeActions.java             the toggle actions
  view/                             experimental "The Tree" pane (see below)
    TheTreeProjectViewPane.java     pane owning its own sort comparator
    TheTreeNodeComparator.java      weight-first comparator
src/main/resources/META-INF/plugin.xml
.thetree.yaml                       example config (this repo's own)
```

## Experimental: the "The Tree" pane

The plugin also registers a second tab in the Project tool window, named **The Tree**. It behaves
like the standard Project pane but owns its sort comparator, which reads each node's weight from its
`VirtualFile` *after* the whole `TreeStructureProvider` chain has run. That makes ordering immune to
downstream providers (Maven pom nodes, Java class nodes, …) rebuilding our nodes and dropping the
weight overrides.

Known rough edge: "Select in Project View" / scroll-from-source still targets the standard pane. To
drop the experiment, remove the `<projectViewPane>` line from `plugin.xml` and delete the `view`
package together with `NodeWeights.java`.

## Version notes

The node wrappers call platform superclass constructors that have varied slightly across
platform versions (`PsiDirectoryNode` with a `ViewSettings`/filter argument, `PsiFileNode`
3-arg constructor). If a specific target IDE version fails to compile, adjust those
`super(...)` calls in the `nodes/` classes — the logic around them is unaffected.

## Credits

Derived from FolderCompact by luzuojin. The compaction and library-node concepts come from
that project; the configuration file, ordering, hiding, and cross-IDE packaging are new.
