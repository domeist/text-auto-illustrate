package autoillustrate.retrieve;

import java.util.List;

/**
 * Finds images that suit a passage of text.
 *
 * <p>This interface is the seam between "what we want" and "how we get it". The
 * shipped implementation matches the text against image captions with BM25, but
 * an implementation backed by image embeddings would satisfy the same contract
 * and could be swapped in without touching evaluation or the CLI.
 */
public interface Retriever extends AutoCloseable {

    /**
     * Returns up to {@code limit} images suiting the given text, best first.
     *
     * @param text  a passage of prose; may be any length
     * @param limit maximum number of results to return
     */
    List<Result> retrieve(String text, int limit) throws Exception;

    @Override
    void close() throws Exception;
}
