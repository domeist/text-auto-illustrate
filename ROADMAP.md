# Roadmap

Things worth doing next, roughly in order of value.

## Evaluation data

**Reduce how much the demo corpus flatters the lenient set.** The demo now
covers both sets, but 29% of its images are judged relevant to some passage, so
lenient scores against it run far above the full corpus (Precision@5 0.704
versus 0.432). More distractors would narrow the gap at the cost of a larger
archive.

## Retrieval

**Reconsider the strict set's completeness.** 16 of 25 passages score zero at
rank 5, yet inspection shows the results are frequently appropriate and simply
unjudged. Extending the judgements — or pooling results across configurations
and judging those — would make the numbers mean more than they currently do.

**Try dense retrieval.** Everything here matches text against text. A CLIP-style
retriever comparing the passage against the images themselves would be a genuine
alternative, and `Retriever` is an interface so it can be measured against the
same evaluation sets. The comparison is the interesting part.

## Presentation

**An interface.** The project is an evaluation harness; you cannot paste a
paragraph and browse the images it suggests without reading a terminal. A small
web front end showing the actual pictures would demonstrate the idea far better
than a table of scores.
