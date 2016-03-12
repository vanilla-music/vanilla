package ch.blinkenlights.android.vanilla.lyrics;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Implementation of lyrics engine based on LyricsWiki API.
 * This api, although hard to find, provides <code>getSong</code> API call
 * to find the corresponding song by artist and title.
 * 
 * <p/> The licensing issues don't allow it to return full song lyrics on API call.
 * Hence the retrieval is done in 2 steps - first, call to <code>getSong</code> to 
 * find the requested song and, if found, get the page containing full song lyrics
 * 
 * @author Oleg Chernovskiy
 * 
 */
public class LyricsWikiEngine implements LyricsEngine {

    private static final String TAG = LyricsWikiEngine.class.getSimpleName();
    
    @Override
    public String getLyrics(String artistName, String songTitle) {
        try {
            
            String lyricsUrl = makeApiCall(artistName, songTitle);
            if (lyricsUrl == null) { // no URL in API answer or no correct answer at all
                return null;
            }

            return parseFullLyricsPage(lyricsUrl);

        } catch (IOException e) {
            Log.w(TAG, "Couldn't connect to lyrics wiki REST endpoints", e);
            return null;
        } catch (JSONException e) {
            Log.w(TAG, "Couldn't transform API answer to JSON entity", e);
            return null;
        }
    }

    /**
     * First call
     */
    private String makeApiCall(String artistName, String songTitle) throws IOException, JSONException {
        HttpsURLConnection apiCall = null;
        try {
            // build query
            // e.g. https://lyrics.wikia.com/api.php?func=getSong&artist=The%20Beatle&song=Girl&fmt=realjson
            Uri link = new Uri.Builder()
                    .scheme("https")
                    .authority("lyrics.wikia.com")
                    .path("api.php")
                    .appendQueryParameter("func", "getSong")
                    .appendQueryParameter("fmt", "realjson")
                    .appendQueryParameter("artist", artistName)
                    .appendQueryParameter("song", songTitle)
                    .build();

            // construct an http request
            apiCall = (HttpsURLConnection) new URL(link.toString()).openConnection();
            apiCall.setReadTimeout(10_000);
            apiCall.setConnectTimeout(15_000);

            // execute
            apiCall.connect();
            int response = apiCall.getResponseCode();
            if (response != HttpsURLConnection.HTTP_OK) {
                // redirects are handled internally, this is clearly an error
                return null;
            }

            InputStream is = apiCall.getInputStream();
            String reply = readIt(is);
            JSONObject getSongAnswer = new JSONObject(reply);

            return getLyricsUrl(getSongAnswer);
        } finally {
            if(apiCall != null) {
                apiCall.disconnect();
            }
        }
    }

    /**
     * Second call
     */
    private String parseFullLyricsPage(String lyricsUrl) throws IOException {
        Document page = Jsoup.parse(new URL(lyricsUrl), 10_000);
        Element lyricsBox = page.select("div.lyricbox").first();
        if(lyricsBox == null) { // no lyrics frame on page
            return null;
        }

        // remove unneeded elements
        lyricsBox.select("div.rtMatcher").remove();
        lyricsBox.select("div.lyricsbreak").remove();
        lyricsBox.select("script").remove();
        
        StringBuilder builder = new StringBuilder();
        for(Node curr : lyricsBox.childNodes()) {
            if(curr instanceof TextNode) {
                builder.append(((TextNode) curr).text());
            } else {
                builder.append("\n");
            }
        }

        return builder.toString();
    }
    
    private String getLyricsUrl(JSONObject getSongAnswer) {
        try {
            String pageId = getSongAnswer.getString("page_id");
            if(TextUtils.isEmpty(pageId)) {
                return null; // empty page_id means page wasn't created
            }
            return getSongAnswer.getString("url");
        } catch (JSONException e) {
            Log.w(TAG, "Unknown format of getSong API call answer", e);
            return null;
        }
    }
    
    // Reads an InputStream and converts it to a String.
    public String readIt(InputStream stream) throws IOException {
        Reader reader = new InputStreamReader(stream, "UTF-8");
        StringWriter sw = new StringWriter();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            sw.write(buffer, 0, count);
        }
        
        return sw.toString();
    }
    
}
