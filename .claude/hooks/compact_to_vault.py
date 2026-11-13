"""Compaction → knowledge vault hooks for RollaMusicPlayer.

PostCompact:          `python .claude/hooks/compact_to_vault.py save`
    Saves the compaction summary verbatim into docs/08 Project/History/sessions/ as a note marked `status: to-distill`.
SessionStart/compact: `python .claude/hooks/compact_to_vault.py remind`
    Injects context telling Claude to distil every `to-distill` session note into the vault's entity notes before
    continuing (vault rule docs/07 Development/Rules/Save compaction summaries to the vault.md).

Never fails the session: any error is reported on stderr and the script exits 0.
"""
import datetime
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SESSIONS = os.path.join(ROOT, "docs", "08 Project", "History", "sessions")
SUMMARY_KEYS = ("summary", "compact_summary", "compactSummary", "compaction_summary")


def read_payload():
    # Read bytes and decode as UTF-8: on Windows sys.stdin uses the ANSI code page (cp1252), which turned every
    # non-ASCII character of the summary into mojibake ("â€¦" for "…").
    raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
    try:
        return json.loads(raw) if raw.strip() else {}
    except ValueError:
        return {"raw": raw}


def frontmatter(lines):
    """The lines between a leading `---` and the next `---`; empty when the note has no frontmatter."""
    if not lines or lines[0] != "---":
        return []
    for i, line in enumerate(lines[1:], start=1):
        if line == "---":
            return lines[1:i]
    return []


def pending_notes():
    if not os.path.isdir(SESSIONS):
        return []
    out = []
    for name in sorted(os.listdir(SESSIONS)):
        if not name.endswith(".md"):
            continue
        with open(os.path.join(SESSIONS, name), encoding="utf-8") as f:
            lines = f.read(600).splitlines()
        # Only a frontmatter line counts: Sessions.md mentions the status in its prose.
        if "status: to-distill" in frontmatter(lines):
            out.append("docs/08 Project/History/sessions/" + name)
    return out


def save(payload):
    summary = next((payload[k] for k in SUMMARY_KEYS if isinstance(payload.get(k), str) and payload[k].strip()), None)
    body = summary if summary is not None else (
        "The hook payload carried no summary field; its full content follows.\n\n```json\n"
        + json.dumps(payload, indent=2, ensure_ascii=False) + "\n```"
    )
    now = datetime.datetime.now()
    os.makedirs(SESSIONS, exist_ok=True)
    stem = now.strftime("%Y-%m-%d %H%M%S") + " compaction"
    path, n = os.path.join(SESSIONS, stem + ".md"), 2
    while os.path.exists(path):  # never overwrite an earlier summary
        path, n = os.path.join(SESSIONS, f"{stem} {n}.md"), n + 1
    trigger = payload.get("trigger", "unknown")
    note = (
        "---\n"
        "type: session\n"
        "status: to-distill\n"
        f"date: {now:%Y-%m-%d}\n"
        f'source: "PostCompact hook ({trigger}), session {payload.get("session_id", "?")}"\n'
        "tags: [session, compaction]\n"
        'related: ["[[Sessions]]", "[[Save compaction summaries to the vault]]"]\n'
        "---\n\n"
        f"# {now:%Y-%m-%d %H:%M} compaction\n\n"
        "Verbatim compaction summary, saved by the PostCompact hook. Claude distils it into the vault's entity notes "
        "and then sets `status: distilled` ([[Save compaction summaries to the vault]]).\n\n"
        "## Summary\n\n" + body.strip() + "\n"
    )
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(note)
    print(json.dumps({"systemMessage": "Compaction summary saved to " + os.path.relpath(path, ROOT)}))


def remind():
    notes = pending_notes()
    listing = "\n".join("- " + n for n in notes) if notes else "- (none listed yet)"
    # SessionStart can fire before PostCompact has written the note, so the listing may miss the newest one.
    listing += (
        "\n(This reminder can run before the save hook finishes: list docs/08 Project/History/sessions/ and take every note whose"
        " frontmatter says `status: to-distill`. If none exists, the save hook failed; write the summary's notes by hand.)"
    )
    context = (
        "COMPACTION HAPPENED. Before continuing the user's task, save the important notes from the compaction summary "
        "into the knowledge vault (docs/), per docs/07 Development/Rules/Save compaction summaries to the vault.md:\n"
        "1. Read each session note marked `status: to-distill`:\n" + listing + "\n"
        "2. Distil every durable item into its entity note in the same turn: user preferences/instructions -> docs/07 Development/Rules/; "
        "decisions -> docs/02 Architecture/Decisions/; lessons -> docs/06 Operations/Knowledge/; feature/module/agent state -> docs/03 Modules/ (Features/, Agents/);"
        "open or closed work and pending tasks -> docs/08 Project/Backlog.md; phase progress -> docs/08 Project/History/Timeline.md. "
        "Update existing notes rather than duplicating; add new notes to their folder index.\n"
        "3. Set the session note's `status: distilled` and list the notes you updated under a '## Distilled into' heading.\n"
        "4. Then resume the task the summary describes."
    )
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": context}}))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else ""
    payload = read_payload()
    if mode == "save":
        save(payload)
    elif mode == "remind":
        remind()
    else:
        print("usage: compact_to_vault.py save|remind", file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # never break the session
        print(f"compact_to_vault.py: {exc}", file=sys.stderr)
    sys.exit(0)
