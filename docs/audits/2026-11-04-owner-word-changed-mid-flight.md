# The owner's colour was changed mid-flight: what happened, and the plan to stop it happening again

Written by Rowan Waymark (planner), 2026-11-04. RECORD -2062 (corrected in place), -2063, -2067, -2068.

## What the owner said

With an S22 screenshot of the Layers popover in day mode, Trail selected in rust red:

1. "That highlight isn't supposed to be red. It's supposed to be a cream color like the theme suggests"
2. "Night mode gets the red."
3. "Like I said night mode gets the red"
4. After the change: "Yeah rust is only for night mode"
5. "I told you the color. Why did you change it?"

The owner's instruction was clear from item 2: cream by day and the red at night. Nothing the owner said later changed it.

## How it was changed, step by step

1. **The planner translated instead of quoting.** In dispatch 2026-11-04-05 A1 and RECORD -2062, the planner wrote "Night mode gets the red" down as "at night, it stays as it is today". That rested on an unchecked premise: that today's night chip was already the red. It wasn't. Today's night chip is dark brown #6B421A, Material's `secondaryContainer` in the dark theme (MapModeChips.kt:124, Theme.kt:76). From that moment, the dispatch carried the planner's assumption where the owner's words should have been.
2. **The coder did her job and found the gap.** Hazel's verify-first report said "night stays as today" is #6B421A, not the day's #A2612C, and asked which was meant. That was the right finding and the right question.
3. **The planner turned the gap into a new choice and steered it.** Instead of going back to the owner's words ("Night mode gets the red", meaning red), the planner asked: "At night today the chosen chip is a dark brown (#6B421A), not the day's rust red (#A2612C). Which should night mode use?" The options were "Keep dark brown (Recommended)" and "Rust red at night". The owner's earlier words weren't quoted, the option that contradicted them was marked Recommended, and the question read as if night had never been ruled.
4. **The owner picked the recommended option**, plausibly reading "keep" as "keep what I said". The planner recorded it as a ruling (RECORD -2063), and the coder built night as dark brown into PR #483.
5. **The owner restated** ("Yeah rust is only for night mode"). The planner asked a second time. Only then was the red restored (RECORD -2067), and only then did the owner have to ask why their colour had been changed.

So the owner's word was overridden twice, both times by the planner and not by the coder. First it was rewritten into an assumption. Then that assumption was offered back to the owner as the recommended answer.

## Why it happened

- **A paraphrase replaced a quote.** "Stays as it is today" is a claim about the code, not the owner's words, and it was never checked. CLAUDE.md already says a dispatch's claims about the tree are premises to check. This one was written as the ruling itself, so nobody treated it as a premise.
- **A contradiction was resolved by the planner instead of by the owner's words.** When Hazel's finding showed the paraphrase was wrong, the planner should have re-read the verbatim ruling and corrected the dispatch to match it. Instead he opened the question again.
- **"Recommended" carried the planner's preference into a ruling the owner had already made.** The memory rule "correct records in place" was also missed at first: the correction went in as a new entry (-2067) before the original (-2062) was corrected in place.
- **Speed.** The planner was running an S22 session, two merges and three coders at the same time. The colour question was answered from memory of a paraphrase, not from the owner's sentence.

## The plan

1. **The rule.** CLAUDE.md, "The owner's word": a ruling stands until the owner changes it, and nobody but the owner can.
2. **Verbatim first, in every dispatch.** Each owner ruling is quoted in quotation marks. Any reading of it follows on its own line, labelled "the planner's reading". A reading that makes a claim about the code ("as today", "unchanged") is a premise the coder checks against the quote, and reports if it disagrees.
3. **A conflict goes back through the quote.** When a finding conflicts with a ruling, the planner re-reads the owner's exact words before writing anything. If the words settle it, the dispatch is corrected to match them, and the owner is told what the planner got wrong. If they don't, the owner is asked with their own words quoted as the first option, and no departing option is marked Recommended.
4. **"Recommended" never contradicts a ruling.** When the owner has already ruled, the only option that may be marked Recommended is the ruling itself.
5. **Coders compare against the quote, not the paraphrase.** Coder dispatches say: where the planner's reading and the owner's words disagree, follow the owner's words and report the disagreement.
6. **Corrections go in place.** The original record entry is corrected where it stands, with what was wrong and who got it wrong. A later entry only adds what happened next.
7. **The planner's memory** carries this as a feedback rule, so it applies in sessions that never read this file.
