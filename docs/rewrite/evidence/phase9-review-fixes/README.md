# Phase 9 review-fix evidence

Recorded on 2026-10-23. Tested product source: **`d53a589a2e36753ba2cbbe00862582e7923d858e`**. PR head `fe1d859` adds the verification document only; this evidence commit also changes documentation only. These are recorded host results, not a CI attestation. Independently rerun the commands below when reviewing.

## Open risk first

**Reviewer ID1 is only resolved for supported owner-thread shutdown. Arbitrary live Folia disable from a non-owner thread remains unresolved.** That path completes the close future without physically closing the inventory. Because disable also removes the guarding listeners, a live player may retain an unguarded menu containing real item stacks. The successful shutdown packet check does not prove that this live-disable risk is closed. A reviewer must decide whether that unsupported lifecycle is acceptable before merge; the completed AI recheck used this explicit scope limitation.

The retained test [`aDisabledNonOwnerCloseCompletesWithoutTheStoppedScheduler`](../../../../src/foliaTest/java/me/rakkoon69/rakunloots/server/Phase9FoliaMenuCloseOwnershipTest.java) deliberately asserts the physical inventory remains open. It is a limitation regression, not evidence of physical closure.

## Finding-to-proof map

| ID/status | Code/test | Before fix | After fix |
| --- | --- | --- | --- |
| 1: shutdown fixed; live non-owner gap above | [FoliaServerAdapter](../../../../src/folia/java/me/rakkoon69/rakunloots/server/FoliaServerAdapter.java), [ownership proof](../../../../src/foliaTest/java/me/rakkoon69/rakunloots/server/Phase9FoliaShutdownOwnershipProofTest.java) | At `429acbc`, one test fails physical-close assertion at line 70: [18s RED output](red-folia-ownership.txt). | [JUnit XML](junit/TEST-me.rakkoon69.rakunloots.server.Phase9FoliaShutdownOwnershipProofTest.xml), [four adjacent ownership tests](junit/TEST-me.rakkoon69.rakunloots.server.Phase9FoliaMenuCloseOwnershipTest.xml); real packet proof below. |
| 3: fixed | [MenuService](../../../../src/main/java/me/rakkoon69/rakunloots/menu/MenuService.java), [WikiMenu](../../../../src/main/java/me/rakkoon69/rakunloots/wiki/WikiMenu.java), [wiki close proof](../../../../src/test/java/me/rakkoon69/rakunloots/wiki/Phase9WikiSessionCloseProofTest.java) | At `429acbc`, one test fails retained-session assertion at line 75: [14s RED output](red-wiki-close.txt). | [Close proof XML](junit/TEST-me.rakkoon69.rakunloots.wiki.Phase9WikiSessionCloseProofTest.xml), [seven lifecycle cases](junit/TEST-me.rakkoon69.rakunloots.menu.Phase9MenuRetirementTest.xml), [three wiki lifecycle cases](junit/TEST-me.rakkoon69.rakunloots.wiki.Phase9WikiSessionRetirementTest.xml). |
| 6: fixed; found during fix review | [superseding-open proof](../../../../src/test/java/me/rakkoon69/rakunloots/menu/Phase9SupersededRetirementProofTest.java), [adjacent race tests](../../../../src/test/java/me/rakkoon69/rakunloots/menu/Phase9SupersededRetirementRaceTest.java) | At `6cc62e4`, one test fails retirement assertion at line 60: [41s RED output](red-superseded-open.txt). | [Proof XML](junit/TEST-me.rakkoon69.rakunloots.menu.Phase9SupersededRetirementProofTest.xml), [three race cases](junit/TEST-me.rakkoon69.rakunloots.menu.Phase9SupersededRetirementRaceTest.xml). Newer pending/successful replacements retain their session; terminal failures retire once. |

`429acbc` contains the first two sealed proofs and no production fix. `6cc62e4` contains the ID6 proof before its production fix. To verify proof preservation:

```bash
git diff 429acbc d53a589 -- src/foliaTest/java/me/rakkoon69/rakunloots/server/Phase9FoliaShutdownOwnershipProofTest.java src/test/java/me/rakkoon69/rakunloots/wiki/Phase9WikiSessionCloseProofTest.java
git diff 6cc62e4 d53a589 -- src/test/java/me/rakkoon69/rakunloots/menu/Phase9SupersededRetirementProofTest.java
```

Both diffs are empty. Relevant commits: `3237859` (shutdown), `31f82a3` (retirement), `d53a589` (superseding attempts). Review the actual code as well as test outputs.

## Rerun project checks

Use the repository's normal dependency/toolchain setup, including its packet-adapter prerequisites and Java 17/25 toolchains. From the PR checkout, Bash or Git Bash:

```bash
bash ./gradlew --console=plain --max-workers=1 --init-script docs/rewrite/evidence/phase9-review-fixes/folia-test-runtime.gradle check shadowJar
```

The committed [init script](folia-test-runtime.gradle) is the exact previously external script used for the accepted host gate. It selects Java 25 only for `foliaTest`; it does not change production bytecode or the explicit Java 17 loader task. On Windows, `gradlew.bat` accepts the same arguments.

Expected recorded result: [BUILD SUCCESSFUL in 8m 15s](host-check.txt). [Counts](host-counts.json): root 1495, Folia 131, Fancy 24, Java 17 loader 17, all 18 packet adapters 506; zero failures/errors. The root has **35 skips**, not 1495 executed passes: 34 real-MySQL cases without a configured server and one loader-property check exercised by `java17Test`. No claim of live MySQL coverage is made.

For a quick final-branch recheck:

```bash
bash ./gradlew --console=plain --max-workers=1 --init-script docs/rewrite/evidence/phase9-review-fixes/folia-test-runtime.gradle :test --tests 'me.rakkoon69.rakunloots.menu.Phase9*Retirement*' --tests 'me.rakkoon69.rakunloots.wiki.Phase9WikiSession*' :foliaTest
```

To reproduce RED without modifying the PR checkout, create separate detached worktrees at `429acbc` and `6cc62e4`. Use the init script from the PR checkout by absolute path. At `429acbc`, run `:foliaTest --tests '*Phase9FoliaShutdownOwnershipProofTest'` and, separately, `:test --tests '*Phase9WikiSessionCloseProofTest'`. At `6cc62e4`, run `:test --tests '*Phase9SupersededRetirementProofTest'`. Each should fail its named assertion, rather than fail setup/compilation. Reuse the repository prerequisites in those worktrees.

## Real-server evidence and reproduction

| Final jar server | Evidence | Result |
| --- | --- | --- |
| Folia 26.2-7 / `14b7fee` | [full driver output](probe-folia.txt) | 58 passed, 0 failed |
| Paper 26.2-129 / `9240f58` | [full driver output](probe-paper.txt) | 58 passed, 0 failed |
| Spigot 26.2 / `4648-Spigot-8db49a2-96ae8cd` | [full driver output](probe-spigot.txt) | 58 passed, 0 failed |

Total **174**, not the historical 351 checks on the previous jar. Each fresh-mode run includes reload and restart persistence; no separate final-jar `--after-reload` pass was run. [Audit JSON](server-audit.json) records all three footers, no FAIL/plugin WARN/ERROR lines, installed hashes, and shutdown result. External Windows OSHI ERROR traces remain visible in the logs; zero means **RakunLoots** WARN/ERROR, not zero server-wide ERROR records.

Observed tested jar SHA256:
`9032E7906D9509616D9824CF7E060483416A714B254E507A43EEFE042BB0558B`.
The binary itself is not committed/uploaded here; this hash identifies the observed artifact, not a promise that a different environment rebuilds byte-identically.

RakunProbe is a **local-only sibling fixture**, not fully contained in this PR. Its test-helper commit is `23ed8e42fab4bdf12c06efc9ca21c66d8b8d49f6`, based on `1bac2d2cd3fa0371d31cdc1f381f81d76d36d677`. The [helper patch](../../phase9-rakunprobe.patch) exports the changes, including four startup-attribution tests; obtaining the matching base, server jars, bot dependencies and local test configuration is a prerequisite. The logs are available here even without that fixture. No server credentials/configuration or world data are published.

With that fixture configured, from its root, use an absolute path for the built jar:

```bash
python tools/rltest.py folia-26.2 /absolute/path/to/RakunLoots/build/libs/RakunLoots-1.0-SNAPSHOT-all.jar 26.1
python tools/rltest.py paper-26.2 /absolute/path/to/RakunLoots/build/libs/RakunLoots-1.0-SNAPSHOT-all.jar 26.1
python tools/rltest.py spigot-26.2 /absolute/path/to/RakunLoots/build/libs/RakunLoots-1.0-SNAPSHOT-all.jar 26.1
```

Run sequentially on disposable stopped test installs: fresh mode backs up/replaces the plugin install and resets test-bot player files. It first checks shipped startup, then sets debug logging and unloaded-chunk generation for smoke scenarios. Protocol 26.1 bots use ViaBackwards on these Java 25 servers. Check the exact final footer, every FAIL line and plugin WARN/ERROR records; do not rely on driver exit status alone.

For the separate supported-shutdown proof:

1. Install the final jar on Folia 26.2-7 and start it with the test fixture. Confirm RakunLoots enabled and the installed jar hash.
2. Run [the exact client probe used](folia-shutdown-probe.cjs). Its first line points at the original machine's Mineflayer dependency; adjust **only that dependency path** for your fixture. No client menu-close action is sent.
3. Wait for its `kind:ready` record showing the wiki window, then stop the server normally within 90s.
4. Require `kind:server_close_window` for that same window ID before disconnect and `pass:true`/`closePacketBeforeDisconnect:true`.

[Final raw JSONL](shutdown-final-jar.jsonl) records window 1 close before `Server closed`/socket close. [Stop output](shutdown-final-stop.txt) records server exit. The [old-jar result](shutdown-old-jar.json) is a **consolidated observation record**, not raw client stdout; it records no close packet on the old `C513...` jar. This proves a specific supported server shutdown, not arbitrary plugin-manager live disable, other Folia versions, or every client.

## Numerical evidence already claimed by this phase

The [independent Decimal70/100 positive-transition reference](math/positive_transition_reference.py) and [recorded reference values](math/positive-transition-reference.txt) are now included so a reviewer can derive the 250-light/250-heavy, 250-draw constants used by [LootChanceLargeGroupAccuracyTest](../../../../src/test/java/me/rakkoon69/rakunloots/roll/LootChanceLargeGroupAccuracyTest.java). The reference uses remaining-count transition probabilities rather than the production quadrature. Its final output-file path is machine-specific; adjust/remove that output-file write when running elsewhere, retaining the calculation and precision checks.

The [standalone comparison source](math/ProbePositiveReference.java), [500-entry comparison result](math/positive-reference-dc6-green.txt) and [10,000-entry first-draw result](math/first-draw-dc6-green.txt) are **historical numerical runs at `dc6ec63`**, not newly executed on the final jar. Recorded max errors were 5.93e-14 and 1.74e-10 respectively. Current final-host XMLs for [large-group accuracy](junit/TEST-me.rakkoon69.rakunloots.roll.LootChanceLargeGroupAccuracyTest.xml), [raw extreme ranges](junit/TEST-me.rakkoon69.rakunloots.config.RawRollExtremeChanceTest.xml) and [the original 50 ms fixture](junit/TEST-me.rakkoon69.rakunloots.roll.LootChanceTest.xml) are also included.

The owner-approved cost correction in [ADR 0018](../../../../docs/adr/0018-own-menus-and-exact-loot-chance.md) must be reviewed with the implementation: stable-direction removal has at most `O(n² + g*n)` cost per quadrature point, with grid-dependent total work. The 50 ms assertion covers 100 distinct entries/max 50 after three warm-ups; it is not a 50 ms guarantee for all input sizes. The independent larger cases above took hundreds of milliseconds. The [verification record](../../phase9-verification.md#math-decision-and-independent-accuracy-checks-107) links the approved ruling and remaining derivation details.

## Review coverage and remaining gaps

- [Final reviewer prose](reviewer-final.txt) and [selected status fields](reviewer-status.json): same DeepSeek High session, agent_exit0, no findings, four race/proof cases and 25 adjacent cases passed in manually invoked checks. Status `test_exit:-1` means no separate harness test command ran for the reviewer; the independent full host gate is recorded separately. The last recheck covers the **ID6 fix delta**; the earlier full Phase9 review and first fix review covered the broader code. It is not a second independent full-phase approval. The reported inherited OUT-OF-SCOPE flag describes authorized cumulative files in a read-only review (`review_violation:false`); `BLOCKED:none` is a parser artifact of literal no-blocker prose. The readable final findings were recovered after a formatting retry. An AI verdict is not a substitute for source review or rerunning checks.
- Live MySQL was not tested. Human client appearance, large-list paging and sound checks remain pending; automated navigation/packet checks do not replace them.
- All 18 adapter suites passed, but final live runs cover only the three named 26.2 server builds. The older351 checks are historical, not final-jar reruns.
- Folia's GuardianBeam laser remains unavailable under the existing adapter policy; logs retain `LASER_UNAVAILABLE` as an INFO diagnostic. It is not a new fix-pass warning suppression.
- An initial host build and initial attempted Folia startup exhausted native JVM allocation. Neither was counted as an accepted run. Retrying with one build worker and sequential servers passed under unchanged server heaps. This does not establish a product memory leak.
- RakunProbe has a known **startup fail-fast gap**: after a failed start it can inspect an empty/uninitialized log and then fail on missing config. Require live readiness/plugin enable and a complete passing footer; the accepted logs show both. Its reviewed #114 classifier attributes only exact OSHI bootstrap chains and retains full traces; unknown/plugin errors must still be investigated.
- The original review's observations 2/4/5 were not implemented as bugs: 2 already meets barrier/high-warning requirements; 4's info icon is explicitly silent in #108/#109; 5 did not establish a normal-player stale-menu trigger or a contract requiring closure of an unrelated existing view. Reviewers may revisit that triage using the canonical issues.
- This evidence pack and verification record are documentation-only. No additional test execution, product change, shipped-resource change, merge or issue closure occurred while publishing it.

[manifest.json](manifest.json) hashes the copied evidence files (not this README or itself). Raw host/driver outputs, historical numerical sources/outputs and JUnit regression XMLs are copied without editing; `host-counts.json`, `server-audit.json`, `reviewer-status.json` and the old-jar consolidated record are derived summaries. Local paths, test usernames and build-host names in recorded outputs describe the test machine. The full local run archive and Boxie gap notes remain in the locations named by the [verification record](../../phase9-verification.md).
