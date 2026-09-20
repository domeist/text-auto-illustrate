# Roadmap

Things worth doing next, roughly in order of value.

## Evaluation data

**Include the lenient judgements in the demo corpus.** The demo corpus is built
from the strict set's judgements only, so scoring the lenient set against it is
meaningless — most of what it judges relevant is absent. The lenient set names
83,332 distinct images, which fits inside a 200,000-image demo corpus
comfortably, so this is a matter of passing both sets to
`tools/build_demo_corpus.py` and republishing. Would grow the archive.

**Document how the lenient set was built.** `tools/` explains where the corpus
comes from and how the demo subset is sampled, but nothing records how the
lenient judgements were generated — they were produced by walking Wikipedia
article links and collecting the images found. Without that, the broader of the
two sets is unreproducible and hard to trust.

**Publish the evaluation sets on Hugging Face.** They are the part of this
project that exists nowhere else, and researchers look for datasets there rather
than inside GitHub repositories.

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

**Show the results.** The README reports numbers but shows no charts, though
`tools/plot_results.py` produces them. Committing a couple would make the
findings legible at a glance.

**Add a Maven wrapper.** `mvnw` would drop Maven from the prerequisites, leaving
only a JDK.

**An interface.** The project is an evaluation harness; you cannot paste a
paragraph and browse the images it suggests without reading a terminal. A small
web front end showing the actual pictures would demonstrate the idea far better
than a table of scores.
