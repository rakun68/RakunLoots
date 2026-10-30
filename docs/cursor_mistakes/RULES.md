- A line that conflicts with another line stops the issue. Comment on the issue and wait.
- Read the ADR, including the rejected options, before starting. A rejected option in the code stops the phase.
Evidence: A01, A05, A07, A10, A12, A15, A21, A22.
## Tests
- Write the test from the acceptance line. Run it against the broken code, or a deliberately wrong version, and confirm it fails. Then make it pass.
- One test per feature loads the real input (the shipped resource, the config file) and checks the real output.
- A fake rejects what the real dependency rejects. A lenient fake is allowed only in a test that says why.
- The bound comes from the expected value. Widen it only by writing down the derivation.
Evidence: A04, A09, A11, A12, A17, A18, A25, A26, A27.
## Product data
- A shipped resource is the product a new server runs. Migrate it, or stop and report why it stays.
- A change to a shipped file is a row in the handoff: path, what changed, why.
- Follow a changed value to every file and every nested group that uses it.
Evidence: A02, A06, A16, A28, A32.
## Fixes
- Trace the call that reaches the bug in real use. A fix that only the test calls is not done.
- A smell is fixed when the design changes. A shorter signature that still unpacks the same unused values is the same smell.
- A field read from the async scheduler, the main thread, or a Folia region is one snapshot for the whole operation. Read ADR 0010 before adding that field.
Evidence: A13, A25, A30, A31.
## Report
- Use the reviewer's ids.
- For each id: the fix, the test name, and the command output.
- List every change that is not in the review list.
- "Done" means each row has that evidence. A green suite is one input to the report, not the report.
Evidence: A02, A03, A08.
## Repo
- Match the commit shape already in the log: one issue per commit, then a tidy commit. The message names the issue.
- Match the parser, the return type, and the adapter-test pattern already next to the code.
- Close issues when the branch merges, when the task allows it.


Evidence: A08, A14, A17, A23, A24.