# The Tree

An IntelliJ-platform plugin that reshapes the **Project** tool window. It is a
ground-up rework of [luzuojin/FolderCompact](https://github.com/luzuojin/FolderCompact),
extended with a per-module configuration file, arbitrary ordering, hiding, and an
"Uncompacted" grouping node — and made to run across IDEs rather than only IntelliJ IDEA.

## What it does

- **Compacts source folders.** A source root such as `src/main/java` shows as one node
  labelled with its relative path, instead of a deep chain of single-child folders.
- **Compacts declared folders.** Non-source folders you list in `.thetree.yaml` with
  `:compacted: true` (for example `src/main/protobuf`) are surfaced the same way, as a
  single top-level node.
- **Groups the rest under "Uncompacted".** Folders and files that are neither a source
  root nor a declared compacted folder are collected into a virtual **Uncompacted** node,
  so the top level stays focused on what matters. (Toggle: *Group Uncompacted Folders*.)
- **Orders arbitrarily by weight.** Every node has a `:weight` (default `0`). Lower
  weights render **above** higher ones, and the ordering crosses the folder/file divide,
  so a file at `:weight: -1` can sit above a folder at the default `0`. Nodes with equal
  weight fall back to the usual folders-first, then-alphabetical order.
- **Hides nodes.** `:hidden: true` removes a node from the tree.
- **Optionally shows module libraries.** A virtual **Libraries** node per module can be
  switched on. (Toggle: *Show Module Libraries*, off by default.)

All behaviour is toggleable from the Project view's *Appearance* / options menu
(the same gear/⚙ menu that hosts "Compact Directories"):
*Compact Source Folders*, *Group Uncompacted Folders*, *Use .thetree.yaml*,
*Show Module Libraries*.

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
(the Gradle IntelliJ plugin downloads the IDE SDK on first build).

```bash
./gradlew buildPlugin
```

The wrapper pins **Gradle 8.10.2**, which runs on Java 17–23 and is compatible with the
`org.jetbrains.intellij` 1.x plugin used here. If you build from inside the IDE, make sure
the **Gradle JVM** is set to a JDK 17+ (Settings → Build, Execution, Deployment → Build
Tools → Gradle). The IDE's bundled JBR is fine. Older Gradle versions cannot run on a
modern JDK and fail with `Unsupported class file major version …` before compiling
anything — that is a Gradle-vs-JVM mismatch, not a problem with the plugin code.

The installable zip is written to `build/distributions/`. Install it via
**Settings → Plugins → ⚙ → Install Plugin from Disk…**

To try it in a sandbox IDE:

```bash
./gradlew runIde
```

> **Note:** this project was assembled without a compile step in the authoring
> environment (no access to the JetBrains SDK there). It is a complete, coherent source
> project, but expect to run `./gradlew buildPlugin` locally and possibly make small
> version-specific adjustments — see *Version notes* below.

## `.thetree.yaml`

Place the file in a **module content root**. Its structure mirrors the folder tree.

- A key that begins with `:` sets a **property** of the current node.
- Any other key names a **child** folder or file; its value is a nested mapping.
- A key containing `/` is **syntactic sugar** for nested nodes — `src/main/protobuf:` is
  the same as `src: { main: { protobuf: { … } } }`. Properties apply to the deepest segment.
- If the same node is described more than once, **later properties win**, and children
  merge recursively.

### Properties

| Property     | Type      | Default | Meaning                                                                 |
|--------------|-----------|---------|-------------------------------------------------------------------------|
| `:compacted` | boolean   | `false` | Surface this folder at the top level as a single compacted node.        |
| `:weight`    | integer   | `0`     | Ordering key. **Lower renders above higher**; crosses the folder/file divide. |
| `:hidden`    | boolean   | `false` | Do not display this node.                                               |

Unknown `:`-properties are ignored, so the format is forward-compatible.

### Example

```yaml
# Treat a non-source folder as a compacted top-level node, pulled to the top.
src/main/protobuf:
  :compacted: true
  :weight: -10

# Another declared compacted folder, pushed below default-weight nodes.
generated:
  :compacted: true
  :weight: 20

# Hide a folder entirely.
build:
  :hidden: true

# Order individual files.
README.md:
  :weight: -100
CHANGELOG.md:
  :weight: -99

# Deep nesting (weight applies to docs/internal only when that branch is shown).
docs:
  :weight: 5
  internal:
    :hidden: true
```

The file is re-read automatically when you save it; the project view refreshes.

## Ordering caveat (Sort by Type / Folders Always on Top)

IntelliJ sorts project-view children with a comparator, and which comparator runs depends
on the view's sort settings:

- With **"Folders Always on Top"** enabled (the default), the platform uses a
  type-sort-weight comparator where **higher** sorts earlier. The plugin overrides
  `getTypeSortWeight()` so your configured `:weight` dominates.
- With that option **off**, the platform uses `getWeight()` where **lower** sorts earlier.
  The plugin overrides `getWeight()` too.

If you additionally enable **"Sort by Type"**, the view groups by file type *first*; that
grouping can appear to override `:weight`. If ordering ever looks inverted for your sort
configuration, flip the sign of the weight term in
`dev.thetree.nodes.Ordering#typeSortWeight`. All ordering math lives in that one class.

## Extending: gitignore-style patterns (not yet enabled)

Pattern matching (`*`, `*.ext`, `**`) is intentionally stubbed but architecturally ready.
Matching goes through `dev.thetree.config.NodeMatcher`:

- `NodeMatcher.of(key)` currently returns a `Literal` matcher (exact segment match).
- The interface already exposes `isDeep()`, `isLiteral()`, `matches(segment)`, and
  `pattern()`, and `NodeConfig` keeps `patternChildren` separate from `literalChildren`,
  resolving literals first and then patterns.

To add patterns: implement a `Glob`/`DeepWildcard` `NodeMatcher`, have `NodeMatcher.of`
detect `*`, `*.ext`, and `**` and return it, and (for `**`) handle multi-segment matching
in `NodeConfig#child`. No other layer needs to change.

## Project layout

```
src/main/java/dev/thetree/
  PathUtil.java                     path helpers + segments()
  TheTreeStructureProvider.java     the TreeStructureProvider (entry point)
  config/
    NodeMatcher.java                literal matcher now; pattern-ready
    NodeConfig.java                 one node's properties + children
    TheTreeConfig.java              root config + resolution + declared-compacted scan
    TheTreeConfigParser.java        SnakeYAML → NodeConfig
    TheTreeConfigService.java       project service, caches by modification stamp
    TheTreeVfsListener.java         refreshes the view when the file changes
  nodes/
    Ordering.java                   all weight math
    CompactedFolderNode.java        compacted folder (relative-path label)
    OrderedDirectoryNode.java       weight-carrying directory wrapper
    OrderedFileNode.java            weight-carrying file wrapper
    UncompactedFolderNode.java      virtual "Uncompacted" node
    ModuleLibrariesNode.java        virtual "Libraries" node
  settings/
    TheTreeConfigState.java         persistent toggles
    TheTreeActions.java             the toggle actions
src/main/resources/META-INF/plugin.xml
.thetree.yaml                       example config (this repo's own)
```

## Version notes

The node wrappers call platform superclass constructors that have varied slightly across
platform versions (`PsiDirectoryNode` with a `ViewSettings`/filter argument, `PsiFileNode`
3-arg constructor). If a specific target IDE version fails to compile, adjust those
`super(...)` calls in the `nodes/` classes — the logic around them is unaffected.

## Credits

Derived from FolderCompact by luzuojin. The compaction and library-node concepts come from
that project; the configuration file, ordering, hiding, Uncompacted grouping, and cross-IDE
packaging are new.
