package ch.blinkenlights.android.vanilla.lyrics;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import ch.blinkenlights.android.vanilla.R;

/**
 * Dialog to show fetched lyrics
 */
public class LyricsDialog extends DialogFragment {
    
    private static final String ARTIST = "artist_key"; 
    private static final String TITLE = "title_key"; 

    private Handler mHandler;
    private LyricsEngine mLyricsEngine = new LyricsWikiEngine();
    
    private ProgressBar mProgressBar;
    private TextView mLyricsText;
    
    public static LyricsDialog newInstance(String artist, String title) {
        Bundle args = new Bundle(2);
        args.putString(ARTIST, artist);
        args.putString(TITLE, title);
        
        LyricsDialog lrd = new LyricsDialog();
        lrd.setArguments(args);
        return lrd;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        HandlerThread thread = new HandlerThread("LyricsFetcher");
        thread.start();
        mHandler = new Handler(thread.getLooper(), new LyricsHandler());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        mHandler.getLooper().quit();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // there's no way to retrieve underlying dialog views, hence we need to use
        // our own inflated layout in order to gain complete control
        View progressDialog = View.inflate(getActivity(), R.layout.lyrics_dialog, null);
        mProgressBar = (ProgressBar) progressDialog.findViewById(android.R.id.progress);
        mLyricsText = (TextView) progressDialog.findViewById(R.id.message);

        // setup simple waiting view
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.fetching_lyrics);
        mLyricsText.setText(R.string.please_wait);
        builder.setView(progressDialog);
        
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        
        mHandler.sendEmptyMessage(0);
    }

    private class LyricsHandler implements Handler.Callback {
        
        @Override
        public boolean handleMessage(Message msg) {
            // the only message that is requested is song lyrics fetch
            Bundle args = getArguments(); // provided on creating
            String lyrics = mLyricsEngine.getLyrics(args.getString(ARTIST), args.getString(TITLE));
            getActivity().runOnUiThread(new ResponseAction(lyrics));
            
            return true;
        }
    }
    
    private class ResponseAction implements Runnable {

        private final String lyrics;
        
        public ResponseAction(String lyrics) {
            this.lyrics = lyrics;
        }

        @Override
        public void run() {
            if(TextUtils.isEmpty(lyrics)) {
                // no lyrics - show excuse
                dismiss();
                Toast.makeText(getActivity(), R.string.lyrics_not_found, Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog dialog = (AlertDialog) getDialog();
            dialog.setTitle(R.string.song_lyrics);
            mProgressBar.setVisibility(View.GONE);
            mLyricsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            mLyricsText.setMovementMethod(new ScrollingMovementMethod());
            mLyricsText.setText(lyrics);
        }
    }
}
