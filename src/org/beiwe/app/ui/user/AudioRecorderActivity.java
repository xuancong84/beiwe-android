package org.beiwe.app.ui.user;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;

import org.beiwe.app.R;
import org.beiwe.app.Timer;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.utils.AppNotifications;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
	
/**Audio Recorder
 * 
 * Provides a GUI to record audio clips and save them as files.
 * Heavily based on code from:
 * http://developer.android.com/guide/topics/media/audio-capture.html
 * 
 * @author Josh Zagorsky, Eli Jones */
public class AudioRecorderActivity extends SessionActivity {
    private static final String LOG_TAG = "AudioRecorderActivity";
    private static boolean displayPlaybackButton = false;

    public static final String unencryptedTempAudioFileName = "unencryptedTempAudioFile.mp4";
    private String unencryptedTempAudioFilePath;
    private Boolean finishedEncrypting = true; // Effectively a lock on deleting the temp file
    
    private MediaRecorder mRecorder = null;
    private MediaPlayer mediaPlayer = null;
    
    private boolean currentlyRecording = false;
    private boolean currentlyPlaying = false;
    
    private Button playButton;
    private Button recordingButton;
    
    private final Handler recordingTimeoutHandler = new Handler();
    
    /*///////////////////////////////////////////////////
    /////////////////Overrides go here/////////////////// 
    ///////////////////////////////////////////////////*/
    
    /**On create, the activity presents the message to the user, and only a record button.
     * After recording, the app will present the user with the play button. */
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_audio_recorder);

        String fileDirectory = getApplicationContext().getFilesDir().getAbsolutePath() + "/";
        unencryptedTempAudioFilePath = fileDirectory + unencryptedTempAudioFileName;
        
    	playButton = (Button) findViewById(R.id.play_button);
    	recordingButton = (Button) findViewById(R.id.recording_button);
        
    	// Each time the screen is flipped, the app checks if it's time to show the play button
    	setPlayButtonVisibility();
    	
    	/* Improvement idea: make the Audio Recording prompt a string that can
    	 * be edited on the server, and then propagates via automatic downloads
    	 * to all phones running the app, just like the survey questions. */
    	//TextView surveyMessage = (TextView) findViewById(R.id.record_activity_textview);
    	//surveyMessage.setText("Please record a statement about how you are feeling today.");
    }


    @Override
	public void onDestroy() {
		super.onDestroy();

		if (isFinishing()) {
			// If the activity is being finished()
	        if (mRecorder != null) { stopRecording(); }
	        if (mediaPlayer != null) { stopPlaying(); }
	        displayPlaybackButton = false;

	        /* Delete the temporary, unencrypted audio file so that nobody can play it back after
	         * the user leaves this screen */
	        if (finishedEncrypting) { TextFileManager.delete(unencryptedTempAudioFileName); }
		}
		else {
			// The activity is probably just getting restarted because the screen rotated
		}
	}

    /** While encrypting the audio file we block out user interaction.*/
    private class EncryptAudioFileTask extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected void onPreExecute() { recordingButton.setClickable(false); }
		@Override
		protected Void doInBackground(Void... params) {
			encryptAudioFile();
			return null;
		}
		@Override
		protected void onPostExecute(Void arg) {
			finishedEncrypting = true;  // Can now delete audio file
			// If isFinishing(), the other call to delete the temp file won't get triggered, so do it here
			if (isFinishing()) { TextFileManager.delete(unencryptedTempAudioFileName); }

			recordingButton.setClickable(true);
		}
    }
    

    /*/////////////////////////////////////////////////
    ///////////////Button functionalities////////////// 
    /////////////////////////////////////////////////*/
	
    /** Checks if mFileName is null. If it is, then the play button will be invisible. Otherwise,
     * the button will be visible. 
     *@param fullFileName */
    private void setPlayButtonVisibility() {
    	if (!displayPlaybackButton) { playButton.setVisibility(Button.INVISIBLE); }
    	else { playButton.setVisibility(Button.VISIBLE) ; }
	}
    
    
    /** When the user presses the "record" button toggle (start/stop) recording. */
    public void buttonRecordPressed(View view) {
    	if (!currentlyRecording) { startRecording(); }
    	else { stopRecording(); }
    }
    
    /** When the user presses the "play" button, toggle (start/stop) playback. */
    public void buttonPlayPressed(View view) {
    	if (!currentlyPlaying) { startPlaying(); }
    	else { stopPlaying(); }    	
    }
    
    /*/////////////////////////////////////////////////
    ///////Recording and playing functionalities/////// 
    /////////////////////////////////////////////////*/

    /** Starts playing back the recording */
    private void startPlaying() {
    	currentlyPlaying = true;
    	
    	// Toggles button
    	playButton.setText(getApplicationContext().getString(R.string.play_button_stop_text));
    	playButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.stop_button, 0, 0);
    	
    	// Recording sequence
    	mediaPlayer = new MediaPlayer();
    	try {
    		// Play the temporary unencrypted file, because you can't read the encrypted file
            mediaPlayer.setDataSource(unencryptedTempAudioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mediaPlayer) { stopPlaying(); }
			} );
        }
        catch (IOException e) { Log.e(LOG_TAG, "prepare() failed"); }
    }
    
    /** Stops playing back the recording, and reset the button to "play" */
    private void stopPlaying() {
    	currentlyPlaying = false;
    	
    	// Toggles button
    	playButton.setText(getApplicationContext().getString(R.string.play_button_text));
    	playButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.play_button, 0, 0);

    	mediaPlayer.stop();
    	mediaPlayer.reset();
    	mediaPlayer.release();
        mediaPlayer = null;
    }    
    
    /** Start recording from the device's microphone */
    private void startRecording() {
    	currentlyRecording = true;
    	finishedEncrypting = false;

    	// Toggles button
    	recordingButton.setText( getApplicationContext().getString(R.string.record_button_stop_text) );
    	recordingButton.setCompoundDrawablesWithIntrinsicBounds( 0, R.drawable.stop_recording_button, 0, 0 );
    	
        mRecorder = new MediaRecorder();
        mRecorder.reset();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setOutputFile( unencryptedTempAudioFilePath );
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioChannels(1);
        mRecorder.setAudioSamplingRate(44100);
        mRecorder.setAudioEncodingBitRate(64000);
        
        try { mRecorder.prepare(); }
        catch (IOException e) { Log.e(LOG_TAG, "prepare() failed"); }

        startRecordingTimeout();
        mRecorder.start();
    }
    
    /** Stop recording, and reset the button to "record" */
    private void stopRecording() {
    	displayPlaybackButton = true;
    	setPlayButtonVisibility();
    	currentlyRecording = false;
    	recordingButton.setText(getApplicationContext().getString(R.string.record_button_text));
    	recordingButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.record_button, 0, 0);

    	cancelRecordingTimeout();
    	
        mRecorder.stop();
        mRecorder.reset();
        mRecorder.release();
        mRecorder = null;

        // Encrypt the audio file as soon as recording is finished
        new EncryptAudioFileTask().execute();
    }
    
    
    /*///////////////////////////////////////////////////
    ////////// Recording timeout functionality ////////// 
    ///////////////////////////////////////////////////*/
    
    /** Automatically stop recording if the recording runs longer than n seconds. */
    private void startRecordingTimeout() {
    	recordingTimeoutHandler.postDelayed(new Runnable() {    		
			@Override
			public void run() {
				showTimeoutToast();
				stopRecording();
			}
		}, Timer.VOICE_RECORDING_MAX_TIME_LENGTH);
    }
    
    
    /** Show a Toast with message "the recording timed out after n minutes" */
    private void showTimeoutToast() {
    	Resources resources = getApplicationContext().getResources();
    	String msg = (String) resources.getText(R.string.timeout_msg_1st_half);
    	msg += ((float) Timer.VOICE_RECORDING_MAX_TIME_LENGTH / 60 / 1000);
    	msg += resources.getText(R.string.timeout_msg_2nd_half);
    	Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }
    
    
    /**Cancel the stop-recording timer (this should be called when 
     * stopRecording() has already been called somewhere else, so that we don't
     * call stopRecording twice. */
    private void cancelRecordingTimeout() { recordingTimeoutHandler.removeCallbacksAndMessages(null); }
    
    /** When the user presses "Done", just kill this activity and take them
     * back to the last one; the audio file should already be saved, so we
     * don't need to do anything other than kill the activity.  */
    public void buttonDonePressed(View v) {
    	PersistentData.setCorrectAudioNotificationState(false);
		AppNotifications.dismissNotification( getApplicationContext(), AppNotifications.recordingCode );
    	startActivity(new Intent(getApplicationContext(), MainMenuActivity.class));
    	finish();
    }
    
    /* #############################################################
     * ######################### File io ###########################
     * ###########################################################*/
    
    /**Generates new file name variables. The name consists of the time the recording takes place. */
    private String generateNewEncryptedAudioFileName() {
		String timecode = ((Long)(System.currentTimeMillis() / 1000L)).toString();
		return PersistentData.getPatientID() + "_voiceRecording" + "_" + timecode + ".mp4";
    }
    
    /** Reads in the existing temporary audio file and encrypts it. Generates AES keys as needed.
     * Behavior is to spend as little time writing the file as possible, at the expense of memory.*/
	private void encryptAudioFile() {
		if (unencryptedTempAudioFilePath != null) {
			// If the audio file has been written to, encrypt the audio file
			String fileName = generateNewEncryptedAudioFileName();
			byte[] aesKey = EncryptionEngine.newAESKey();
			String encryptedRSA = null;
			String encryptedAudio = null;
			try{
				encryptedRSA = EncryptionEngine.encryptRSA( aesKey ); 
				encryptedAudio = EncryptionEngine.encryptAES( readInAudioFile(), aesKey ); }
			catch (InvalidKeySpecException e) {
				Log.e("AudioFileManager", "encrypted write operation to the audio file without a keyFile.");
				throw new NullPointerException( e.getMessage() ); }
	        catch (InvalidKeyException e) {
	        	Log.e("AudioFileManager", "encrypted write operation to the audio file without an aes key? how is that even...");
				throw new NullPointerException( e.getMessage() ); }
			writePlaintext( encryptedRSA , fileName );
			writePlaintext( encryptedAudio, fileName );
		}
	}

	
    /** Writes string data to a the audio file. */
	private synchronized void writePlaintext(String data, String outputFileName){
		FileOutputStream outStream;
		try {  //We use MODE_APPEND because... we know it works.
			outStream = getApplicationContext().openFileOutput(outputFileName, Context.MODE_APPEND);
			outStream.write( ( data ).getBytes() );
			outStream.write( "\n".getBytes() );
			outStream.flush();
			outStream.close(); }
		catch (FileNotFoundException e) {
			Log.e("AudioRecording", "could not find file to write to, " + outputFileName);
			e.printStackTrace(); }
		catch (IOException e) {
			Log.e("AudioRecording", "error in the write operation: " + e.getMessage() );
			e.printStackTrace(); }
	}
    
	
	/** Reads a byte array of the current temp audio file's contents.
	 * @return byte array of file contents. */
	private synchronized byte[] readInAudioFile() {
		DataInputStream dataInputStream;
		byte[] data = null;
		try {  //Read the (data) input stream, into a bytearray.  Catch exceptions.
			File file = new File(unencryptedTempAudioFilePath);
			dataInputStream = new DataInputStream( new FileInputStream( file ) );	
			data = new byte[ (int) file.length() ];
			try{ dataInputStream.readFully(data); }
			catch (IOException e) { Log.i("DataFileManager", "error reading " + unencryptedTempAudioFilePath);
				e.printStackTrace(); }
			dataInputStream.close(); }
		catch (FileNotFoundException e) {
			Log.i("AudioRecording", "file " + unencryptedTempAudioFilePath + " does not exist");
			e.printStackTrace(); }
		catch (IOException e) {
			Log.i("AudioRecording", "could not close " + unencryptedTempAudioFilePath);
			e.printStackTrace(); }
		return data;
	}
}