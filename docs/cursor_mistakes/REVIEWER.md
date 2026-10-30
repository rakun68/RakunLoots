# Reviewer mistakes

These three are from the phase 6 audit. The implementer did not make them. The audit states what happened. It does not contain the reviewer's private reason, and this file does not invent one.

## A false finding

The first handoff listed L4, "`enclosed` never uses chunk edge columns", as a gap. Issue #38 requires that behaviour: "only columns 1 to 14 of the chunk qualify". The finding was withdrawn in the second review.

Lesson: quote the spec line before flagging a behaviour as missing.

## An imprecise finding

A review sub-agent reported that `RingMath.facesMedian` had no callers. It had one caller, in a test. The first handoff repeated that, then corrected it to "test-only code in main".

Lesson: confirm the caller search includes tests before reporting a method as unused.

## A bug found late

The false `UNKNOWN_NAME` warning in A09 existed from round 1. The first review used the same lenient name catalog as the implementer and missed it. It was found in round 3, when the shipped files were loaded with a catalog that rejects unknown names and `@` / `#` entries.

Lesson: review the shipped resources with a strict fake. A fake that accepts every string hides the warning.
