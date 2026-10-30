# Phase 9 verification

Verified on 2026-10-23 (Asia/Bangkok) for #106–#114. The implementation is on `codex/phase9-menus`; issue closure follows its merge.

Current review-fix product: **`d53a589`**. The [committed reviewer evidence pack](evidence/phase9-review-fixes/README.md) contains raw outputs, JUnit regression results, reproduction steps and explicit remaining gaps. The original verification below describes the previous jar; the [review-fix follow-up](#phase-9-review-fixes-2026-10-23) records the final jar and 174 checks separately.

## Tested artifact

- Product source commit: `79c50b0ec75c5ecec007892ef668a846a205b268`.
- Jar: `build/libs/RakunLoots-1.0-SNAPSHOT-all.jar`.
- SHA-256: `c513c8595c7b28e797486e2267e0b0d5685124b63cb23a639a6737a5e21ac5fc`.
- RakunProbe helper commit: `23ed8e4`, local branch `codex/phase9-probe-tests` in the sibling RakunProbe repository.
- [Reproducible RakunProbe helper patch](phase9-rakunprobe.patch). Apply to its original base with `git apply --unidiff-zero phase9-rakunprobe.patch`; reverse application was checked against the committed helper. The patch includes its manual and four parser tests.

The original verification-only commits record this artifact and helper patch. Subsequent review fixes changed the product source as recorded in the follow-up below.

## Automated project checks

Final `check shadowJar` passed in 7m54s (135 tasks). The host selected Java 25 for the Folia test runtime through an external Gradle init script; core bytecode and the explicit Java 17 loader test retain their Java 17 floor. The same runtime arrangement passed the original baseline.

| Suite | Tests | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Root | 1,480 | 0 | 35 existing conditional skips |
| Folia | 126 | 0 | 0 |
| FancyHolograms | 24 | 0 | 0 |
| Java 17 loader | 17 | 0 | 0 |
| All 18 packet adapters | 506 | 0 | 0 |

The root skips are 34 MySQL tests without a configured MySQL server and one loader test whose property is supplied by the dedicated Java 17 task. No new test was skipped. The final unfiltered root run separately passed in 1m16s.

## Real servers: #113

All six complete smoke runs passed with the same jar. Each after-reload run verifies the installed jar hash, preserves configuration/database, starts the existing install and reloads before repeating the checks. Test bots use protocol 26.1 through ViaBackwards on the Java 25 servers.

| Server/build | Mode | Passed | Failed | RakunLoots ERROR | RakunLoots WARN |
| --- | --- | ---: | ---: | ---: | ---: |
| Paper 26.2-129, `9240f58` | Fresh | 58 | 0 | 0 | 0 |
| Paper 26.2-129, `9240f58` | After reload | 59 | 0 | 0 | 0 |
| Spigot 26.2, `4648-Spigot-8db49a2-96ae8cd` | Fresh | 58 | 0 | 0 | 0 |
| Spigot 26.2, `4648-Spigot-8db49a2-96ae8cd` | After reload | 59 | 0 | 0 | 0 |
| Folia 26.2-7, `14b7fee` | Fresh | 58 | 0 | 0 | 0 |
| Folia 26.2-7, `14b7fee` | After reload | 59 | 0 | 0 | 0 |

Total: **351 passed, zero failed**. Both shipped menu files loaded quietly on every run. All three servers were stopped afterward; their game and RCON ports had no listeners.

Each run checked the initial wiki and empty claim, player-only console replies, every shipped summon and a timer summon, actual blocks/items, drop and inventory opening, a break, hologram packets, ownership locking, the claim menu's exact two-item click and empty close, direct claim-all, shipped wiki ordering, treasure-chest info and back navigation, reload, persistence through restart and removal.

Recorded console replies on all three servers:

| Command | Reply |
| --- | --- |
| `claimsummon` | Only a player can take summon items, because they go into an inventory. |
| `claimsummon all` | Only a player can take summon items, because they go into an inventory. |
| `loots` | Only a player can open the loots wiki, because it shows the world they are in. |

The owner's client check of opening, paging, claim clicks, appearance and sounds follows. It is nonblocking under #113 and the phase specification.

## External startup diagnostics: #114

Initial Paper and Spigot runs passed 57 functional checks but failed the broad startup assertion on Windows OSHI diagnostics emitted before RakunLoots enabled. OSHI could not read Perflib 009 and Minecraft's crash-report preload then reported three null-process statistics. A standalone OSHI-only probe reproduced the missing process map. No RakunLoots frame was involved.

The helper now prints those exact records explicitly as external diagnostics and retains their full traces. It recognizes the Paper/Folia preload thread and Spigot's `ServerMain` format, requires the OSHI/SystemReport/preload call chain, and rejects plugin frames, plugin ERRORs, unknown nested exceptions and missing-preload records. No Windows registry repair, server-jar change or JVM workaround was used in the accepted runs. Each final report still contains the external registry ERROR; the zero counts above refer to RakunLoots as required by #113.

Four host tests and the completed same-session DeepSeek High recheck passed. The reviewer independently reconstructed the files and classified the actual Spigot excerpt as zero unexpected/four external records. The final matcher was also replayed against both archived Paper runs with the same result and no plugin WARN/ERROR. An earlier review timed out despite a recovered empty findings array; it was not counted as completed approval. The successful final recheck exited zero.

## Math decision and independent accuracy checks: #107

The owner approved correcting the original smaller-side cost claim. ADR 0018, the phase specification and the canonical issue bodies now state stable-direction removal, at most `O(n² + g*n)` per quadrature point, with grid-dependent total work. The accuracy and 50 ms requirements stayed intact.

| Check | Result |
| --- | --- |
| Raw `Integer.MAX_VALUE` count | Terminates with the capped tail folded in O(n); long interval cardinality |
| Raw distinct weights, max above n, always/hidden | Brute-force draw-order oracle passes |
| 99 entries of weight 100, one of weight 1, k=99 | Analytic inclusion `0.050379102187102065`; retained regression passes |
| 250 weight-1 and 250 weight-2 entries, k=250 | Independent positive-transition Decimal70/100 reference; every marginal within `5.93e-14` |
| 9,900 weight-1 and 100 weight-2 entries, k=1 | Every marginal within `1.74e-10` of the exact weight share |
| 9,999 weight-1 and one weight-2 entry, k=2 | All 10,000 marginal checks pass the exact two-draw inclusion formula |
| 100 entries, 100 distinct weights, max=50 | Original three warm-ups and strict 50 ms assertion pass |

The final review corrected a misleading lower-tail comment. The calculation adds the unconditional self-ring marginal for k≥2, which already includes exactly-one-other mass. Its overcount is bounded by the event of at least two other rings. Independent Decimal90 calculation and Kimi's derivation showed `2.39e-12` actual overcount below the `9.58e-12` bound in the disputed example. The same DeepSeek reviewer withdrew its numerical objection and accepted the corrected derivation and additional regression. No normalization, accuracy relaxation or timing-threshold change was introduced.

## Review and handoff records

Implementers and reviewers ran sequentially. Two consecutive Gemini failures triggered the approved DeepSeek Max fallback. DeepSeek High reviewed the implementations after the requested Opus endpoint proved unavailable. Every original implementation and subsequent fix diff was read before acceptance; interrupted or unreadable reviews were not accepted.

- math/formatting handoff (old issue 107)
- menu-file handoff (old issue 108)
- menu-engine handoff (old issue 109)
- claim-menu handoff (old issue 110)
- wiki-list handoff (old issue 111)
- loot-page handoff (old issue 112)

Full per-run logs, XML counts, independent numerical probes and model statistics are retained under `C:/Workspace/Tools/Boxie/runs/rakunloots/`. Boxie bugs and recovery gaps are recorded under `C:/Workspace/Tools/Boxie/docs/`, particularly `rakunloots-phase9-2026-10-21.md`.

## Phase 9 review fixes: 2026-10-23

The follow-up fixes use product commit `d53a589a2e36753ba2cbbe00862582e7923d858e`. They were implemented by the same DeepSeek v4.1 Flash Max worker and checked by the same DeepSeek v4.1 Flash High reviewer, sequentially. The completed final recheck exited zero with no findings; the earlier recheck's ID6 was reproduced and fixed before acceptance.

| Reviewer ID | Fix | Retained regression and output |
| --- | --- | --- |
| 1 (#109) | When Folia has disabled the plugin and the current thread owns the player, close the physical inventory directly. Enabled calls retain entity scheduling. | `Phase9FoliaShutdownOwnershipProofTest` failed its physical-close assertion before the fix; `Phase9FoliaMenuCloseOwnershipTest` also covers owner/non-owner, scheduled and retired-player paths. Final Folia suite: 131 tests, zero failures/errors/skips. |
| 3 (#112) | Retire Wiki sessions through exact menu epochs on ordinary/explicit close, reload, failed replacement and fresh-open supersession. Navigation transfers the same logical session; identity-checked removal and immediate late-registration disposal protect newer sessions. Callbacks run outside the menu-service lock. | `Phase9WikiSessionCloseProofTest` failed its retained-session assertion before the fix. `Phase9MenuRetirementTest` and `Phase9WikiSessionRetirementTest` cover retirement, navigation, supersession and late registration. Final root suite passes. |
| 6 (#112, found during fix review) | Carry the old menu's physical-close state across superseding attempts. Retire only after the newest pending attempt resolves; a stale open/failure cannot remove a newer pending or successful session. | Root retained `Phase9SupersededRetirementProofTest`: one test/one failure at line 60 on the broken candidate (`BUILD FAILED in 41s`). It and the three `Phase9SupersededRetirementRaceTest` cases pass after the fix. Reviewer independently ran all four plus 25 adjacent tests: zero failures/errors. |

The Folia shutdown behavior follows its public entity-ownership API. Folia stops region ticking before disabling plugins, and its shutdown thread owns the player for this check. The final jar was verified on Folia 26.2 build 7 with a real client: `/loots` opened window 1, the server stopped, and the client received `close_window {windowId:1}` before `Server closed` and socket disconnect. The same helper on the old jar received no close packet and failed. The bot did not send a client-close command.

**Known limitation:** disabling the plugin live from a thread which does not own the player still completes without physically closing that player's inventory. This fix guarantees the supported owner-thread shutdown path; it does not establish safe arbitrary cross-region hot-disable. No unsafe cross-region inventory access, plugin-identity shim, kick or blocking barrier was introduced. See the [Folia shutdown process](https://docs.papermc.io/folia/reference/overview/#server-shutdown-process) and [Folia 26.2 ownership implementation](https://github.com/PaperMC/Folia/blob/ver/26.2.x/folia-server/paper-patches/features/0001-Region-Threading-Base.patch).

Final jar: `build/libs/RakunLoots-1.0-SNAPSHOT-all.jar`.
SHA-256: `9032E7906D9509616D9824CF7E060483416A714B254E507A43EEFE042BB0558B`.

Host command: `gradlew.bat --console=plain --max-workers=1 --init-script C:/Workspace/Tools/Boxie/tasks-rakunloots-folia-test-runtime.gradle check shadowJar`.
Output: **BUILD SUCCESSFUL in 8m 15s; 135 actionable tasks: 47 executed, 88 up-to-date.** The external init script selects Java 25 only for Folia tests; production bytecode and the Java 17 loader check remain unchanged.

| Suite | Tests | Failures/errors | Skipped |
| --- | ---: | ---: | ---: |
| Root | 1495 | 0 | 35 existing conditional skips |
| Folia | 131 | 0 | 0 |
| FancyHolograms | 24 | 0 | 0 |
| Java 17 loader | 17 | 0 | 0 |
| All 18 packet adapters | 506 | 0 | 0 |

Extra changes: lifecycle/race regression tests and faithful Folia ownership fake coverage; this verification record. Shipped-resource changes: **none**. No ADR, menu, language, loot or configuration defaults changed during this fix pass. The original 351 checks above remain historical evidence for the previous jar; final-jar follow-up runs are recorded separately below.

The first host build and first attempted live Folia run hit native JVM allocation failures under host memory pressure. They were not accepted as test failures or passing smoke evidence. The host gate then passed with one build worker. Live servers run sequentially with their normal heap settings; only this task's idle worker container/build daemon and disposable VM filesystem cache were released. Full logs remain in `C:/Workspace/Tools/Boxie/runs/rakunloots/`; the orchestration/reporting gaps are recorded in `C:/Workspace/Tools/Boxie/docs/rakunloots-phase9-2026-10-21.md`.

Final-jar RakunProbe follow-up (fresh mode, including reload and restart-persistence checks within each run):

| Server/build | Passed | Failed | RakunLoots WARN/ERROR |
| --- | ---: | ---: | ---: |
| Folia 26.2-7, `14b7fee` | 58 | 0 | 0 |
| Paper 26.2-129, `9240f58` | 58 | 0 | 0 |
| Spigot 26.2, `4648-Spigot-8db49a2-96ae8cd` | 58 | 0 | 0 |

Total: **174 passed, zero failed**, plus the separate Folia shutdown packet check. The final audit checked every footer, rejected any FAIL or plugin WARN/ERROR line, and SHA-256-checked each installed jar against the final hash above. External OSHI bootstrap traces remain visible and attributed under #114. All three servers stopped; no listeners remained on game/RCON ports 25565/25575, 25566/25576 or 25572/25582. No RakunLoots boxie worker remains active. The local-only RakunProbe source, the user's existing TESTLOG edits and bot helpers were preserved.

Evidence: `p9-review-fixes-final-host-check.log`, `p9-review-fixes-final-host-counts.json`, `p9-review-fixes-probe-{folia,paper,spigot}-final.log`, `p9-folia-shutdown-green-result.jsonl` and `p9-review-fixes-final-server-audit.json` under the run-log directory above. Exact model statistics and harness gaps are in the Boxie docs directory. The PR remains open for review; issues close only on merge.
