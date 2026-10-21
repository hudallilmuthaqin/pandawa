package id.pbpandawa.match;

public enum MatchStatus {
    /** In the queue, not at the front. */
    WAITING,
    /** Front of the queue: next to be played. */
    READY,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isQueued() {
        return this == WAITING || this == READY;
    }
}
