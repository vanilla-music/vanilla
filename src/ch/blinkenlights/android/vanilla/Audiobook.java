package ch.blinkenlights.android.vanilla;

/**
 * Represents an audiobook entry
 */
public class Audiobook {

    private final String path;
    private final long songID;
    private final long position;
    private Song song;
    private int timelineIndex;

    public Audiobook(String path, long songID, long position) {
        this.path = path;
        this.songID = songID;
        this.position = position;
    }

    public String getPath() {
        return path;
    }

    public long getSongID() {
        return songID;
    }

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public int getTimelineIndex() {
        return timelineIndex;
    }

    public void setTimelineIndex(int timelineIndex) {
        this.timelineIndex = timelineIndex;
    }

    public long getPosition() {
        return position;
    }
}
