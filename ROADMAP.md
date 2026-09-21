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

**Let the interface search the full corpus comfortably.** `serve` opens the
index once and shares it, which is fine for the demo corpus. Against the full
5.4-million-image index the first query pays a warm-up cost; pre-warming on
startup would hide it.

**Deploy it somewhere.** The interface only runs locally. A hosted instance —
even over the demo corpus — would let someone try the project without cloning it.
