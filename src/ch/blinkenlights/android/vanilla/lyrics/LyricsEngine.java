package ch.blinkenlights.android.vanilla.lyrics;

import android.content.Loader;
import android.os.HandlerThread;

/**
 * Interface for various engines for lyrics extraction
 * 
 * @author Oleg Chernovskiy
 */
public interface LyricsEngine {

    /**
     * Synchronous call to engine to retrieve lyrics. Most likely to be used in {@link HandlerThread}
     * or {@link Loader}
     * @param artistName band or artist name to search for
     * @param songTitle full song title to search for
     * @return string containing song lyrics if available, null if nothing found
     */
    String getLyrics(String artistName, String songTitle);
    
}
