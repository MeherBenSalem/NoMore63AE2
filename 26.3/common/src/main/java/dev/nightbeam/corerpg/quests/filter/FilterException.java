package dev.nightbeam.corerpg.quests.filter;

/** A syntax or argument error in a filter expression, with the character position where it was found. */
public class FilterException extends RuntimeException {
    private final int position;

    public FilterException(String message, int position) {
        super(message);
        this.position = position;
    }

    public int position() {
        return position;
    }

    @Override
    public String getMessage() {
        return position >= 0 ? super.getMessage() + " (at " + (position + 1) + ")" : super.getMessage();
    }
}
