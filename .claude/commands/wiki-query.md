---
description: Answer a question from the LLM wiki, with citations
argument-hint: <question>
---

Answer this question from the project wiki: $ARGUMENTS

1. Read `docs/wiki/index.md`, pick the relevant pages, and read them (follow [[links]] one hop when
   it helps).
2. Answer with [[slug]] citations for each claim. If the wiki is silent or thin on something,
   say so, then check the code or raw sources and label that part "(not yet in wiki)".
3. If the wiki contradicts the code, say so and flag it for /wiki-lint.
4. Ask whether to file the answer. If yes: write `docs/wiki/answers/<slug>.md` (type: answer),
   link it from the pages it draws on, add it to `docs/wiki/index.md`.
5. Append to `docs/wiki/log.md`: `## [YYYY-MM-DD] query | <short question>` plus pages read and
   whether the answer was filed.
