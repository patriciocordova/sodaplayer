package info.sodapanda.sodaplayer;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

//TODO Play streams without audio
//TODO Set max queue length
public class FFmpegVideoView extends SurfaceView implements SurfaceHolder.Callback{
	private Activity activity;
	public static boolean is_playing = false;
	
	AudioTrack audioTrack;
	private ArrayList<String> rtmpUrlList;
	private long instance;
	
	Thread play_thread;

    private PlayCallback playCallback;
	
	int retry_time = 0;
	
	public FFmpegVideoView(Context context){
		super(context);
	}

	
	public FFmpegVideoView(Context context,PlayCallback playCallback,int width,int height) {
		super(context);
        this.playCallback = playCallback;
		instance = getPlayInstance(width,height);
		Log.i("soda", "get the event of playing video "+instance);
		
		this.activity = (Activity) context;
		initSurfaceView();
	}
	
	private void initSurfaceView(){
		getHolder().addCallback(this);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i("soda","surface created");
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
		Log.i("soda","surface changed");
		setupsurface(holder.getSurface(), width, height,instance);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i("soda","surface destroyed");
		nativedisablevidio(instance);
	}
	
	/**
	 * init AudioTrack called by native code
	 * @param sample_rate bit rate of source audio
	 * @return audio buffer byte array
	 */
	public byte[] initAdudioTrack(int sample_rate){
		Log.i("soda","java get the sample rate: "+sample_rate);
		int buffer_size=AudioTrack.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		if(buffer_size<8192){
			buffer_size = 8192;
		}
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sample_rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT, buffer_size,AudioTrack.MODE_STREAM);

		Log.e("soda", "Audio buffer_size "+buffer_size);
		byte[] bytes = new byte[buffer_size];
		return bytes;
	}
	
	/**
	 * start to play PCM audio in buffer, called by native code
	 * @param buffer PCM data to be played
	 * @param buff_size bytes of buffer
	 */
	public void playSound(byte[] buffer,int buff_size){
		if(audioTrack==null){
			Log.e("sodaplayer","audioTrack is null");
			return;
		}
		if(audioTrack.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING){
			audioTrack.play();
		}
		audioTrack.write(buffer, 0, buff_size);
	}
	
	/**
	 * start player
	 * @param filename
	 */
	private void start(final String filename) {
		//if video is playing, return
		if(play_thread!=null && play_thread.isAlive()){
			return ;
		}
        playCallback.onConnecting();
		play_thread = new Thread(new Runnable() {
			public void run() {
				is_playing = true;
				int error=openfile(filename,instance);
				is_playing = false;
				if(error !=0){//file opening error
					activity.runOnUiThread(new Runnable() {
						public void run() {
							Log.i("soda","Error ocurred when trying to open the file");
							stop();
                            videoDisConnected();
						}
					});
				}
			}
		});
		play_thread.start();
	}
	
	public void startPlayer(ArrayList<String> rtmpUrlList) {
		if(rtmpUrlList==null || rtmpUrlList.size()<=0){
			return;
		}
		this.rtmpUrlList = rtmpUrlList;
		String filename = rtmpUrlList.get(0);
        Log.i("soda","The address is: "+filename);
		start(filename);
	}

	/**
	 * stop play
	 */
	public void stop() {
		nativestop(instance);
		if (play_thread!=null) {
			try {
				play_thread.join();
				Log.e("soda", "play_thread return");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Log.i("soda","stop playing audio");
		if (audioTrack!=null) {
			audioTrack.flush();
			audioTrack.stop();
			audioTrack.release();
			audioTrack=null;
		}
        playCallback.onStop();
	}

    public void finishplay() {
        if (audioTrack != null) {
            audioTrack.flush();
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        playCallback.onStop();
    }

    public void onNativeConnected(){
		Log.i("soda","video connected successfully");
        playCallback.onConnected();
		retry_time = 0;
	}
	
	//**video reconnect */
	public void videoDisConnected() {
		if (retry_time < rtmpUrlList.size()) {
			Log.i("test","Times tried:" + retry_time + " on the address " + rtmpUrlList.get(retry_time));
			start(rtmpUrlList.get(retry_time));
		} else {
			Log.i("test", retry_time + " times. Timed out.");
			stop();
			retry_time = 0;
			Toast.makeText(activity, "Fail to connect of the video address.", Toast.LENGTH_LONG).show();
		}
		retry_time++;
	}
	
	public native int openfile(String filename,long instance);
	public native int setupsurface(Surface surface,int width,int height,long instance);
	public native int nativestop(long instance);
	public native int nativedisablevidio(long instance);
	public native long getPlayInstance(int width,int height);

}
