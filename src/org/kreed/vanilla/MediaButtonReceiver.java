package org.kreed.vanilla;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.view.KeyEvent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class MediaButtonReceiver extends BroadcastReceiver {
	private static final int MSG_LONGPRESS_TIMEOUT = 1;
	private static final int MSG_MEDIA_CLICK = 2;
	private static final int LONG_PRESS_DELAY = 1000;
	private static final int DOUBLE_CLICK_DELAY = 300;
	

	private static long mLastClickTime = 0;
	private static boolean mDown = false;
	private static boolean mCheckDoubleClick = false;
	//private static boolean mLaunched = false;
	private static Handler mHandler= new Handler(){
		@Override
		public void handleMessage(Message msg){
			Context context = (Context)msg.obj;
            Intent i = new Intent(context, PlaybackService.class);
            Log.d("MAGNUS", "msg.what "+ msg.what);
			switch (msg.what){
				case MSG_LONGPRESS_TIMEOUT:

/*					Context context = (Context)msg.obj;
		            Intent i = new Intent(context, PlaybackService.class);

					i.setAction(PlaybackService.NEXT_SONG);
					context.startService(i);
*/
					break;
				case MSG_MEDIA_CLICK:
					if (mCheckDoubleClick) {
						i.setAction(PlaybackService.NEXT_SONG);
						mCheckDoubleClick = false;
					}
					else {
						i.setAction(PlaybackService.TOGGLE_PLAYBACK);						
					}
					context.startService(i);
					break;
					default:
						Log.d("MAGNUS", "msg.what");
				}
		}
	};
	
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String intentAction = intent.getAction();
        /*if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
            Intent i = new Intent(context, MediaPlaybackService.class);
            i.setAction(MediaPlaybackService.SERVICECMD);
            i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDPAUSE);
            context.startService(i);
        } else*/
        
		Log.d("MAGNUS", "action "+intentAction);

        
        if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            KeyEvent event = (KeyEvent)
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            
            if (event == null) {
                return;
            }

            int keycode = event.getKeyCode();
            int action = event.getAction();
            long eventtime = event.getEventTime();

            // single quick press: pause/resume. 
            // double press: next track
            // long press: start auto-shuffle mode.
            
            Intent i = new Intent(context, PlaybackService.class);
            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                	command = PlaybackService.TOGGLE_PLAYBACK;
                    i.setAction(PlaybackService.TOGGLE_PLAYBACK);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                	// not sure this works
                	command = PlaybackService.NEXT_SONG;
                    i.setAction(PlaybackService.NEXT_SONG);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                	// not sure this works
                	command = PlaybackService.PREVIOUS_SONG;
                    i.setAction(PlaybackService.PREVIOUS_SONG);
                    break;
            }
            
            Log.d("MAGNUS", "MediaButtonReceiver.onReceive - keyEvent "+ keycode 
            		+" - command "+ command 
            		+" - mLastClickTime "+ mLastClickTime
            		+" - eventtime "+ eventtime
            	);

            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mDown) {
                        if (PlaybackService.TOGGLE_PLAYBACK.equals(command)
                                && mLastClickTime != 0 
                                && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
                        	mHandler.sendMessage(
                                    mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context));
                        }
                    } else {
                    	
                    	if (keycode == KeyEvent.KEYCODE_HEADSETHOOK) {
                    		// unique button ?
                    		// we need to check if it it repeated
                    		if ( eventtime - mLastClickTime < DOUBLE_CLICK_DELAY ) {
                    			// double
                    			mCheckDoubleClick = true;
                    		}
                    		else {
	                    		mHandler.sendMessageDelayed(
	                    				mHandler.obtainMessage(MSG_MEDIA_CLICK, context), DOUBLE_CLICK_DELAY);
                    		}
                    		mLastClickTime = eventtime;
                    		mDown = true;
                    	}
                    	else {
                            context.startService(i);
                    	}
                    	
                    }
                } else {
                    mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }
            }
        }
        abortBroadcast();
    }
}