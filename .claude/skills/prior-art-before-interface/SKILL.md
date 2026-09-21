---
name: prior-art-before-interface
description: How to check what established applications already do before designing any control, layout, or interaction a human will operate, so the design starts from a convention people already know rather than from first principles. Use this whenever you are placing a control, naming a setting, choosing what a gesture does, deciding what happens on rotation or resize, or pricing a UX tradeoff. Use it especially when you can construct a coherent argument for an unconventional choice, because a coherent argument is exactly what makes a convention violation feel like a considered decision.
---

# Prior art before interface

Software that people operate carries conventions built from enormous accumulated use. Where the shutter sits. What a long press does. Which way a toggle reads. These are not arbitrary and they are not matters of taste — they are the accumulated result of millions of people succeeding or failing at a task, and a user arrives already knowing them.

An interface designed from first principles will be internally coherent and will still feel wrong, because coherence is not the property users are judging. Familiarity is.

## Look before you place

Before designing any control a person operates, find two or three established applications that do the same thing and see how they do it. Not to copy the pixels — to learn what the convention is, and to find out whether one exists at all.

The check is fast. It is usually one search and a look at a screenshot, and it is always cheaper than discovering the convention through a rejected design.

What to establish:

- **Where does this control live, and why there?** Often the answer is reachability: a thumb, a mouse rest position, a scanning order.
- **What does the convention protect?** A shutter in a fixed place protects the user who is looking at the subject, not the screen. A destructive action behind a confirm protects the user who is moving fast.
- **Is there more than one convention?** Sometimes two camps exist, which tells you the choice is genuinely open and what each camp optimises for.
- **What do they do in the awkward case?** Rotation, resize, small screens, one hand, gloves, sunlight. The awkward case is where conventions were actually forged.

## The failure this prevents

A design can be defensible at every step and still violate something universal. The way it happens: a constraint is discovered, the tradeoffs are priced honestly, one cost is accepted as the lesser evil, and the accepted cost turns out to be a thing no shipping application does — because the convention exists precisely to prevent it.

The signal is pricing a cost as acceptable when you have not checked whether anyone else accepts it. If no established application makes that trade, that is data, and the correct next question is what they do instead.

Asking the user to choose between two costs is also where this bites. If one of the two is a convention violation, it should not have been on the list.

## When the convention does not fit

Sometimes it genuinely does not. Different input device, different posture, different user, different constraints entirely. That is fine, and it is a decision worth recording with the reason — including what convention it departs from, so a later reader knows it was a choice rather than an oversight.

What is not fine is departing without knowing you departed.

## This applies beyond layout

The same check is worth running on settings and their wording, on defaults, on what a gesture does, on error and empty states, on what happens when a permission is denied, and on anything with an established name. A setting whose name means something different elsewhere will be misread no matter how carefully its description is written.

## Prior art in the implementation, too

The same discipline pays off below the surface. Open-source applications in the same domain have already met the platform quirks, vendor differences and edge cases you are about to meet, and reading how they handled them is faster than rediscovering why. Their bug trackers are often the best available record of which devices misbehave and how.

Take the mechanisms; do not take the architecture. Their constraints are not yours.
