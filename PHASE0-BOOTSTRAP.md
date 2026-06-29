# Phase 0 Bootstrap — Claude Code Prompt Sequence

A runnable starting script. Paste each prompt into **Claude Code** (run from the repo root), in order.
After every prompt, wait for the **✅ checkpoint** before moving on — don't batch steps.
Commit at each `git commit` marker so you always have a green fallback.

> Cadence: small vertical slices, build green between steps, review before merge. If a step
> reveals friction between agents, fix `OWNERSHIP.md` / the agent before scaling up.

---

## Prerequisites (one-time, before prompting)

- [ ] Android Studio + Android SDK (API 34), JDK 17 installed
- [ ] Claude Code installed and launched from `C:\Users\VK\Desktop\RollaMusicPlayer`
- [ ] `.claude/` fully populated: all agents, skills, and rules moved in from the outputs folder
- [ ] Track→Song rename applied per `.claude/rules/model-vocabulary.md`
- [ ] `git init` done (so you can commit per slice)
- [ ] An emulator or device available (AOSP/Google-APIs image)

---

## Prompt 0 — Orient (no code yet)

```
Read CLAUDE.md, OWNERSHIP.md, and everything in .claude/rules/. Confirm you understand:
(1) the hybrid feature+core multi-module structure, (2) the offline-first / no-network
constraints, and (3) that the canonical audio-item model is `Song` — never `Track`.
Do NOT write code yet. Summarize the Phase 0 plan (scaffold → :core:model → vertical slice)
and list, per step, which agents and skills you will use.
```

✅ **Checkpoint:** it restates the plan and names the right agents/skills. If it proposes networking or `Track`, stop and correct before continuing.

---

## Step 1 — Scaffold the skeleton

### Prompt 1a — Gradle + modules
```
Use the build-tooling-agent and the setup-modularization skill. Create the Gradle scaffold only
(no feature code): settings.gradle.kts including every module in the CLAUDE.md module graph;
root build.gradle.kts; gradle/libs.versions.toml with versions for Kotlin, AGP, Compose BOM,
Hilt, Media3, Room, DataStore, Coil, Glance, coroutines, and JUnit; and a build-logic included
build with convention plugins (rolla.android.application, rolla.android.library.compose,
rolla.android.feature, rolla.android.hilt, rolla.android.room, rolla.jvm.library).
Create each module directory with a thin build.gradle.kts and namespace. minSdk 24, targetSdk 34.
Then run ./gradlew assembleDebug and fix until it builds.
```
✅ **Checkpoint:** `./gradlew assembleDebug` exits 0 on the empty shell.

### Prompt 1b — Quality gates
```
Use the build-tooling-agent and the setup-static-analysis skill. Add detekt + Spotless(ktlint)
with the project config (allow PascalCase @Composable names), the checkNoNetwork verification
task, a pre-commit hook, and a GitHub Actions ci.yml that runs build + tests + detekt +
spotlessCheck + checkNoNetwork. Run ./gradlew check and confirm all gates execute.
```
✅ **Checkpoint:** `./gradlew check` runs detekt, spotlessCheck, and checkNoNetwork (passing on the empty shell).

### Prompt 1c — App shell
```
Create the :app shell: RollaApp (@HiltAndroidApp Application), MainActivity using the
implement-edge-to-edge-and-insets skill (enableEdgeToEdge before setContent) hosting a placeholder
RollaMusicPlayerTheme and an empty NavHost (navigation-agent owns the graph). AndroidManifest must
declare NO internet permission. Build and launch on the emulator showing a blank themed screen.
```
✅ **Checkpoint:** app launches edge-to-edge to a blank themed screen; manifest has no INTERNET.

```
git add -A && git commit -m "chore: multi-module scaffold + tooling + app shell"
```

---

## Step 2 — Lock the domain model

### Prompt 2 — :core:model
```
Use :core:model and treat .claude/rules/model-vocabulary.md as the source of truth. Create the
domain models as pure-Kotlin (no Android deps): Song (canonical — never Track), Album, Artist,
Playlist, EqualizerPreset, and enums RepeatMode, ShuffleMode, with exactly the fields listed in
model-vocabulary.md. Add unit tests for any model-level logic. Build.
```
✅ **Checkpoint:** `:core:model` compiles as a JVM library; `./gradlew assembleDebug` green. No `Track` type anywhere.

```
git add -A && git commit -m "feat(core-model): canonical domain models (Song, Album, Artist, Playlist)"
```

---

## Step 3 — Vertical slice: permission → scan → list → play one song

Build and verify between each sub-step. This slice validates that the agent mesh composes.

### Prompt 3a — Permissions
```
Use the permissions-agent and the handle-runtime-permissions skill. Implement :core:permissions:
the version-aware media permission (READ_MEDIA_AUDIO on 33+, READ_EXTERNAL_STORAGE w/ maxSdkVersion
32 below), a Compose permission gate with rationale + Settings routing + resume re-check, and the
manifest declarations (no INTERNET, no WRITE). Expose an onGranted callback. Build.
```
✅ **Checkpoint:** permission flow compiles; requests the correct permission per SDK.

### Prompt 3b — Database
```
Use the data-layer-agent and the add-room-database skill. In :core:database create SongEntity
(include media_store_id, date_modified, content_uri, artwork_uri), SongDao (observeAllSongs(): Flow,
upsertSongs, deleteByMediaStoreIds, getAllSongs), MusicDatabase v1, and the Hilt DatabaseModule.
Add a DAO instrumentation test. Build.
```
✅ **Checkpoint:** `:core:database` builds; DAO test passes.

### Prompt 3c — Scanner
```
Use the media-scanning-agent and the implement-media-scanning skill. In :core:data implement
MediaScanner (MediaStore.Audio query, IS_MUSIC filter, stable content:// URIs, TRACK-number
normalization), the ScannedSong→SongEntity mapper, and LibraryIndexer.sync() doing an incremental
diff into Room (no full wipe). Build.
```
✅ **Checkpoint:** `:core:data` builds; scanner runs on Dispatchers.IO with closed cursors.

### Prompt 3d — Repository
```
Use the data-layer-agent and the implement-repository-pattern skill. In :core:data add
SongRepository exposing observeSongs(): Flow<List<Song>> mapping entities to :core:model Song.
Wire it through Hilt. Build.
```
✅ **Checkpoint:** repository exposes domain `Song` Flow; builds.

### Prompt 3e — Playback
```
Use the audio-engineer agent and the media3-playback rules. In :core:media implement the Media3
ExoPlayer playback service with MediaSession, audio focus, and a foreground notification, plus a
PlaybackController (play(song), togglePlayPause, next, previous). Play from content:// URIs and
expose the audioSessionId for later equalizer use. Build.
```
✅ **Checkpoint:** `:core:media` builds; service registered in the manifest with `foregroundServiceType="mediaPlayback"`.

### Prompt 3f — Library feature
```
Use the ui-builder and viewmodel-architect agents with implement-state-management and
implement-image-loading-coil. In :feature:library build LibraryScreen: a LazyColumn of SongListItem
(stable keys, Coil artwork), backed by LibraryViewModel that triggers the scan on permission grant
and exposes songs: StateFlow<List<Song>>. Tapping a song calls PlaybackController.play(song). Wrap
the screen in the :core:permissions gate. Build.
```
✅ **Checkpoint:** `:feature:library` builds; screen reads from the repository Flow.

### Prompt 3g — Wire the app end-to-end
```
Use the navigation-agent. Register :feature:library's nav entry as the start destination in the
:app NavHost, inject the playback service/controller, and run the app on a device IN AIRPLANE MODE.
Verify end-to-end: grant permission → library populates from local files → tap a song → it plays
with working notification controls.
```
✅ **Checkpoint:** the full path works on-device in airplane mode. This is the real Phase 0 success.

### Prompt 3h — Review gate (run before committing)
```
Run the code-reviewer, compose-performance-auditor, and test-writer agents over the slice
(in parallel). Fix all CRITICAL/HIGH findings — especially any network usage, memory leaks,
missing list keys, or main-thread I/O. Add unit tests for LibraryViewModel and SongRepository.
Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH findings; `./gradlew check` green; ViewModel + repository tested.

```
git add -A && git commit -m "feat: vertical slice — scan, list, play one song (offline, end-to-end)"
```

---

## After Phase 0

You've proven the agent mesh on one real feature. Now go feature-by-feature, each on its own branch,
reusing the same loop (skill → owning agent → build → review gate → commit):

- `:feature:player` (full now-playing + mini-player)
- `:feature:playlists`
- `:feature:equalizer` (equalizer-agent effect in :core:media + screen)
- `:feature:tageditor`
- `:feature:search`
- `:feature:widget`
- `generate-baseline-profile` once the hot paths (startup + library scroll) are stable

Keep CLAUDE.md's "Current Status" updated as you go, and let `OWNERSHIP.md` settle any
"who edits this file" question the moment two agents reach for the same path.
```
