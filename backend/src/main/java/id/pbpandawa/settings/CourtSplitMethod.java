package id.pbpandawa.settings;

/** How the daily court fee is reflected in each player's personal estimate. */
public enum CourtSplitMethod {
    /** Divide the day's court fee equally among the unique players of that date. */
    EQUAL_SPLIT,
    /** Court fee is a club expense only; not included in personal estimates. */
    CLUB_ONLY,
    /** Every unique player of the date pays the full daily court price; the day's court cost = price x players. */
    PER_PLAYER
}
