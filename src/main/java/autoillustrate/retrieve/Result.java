package autoillustrate.retrieve;

/**
 * A single retrieved image, with the caption that caused it to match.
 *
 * @param id       corpus identifier, matching the ids used in the evaluation sets
 * @param imageUrl direct link to the image file
 * @param pageUrl  the Wikipedia page the image was taken from
 * @param caption  the caption text that was indexed and searched
 * @param score    BM25 relevance score; comparable only within one result list
 */
public record Result(String id, String imageUrl, String pageUrl, String caption, double score) {
}
