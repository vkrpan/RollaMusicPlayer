# RollaMusicPlayer - Project Memory

Before the first tool call of any session — and before writing or
proposing a plan, not merely before executing one — invoke the
task-observer skill AND execute its Session Start Protocol (storage
check, frontmatter scan, review trigger). Loading the skill and running
the protocol are separate steps; a session that loads the file and stops
has activated nothing. Any turn that will involve a tool call counts; do
not classify the session as "too simple" from its opening message.

Select skills on the DECISION the request is about, not on the artefact it
arrived as. Name what the user is deciding, then match the installed skill
descriptions against that — a request handed over as a file to review
still needs the skill whose description names its subject.

After completing each task, list the observation records written this
session in the same turn and report a one-line summary from that listing
(ids and titles, or "none logged and why") — never from memory of
writing them. This is the activation backstop: it forces a look at the log,
so a session that silently skipped the protocol is discovered at the
first task boundary instead of never.

Loading a skill is not complete until you have queried the observation
log for OPEN observations naming it and read their bodies:
  find "C:/Users/VK/.claude/task-observer-workspace/skill-observations/observation-log" -maxdepth 1 \
    -name '*.md' -exec grep -l "skill:.*<skill-name>" {} +
(Use find, not a bare *.md glob. Under zsh an unmatched glob is an error,
so on an empty log the command never runs and the enclosing block aborts —
and 2>/dev/null does not help, because the redirection belongs to a
command that never starts. This is the first thing that fires on a fresh
install, in every session, for as long as the log is empty.)
Apply their insights to the current work — meaning: let them change what
you do in THIS task. Editing the skill file, or writing the rule into any
other file a later session reads, is acting on the observation and waits
for the review. See "Log, don't act" in SKILL.md: the test is whether the
action leaves a durable change outside the observation log. Run this at every skill load, however many skills load
in one session. The session-start scan does not cover it: that is a
frontmatter sweep over every observation at session start, this is a
body-level lookup for one skill at the moment its rules are applied.

The converse holds too: the grep, and any checkpoint line recording the
load, run only in the same batch as the Skill invocation, and take the
skill name from a load that has actually happened — never from a list of
skills you built to load. A checkpoint line written without the load is a
false record. Before writing to any file a skill reads at run time (a
state file), load that skill: a state file that describes itself is not a
substitute for the skill that owns it.

Whenever superpowers' writing-plans, subagent-driven-development, brainstorming or
test-driven-development loads, also load the user-owned companion skill
`superpowers-extras` in the same batch, if it is installed (staged by the
2026-10-17 observation review), and read its reference file for that host
skill before the host's first action.

The task-observer workspace for this project is:
  C:/Users/VK/.claude/task-observer-workspace
Every path the skill uses derives from that root and nothing else:
  C:/Users/VK/.claude/task-observer-workspace/skill-observations/observation-log/   (the log)
  C:/Users/VK/.claude/task-observer-workspace/skill-observations/cross-cutting-principles.md
  C:/Users/VK/.claude/task-observer-workspace/skill-updates/                        (staging root)
  C:/Users/VK/.claude/task-observer-workspace/skill-updates/PENDING.md              (staging manifest)
Never resolve any of them from the current working
directory — a cwd inside an ephemeral checkout (a git worktree, a temporary
clone) is torn down and takes the log with it. Never place the workspace
inside a skills-discovery directory or any path linked into one. If this
environment mints a separate project identity per checkout, or more than
one agent works this project, the pinned path above is the single shared
location; do not derive one per session, tool or project.

The user's repositories live under:
  [PROJECTS ROOT]
(the root the sibling check sweeps before declaring a skill absent;
observation-log.md, "Skill families and the sibling check").

A subagent dispatched by a session that already runs this protocol does
not run it again and does not write to the log. The controller observes,
because only it sees the whole task and its review; a subagent that
notices something worth logging says so in its final report, and the
controller writes it, running the id snippet per write. A start-up rule
in a project file reaches every agent the project ever dispatches.

An observation file comes into being only through
  bash "<skill directory>/scripts/new-observation.sh" <slug> "/c/Users/VK/.claude/task-observer-workspace"
which prints the path to write into (the helper rejects the C:/ drive-letter form of the same path, so it takes
the /c/ form here). Never by copying another file's
header or counting a listing — including on the turn after a
compaction, when the skill body is out of context.

## Knowledge vault (project memory)

`docs/` is an Obsidian vault and the project's memory: the user's standing rules, decisions, lessons learned, history
and backlog. Claude's private auto-memory holds only a pointer to it. Entry point `docs/Home.md`; note types,
frontmatter and query commands in `docs/_meta/Vault conventions.md`.

- **Vault first.** For any question about the project and before any task, grep the vault for the area
  (`grep -rli "<term>" docs --include=*.md`), read the matching notes and obey every note in `docs/rules/` (index
  `docs/rules/Rules.md`) before reading code or git history.
- **Every change reaches every involved entity.** When code or an `.md` file is created, modified or removed, update
  every vault note that describes it — its feature, module, agent, decision, rule, guide, backlog and timeline notes —
  before reporting the work done. A change with no vault update is unfinished. Subagents report the entities they
  touched; the controller writes the vault.
- **After every compaction**, before resuming: a PostCompact hook saves the summary to `docs/history/sessions/`
  (`status: to-distill`); distil each such note into the entity notes, then mark it `status: distilled`
  (`docs/rules/Save compaction summaries to the vault.md`).
- **Write in the same turn.** A preference the user states ("from now on", "always", "never") → `docs/rules/`; a
  product or design choice → `docs/decisions/`; a lesson learned the hard way → `docs/knowledge/`; a finished phase
  → `docs/history/` and `docs/history/Timeline.md`; opened or closed work → `docs/backlog/Backlog.md`. Update an
  existing note before adding one, mark replaced notes `status: superseded`, and add every note to its folder index.
- **Point, don't copy.** `.claude/rules/`, `.claude/agents/` and `.claude/skills/` stay where the harness loads them;
  specs and plans stay in `docs/superpowers/`. Vault notes link to them.

## Project Overview

**RollaMusicPlayer** is a feature-rich Android music player application built with modern Android development practices. The app focuses on providing an excellent local music playback experience with advanced features like equalizer, tag editing, and comprehensive library management.

## 🔒 Offline & Privacy Architecture

**RollaMusicPlayer is designed as a fully offline, privacy-first application.**

### Architectural Principles

1. **No Network Layer**: The application architecture intentionally excludes any networking components
2. **Local Data Only**: All data persistence uses local storage (Room database, DataStore, file system)
3. **Zero External Communication**: No analytics, crash reporting to external services, or data transmission
4. **Privacy by Design**: User data never leaves the device
5. **Minimal Permissions**: Only READ_MEDIA_AUDIO (Android 13+) or READ_EXTERNAL_STORAGE (older versions)

### Data Storage Strategy

- **Music Files**: Accessed from device storage via MediaStore API
- **Library Database**: Room SQLite database for metadata indexing
- **Playlists**: Stored in local Room database
- **Equalizer Presets**: Stored in local Room database
- **Settings**: DataStore (Preferences) for app configuration and equalizer active-state
- **Artwork**: each track's own embedded picture, decoded once into a per-track cache in app-specific storage
  (`SongArtworkCache`, `cacheDir/song_art`); albums, artists and folders have no artwork and show the placeholder
- **Custom tags** (in progress): a track's tag and subtag IDs live in its own file's `ROLLA_TAGS` field (MP3, FLAC; M4A
  read-only); the two lists, per-list ID counters and the song links are in Room (v6; v7 adds a per-row read
  generation), rebuilt from the files by the library scan
- **Playback State**: The queue, current track, position, shuffle order and repeat mode are saved in Room (v5 `playback_queue`/`playback_state`) and restored, paused, on the next start; Play from a headset or Bluetooth resumes them without opening the app. A System UI media card that keeps the saved track after the process has died, with a Play that resumes the queue the same way, is designed for the stock-Android System UI (AOSP `MediaResumeListener`) and is **not yet device-verified** (`PlaybackService` is a `MediaLibraryService`; no library is browsable). On Samsung One UI (SM-S901B, Android 14) it is not delivered, because One UI never adds new apps to its media resume list, so on that phone the card disappears when the process dies

### Technical Implications

- **No Retrofit/OkHttp**: No HTTP client libraries in dependencies
- **No Firebase**: No analytics, crash reporting, or cloud services
- **No Third-Party SDKs**: Avoid SDKs that collect data or require internet
- **Local Caching Only**: All caching strategies use device storage
- **Offline-First UI**: No loading states for network requests, no sync indicators

## Tech Stack

- **Language**: Kotlin 2.2 (2.2.21, KSP2)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel), multi-module (hybrid feature + core)
- **Build**: Gradle Kotlin DSL with version catalog + convention plugins (build-logic); AGP 8.13.2, Gradle 8.13, `compileSdk 36`
- **Visual style**: One UI–inspired dark (OLED black, blue accent, soft rounded surfaces). Binding spec: `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md` §5–§6 (values) and `.claude/rules/ui-style-guide.md` (rules); tokens and the component kit are implemented in `:core:designsystem`
- **Audio Playback**: Media3 1.11.1 ExoPlayer
- **Database**: Room for local data persistence
- **Dependency Injection**: Hilt 2.57.2 (2.59.x needs AGP 9)
- **Coroutines**: For asynchronous operations
- **Media Session**: For playback control and notification integration
- **Foreground Service**: For background playback

## Core Features

### Essential Features
- **100% Offline Operation** - No internet connection required
- **Privacy Focused** - Zero data collection or external communication
- Local music file playback (MP3, FLAC, WAV, OGG, M4A, AAC)
- Complete playlist management (create, edit, delete, reorder)
- Shuffle and repeat modes (off, all, one)
- Background playback with notification controls
- Persistent playback queue across app restarts (restored paused; resumable from a headset or Bluetooth Play; a System UI media card that resumes it after the process has died is designed for the stock-Android System UI and not yet device-verified; Samsung One UI never adds new apps to its resume list, so it does not keep the card)

### Advanced Audio Features
- 8-band graphic equalizer (40Hz, 80Hz, 160Hz, 315Hz, 630Hz, 1.25kHz, 2.5kHz, 5kHz, 10kHz)
- Equalizer preset management (save/load custom presets)
- Gains-driven response curve visualization while adjusting (no live audio capture — a real
  `Visualizer` was deliberately rejected; it requires `RECORD_AUDIO`, which conflicts with the
  privacy-first positioning)

### Music Library Management
- Automatic music library scanning and indexing
- Metadata extraction and display
- Search across songs, albums, and artists (grouped results; substring match over Room)
- Per-track artwork: every track shows its own embedded picture (lists, Search, queue, mini-player, Now Playing,
  notification, lock screen, media card, widget); no picture shows the white note; albums, artists and folders always
  show the placeholder; the stock-Android resume card always shows the white note
- Full ID3 tag editing (title, artist, album, genre, year, track number, album artist, composer, artwork), with Change
  and Remove artwork (MP3, FLAC and M4A only); Set or Remove artwork for many tracks from the Tracks tab's selection ⋮
- Batch tag editing for multiple files
- Home: six tabs (Favourites · Playlists · Tracks · Albums · Artists · Folders) with a One UI A–Z fast-scroll rail
- Folders browsing (every folder that directly holds tracks) and Folder, Album and Artist detail screens, reached from
  the Home tabs, Search and Now Playing's ⋮

> A **custom user-defined tag system** was cut from v1.0, then asked for again on 2026-11-09 and is in progress: tags
> and subtags stored in each file's `ROLLA_TAGS` field — see `docs/features/Custom tags.md` and
> `docs/decisions/Custom tags live in the file's ROLLA_TAGS field.md`.

### Home Screen Widget
- The current track's own artwork (or the white note)
- Visual timeline/progress bar
- 15-second skip backward/forward buttons
- Previous/next track buttons
- Play/pause toggle
- Real-time updates during playback
- Tappable to open main app

## Project Structure

**Architecture: multi-module, hybrid feature + core (Now in Android style).** The app shell wires
everything; `:core:*` modules hold shared concerns; `:feature:*` modules hold one screen-area each.
See the `setup-modularization` skill for the full graph, convention plugins, and dependency rules.

```
:app                    # Application, MainActivity, NavHost wiring, DI root, app theme entry

:core
├── :core:model         # Domain models + enums (Song, Album, Artist, Playlist, ...). Pure Kotlin.
├── :core:common        # The library order (text/LibraryOrder.kt: indexLetter, sortedByLibraryName), shared by :core:data and :core:designsystem. Dispatcher qualifiers are still per module (e.g. CoreDataIoDispatcher, TagEditorIoDispatcher, WidgetIoDispatcher)
├── :core:database      # Room: entities, DAOs, migrations, MusicDatabase
├── :core:datastore     # DataStore: app settings (theme/dynamic color/speed/skip-silence), equalizer active-state, recent searches
├── :core:data          # Repositories + media scanner + the per-track artwork cache (SongArtworkCache)
├── :core:media         # Media3 PlaybackService (a MediaLibraryService), MediaSession, the queue (QueueState/QueuePublisher), queue save/restore, audio session, equalizer effect
├── :core:designsystem  # Theme (Color/Type/Shape) + model-agnostic components
├── :core:ui            # Model-aware shared composables (SongListItem, list rows, AlbumCard, FeatureCard, SongOptionsHost)
├── :core:tagio         # jaudiotagger IO shared below features: tag helpers, the ROLLA_TAGS custom-tag field (MP3, FLAC; M4A read-only)
├── :core:permissions   # Runtime media permission gate + flow
└── :core:testing       # Fakes, fixtures, test rules

:feature
├── :feature:library    # Home tabs Tracks, Albums, Artists, Folders + Album, Artist and Folder detail screens (no Genres)
├── :feature:search     # Local search across the library
├── :feature:player     # Now Playing, mini-player, queue sheet
├── :feature:equalizer  # Equalizer screen (band sliders, presets UI)
├── :feature:playlists  # Favourites and Playlists tabs, playlist detail, smart playlists, reordering
├── :feature:tageditor  # ID3 read/write screen + scoped-storage consent
├── :feature:settings   # App settings
└── :feature:widget     # Glance home screen widget

:baselineprofile        # Macrobenchmark + Baseline Profile generator (test module)
build-logic/            # Convention plugins (included build)
```

**Dependency direction**: `:feature → :core → :core:model`. Features never depend on each other;
core never depends on a feature; no cycles. Cross-feature flows go through `:core:data` or navigation.

### Agent ↔ Module Ownership (quick map)

- `:core:database` → data-layer-agent · `:core:data` scanner → media-scanning-agent
- `:core:media` → audio-engineer · equalizer effect → equalizer-agent
- `:core:designsystem` → m3-design-system-agent · `:core:ui` & `:feature:*` screens → ui-builder
- `:core:permissions` → permissions-agent · `:feature:widget` → widget-agent
- `:feature:tageditor` IO and `:core:tagio` → tag-editor-agent · `:feature:*` ViewModels → viewmodel-architect
- `:app` NavHost + per-feature nav entries → navigation-agent
- `build-logic/`, root Gradle, version catalog, `:baselineprofile` → build-tooling-agent
- `:core:model` is a shared contract — settle model names once (the codebase uses **`Song`**, not `Track`)

## Development history

v1.0 was built in Phases 0–8 (2026-06-29 to 2026-07-23; prompt docs in `docs/history/v1-phases/`), the One UI redesign
in Phases 1–7 (2026-10-06 to 2026-11-04). See `docs/history/Timeline.md`.

## Key Design Decisions

1. **Media3 over MediaPlayer**: Better API, more features, active development
2. **Jetpack Compose**: Modern UI toolkit, declarative approach
3. **MVVM Architecture**: Clear separation of concerns, testability
4. **Room Database**: Type-safe, compile-time verification
5. **Hilt for DI**: Official Android DI solution, good Compose integration
6. **Foreground Service**: Required for background playback on modern Android
7. **One UI–inspired dark design system**: True-black OLED, single blue accent, heavily rounded surfaces. Values in the redesign spec §5–§6, rules in `.claude/rules/ui-style-guide.md`, implemented as tokens and a component kit in `:core:designsystem`. All UI agents/skills build from these tokens and the kit — never hardcode colors, type, or shapes.

## Important Considerations

- **Offline Operation**: No internet permission requested or used; all features work without connectivity
- **Privacy**: No analytics, no crash reporting to external services, no data collection
- **Local Storage**: All data stored on device; efficient caching and indexing strategies
- **Permissions**: READ_MEDIA_AUDIO (Android 13+), READ_EXTERNAL_STORAGE (older versions) - storage only
- **Scoped Storage**: Handle Android 10+ storage restrictions properly
- **Battery Optimization**: Proper service lifecycle management for background playback
- **Memory Management**: Efficient bitmap loading for artwork from the per-track cache
- **Audio Focus**: Handle audio focus changes properly for local playback
- **MediaSession**: Proper integration for system controls (no Android Auto streaming)

## Current Status

- **v1.0.0** shipped on `main` (2026-07-23). Detail: `docs/history/v1.0 release.md`.
- **One UI redesign** Phases 1–7 complete on `feat/oneui-redesign` (last commit `15821b4`, pushed), waiting for the
  user's merge into `main`. Detail: `docs/history/One UI redesign.md`.
- **Per-track artwork**: committed (`e129f3e`) and pushed on `feat/oneui-redesign` (2026-11-09), device-verified. Spec
  `docs/superpowers/specs/2026-11-08-per-track-artwork-design.md`, plan `docs/superpowers/plans/2026-11-08-per-track-artwork.md`,
  status `docs/features/Per-track artwork.md`.
- **Custom tags** phase 1 of 4 (foundation: `:core:tagio`, Room v6, the scan's file pass, `CustomTagRepository`) built,
  reviewed, device-verified and committed (`dce76dd`, 2026-11-10) on `feat/oneui-redesign`. Phase 2 (assigning: the
  picker sheet, tags in the single and batch tag editors, Room v7) complete, device-walked and committed (`eb9ebcb`,
  2026-11-11). Phase 3 (managing: the Manage tags screen from Home ⋮ and the picker, rename, delete with file clean-up,
  export/import of the tag lists) complete and device-walked (2026-11-12), waiting for the user's commit. Spec
  `docs/superpowers/specs/2026-11-09-custom-tags-design.md`, roadmap `docs/superpowers/plans/2026-11-09-custom-tags-roadmap.md`,
  status `docs/features/Custom tags.md`.
- Backlog, review follow-ups and next steps: `docs/backlog/Backlog.md`. History by date:
  `docs/history/Timeline.md`.

**Last Updated**: 2026-11-12

## Notes

- Target Android API: 24 (Android 7.0) minimum, 34 (Android 14) target, compile 36
- Use Material Design 3 components throughout
- Follow Kotlin coding conventions
- Write unit tests for business logic
- Use Coil for image loading
- Implement proper error handling and user feedback

## Knowledge wiki (Karpathy LLM-wiki pattern)

`docs/wiki/` is an LLM-maintained knowledge base about this project, viewed in Obsidian inside the `docs/` vault (the
"Karpathy LLM Wiki" plugin is installed there with `wikiFolder: wiki`). The plugin or Claude Code writes it; humans read
it. Operations: `/wiki-ingest`, `/wiki-query`, `/wiki-lint` (in `.claude/commands/`) or the plugin's Ingest, Query wiki
and Lint wiki. It is a derived view; the rest of the `docs/` vault stays the authored record ("Knowledge vault" above).

### Layers
- **Raw sources (read-only during wiki work — never edit them as part of a wiki operation):**
  `CLAUDE.md`, `docs/agents/Ownership map.md` (formerly `OWNERSHIP.md`), `README.md`,
  `docs/history/v1-phases/PHASE*.md`, `docs/superpowers/specs/*.md`, `docs/superpowers/plans/*.md`,
  `.claude/rules/*.md`, `.claude/agents/*.md`, `.claude/skills/*/SKILL.md`. The code itself is the final authority.
- **Wiki:** `docs/wiki/` — `sources/` (one page per raw doc), `entities/` (concrete things: modules,
  classes, screens, routes, agents, DB tables), `concepts/` (decisions, patterns, rules, rejected
  alternatives), `answers/` (filed query answers), plus `index.md` and `log.md`.
- **Schema:** this section.

### Page format
- Filename: kebab-case, unique across the whole `docs/` vault, because Obsidian resolves `[[links]]` by name. Module
  pages are `module-<name>.md` (`module-core-media.md`), since the vault's own module notes use the bare names.
- Frontmatter:
  ```yaml
  ---
  type: source | entity | concept | answer
  summary: One sentence saying what this page is.
  aliases: [AlbumArtworkCacheImpl]
  tags: [module, media]
  sources: ["[[claude-md]]", "[[phase8-polish-release]]"]
  updated: YYYY-MM-DD
  ---
  ```
- Body: `# Title`, the summary repeated as the first line, then content. Link every mention of
  another wiki page with `[[slug]]`. End with `## Related` (links) and, when needed,
  `## Disagreements & open questions`.
- Use the vocabulary in `.claude/rules/model-vocabulary.md` (`Song`, never `Track`).

### Rules
1. **Extend, don't duplicate.** Before creating a page, check `docs/wiki/index.md` and grep `docs/wiki/`
   for the name and its aliases. Update the existing page if there is one.
2. **Rewrite, don't append.** Keep each page a coherent current summary, not a changelog.
3. **Never silently overwrite a conflict.** Record both claims with their sources under
   `## Disagreements & open questions`. If the code can settle it, check the code and say which
   claim is right (cite the file path).
4. **Never hand-edit wiki pages.** The only manual edit is `reviewed: true` in a curated page's frontmatter; such a
   page is protected: don't edit it, propose changes in chat instead.
5. **Code and CLAUDE.md win.** The wiki is a derived view; when it disagrees with them, the wiki
   is wrong.
6. Every operation updates `docs/wiki/index.md` (if pages changed) and appends to `docs/wiki/log.md` as
   `## [YYYY-MM-DD] ingest | Title` (or `query` / `lint`) followed by bullets.
7. Wiki operations never touch code, build files, or raw sources.
8. **Keep it current.** After each phase or major commit, ingest that phase's doc. File a good query answer back as
   a page in `answers/`. Run lint before starting each new phase, and review its counts before applying fixes (in the
   plugin, before "Smart Fix All").

### For other agents
For project context, read `docs/wiki/index.md` first, then the linked pages, before reading a
wiki-covered topic from scratch.