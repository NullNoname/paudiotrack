package com.github.nullnoname.paudiotrack;

import java.io.FileDescriptor;
import java.util.LinkedList;
import java.util.ListIterator;

import android.media.MediaPlayer;
import paulscode.sound.FileDescriptorWrapper;
import paulscode.sound.FilenameURL;
import paulscode.sound.IMidiChannel;
import paulscode.sound.SimpleThread;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.SoundSystemLogger;

/**
 * An IMidiChannel implementation that uses Android MediaPlayer.
 *<br><br>
 *<b><i>    SoundSystem License:</b></i><br><b><br>
 *    You are free to use this library for any purpose, commercial or otherwise.
 *    You may modify this library or source code, and distribute it any way you
 *    like, provided the following conditions are met:
 *<br>
 *    1) You may not falsely claim to be the author of this library or any
 *    unmodified portion of it.
 *<br>
 *    2) You may not copyright this library or a modified version of it and then
 *    sue me for copyright infringement.
 *<br>
 *    3) If you modify the source code, you must clearly document the changes
 *    made before redistributing the modified source code, so other users know
 *    it is not the original code.
 *<br>
 *    4) You are not required to give me credit for this library in any derived
 *    work, but if you do, you must also mention my website:
 *    http://www.paulscode.com
 *<br>
 *    5) I the author will not be responsible for any damages (physical,
 *    financial, or otherwise) caused by the use if this library or any part
 *    of it.
 *<br>
 *    6) I the author do not guarantee, warrant, or make any representations,
 *    either expressed or implied, regarding the use of this library or any
 *    part of it.
 * <br><br>
 *    Author: Paul Lamb
 * <br>
 *    http://www.paulscode.com
 * </b>
 * @author Paul Lamb (Original codes)
 * @author NullNoname (Android port)
 */
public class MPMidiChannel implements IMidiChannel {
	/**
	 * Used to return a current value from one of the synchronized
	 * boolean-interface methods.
	 */
	private static final boolean GET = false;

	/**
	 * Used to set the value in one of the synchronized boolean-interface methods.
	 */
	private static final boolean SET = true;

	/**
	 * Used when a parameter for one of the synchronized boolean-interface methods
	 * is not aplicable.
	 */
	private static final boolean XXX = false;

	/**
	 * Processes status messages, warnings, and error messages.
	 */
	private SoundSystemLogger logger;

	/**
	 * Filename/URL to the file
	 */
	private FilenameURL filenameURL;

	/**
	 * Unique source identifier for this MIDI source.
	 */
	private String sourcename;

	/**
	 * Android MediaPlayer
	 */
	private MediaPlayer mp;

	/**
	 * Should playback loop or play only once.
	 */
	private boolean toLoop = true;

	/**
	 * Playback volume, float value (0.0f - 1.0f).
	 */
	private float gain = 1.0f;

	/**
	 * True while MediaPlayer is busy being set up.
	 */
	private boolean loading = true;

	/**
	 * false if data source is not yet loaded into MediaPlayer (like after the reset), true if ready
	 */
	private boolean dataSourceLoaded = false;

	/**
	 * The list of MIDI files to play when the current sequence finishes.
	 */
	private LinkedList<FilenameURL> sequenceQueue = null;

	/**
	 * Ensures that only one thread accesses the sequenceQueue at a time.
	 */
	private final Object sequenceQueueLock = new Object();

	/**
	 * Specifies the gain factor used for the fade-out effect, or -1 when playback is not currently fading out.
	 */
	protected float fadeOutGain = -1.0f;

	/**
	 * Specifies the gain factor used for the fade-in effect, or 1 when playback is not currently fading in.
	 */
	protected float fadeInGain = 1.0f;

	/**
	 * Specifies the number of miliseconds it should take to fade out.
	 */
	protected long fadeOutMilis = 0;

	/**
	 * Specifies the number of miliseconds it should take to fade in.
	 */
	protected long fadeInMilis = 0;

	/**
	 * System time in miliseconds when the last fade in/out volume check occurred.
	 */
	protected long lastFadeCheck = 0;

	/**
	 * Used for fading in and out effects.
	 */
	private FadeThread fadeThread = null;

	/**
	 * Constructor: Defines the basic source information.
	 * @param toLoop Should playback loop or play only once?
	 * @param sourcename Unique identifier for this source.
	 * @param midiFilenameURL Filename/URL to the MIDI file to play.
	 */
	public MPMidiChannel( boolean toLoop, String sourcename, FilenameURL midiFilenameURL )
	{
		// let others know we are busy loading
		loading( SET, true );

		// grab a handle to the message logger:
		logger = SoundSystemConfig.getLogger();

		// save information about the source:
		filenameURL( SET, midiFilenameURL );
		sourcename( SET, sourcename );
		setLooping( toLoop );

		// initialize the MIDI channel:
		init();

		// finished loading:
		loading( SET, false );
	}

	private void init() {
		// Create an Android MediaPlayer:
		mp = new MediaPlayer();

		// Load the sequence to play:
		setDataSourceToMediaPlayer();
	}

	/**
	 * Load the MIDI and initialize the MediaPlayer
	 */
	private void setDataSourceToMediaPlayer() {
		// Load MIDI
		setSequence(filenameURL(GET, null));
		// Initialize the MediaPlayer
		prepare();
		// Ensure the initial volume is correct:
		resetGain();
		// Done
		dataSourceLoaded = true;
	}

	/**
	 * Loads the MIDI sequence form the specified URL, and sets the sequence.
	 * If variable 'MediaPlayer' is null or an error occurs, then variable 'sequence' remains null.
	 * @param filenameURL FilenameURL to a MIDI file.
	 */
	private void setSequence(FilenameURL filenameURL) {
		if(mp == null) {
			errorMessage("Unable to update the sequence in method " + "'setSequence', because variable 'mp' " + "is null");
			return;
		}

		if(filenameURL == null) {
			errorMessage("Unable to load Midi file in method 'setSequence'.");
			return;
		}

		message("setSequence filename:" + filenameURL.getFilename());

		// MediaPlayer only accepts FileDescriptor or a plain URL string
		boolean success = false;
		FileDescriptorWrapper fdw = null;
		try {
			fdw = filenameURL.openFileDescriptorWrapper();
			if(fdw != null) {
				long length = filenameURL.getContentLength();
				long offset = filenameURL.getContentStartOffset();
				FileDescriptor fd = fdw.getFileDescriptor();
				mp.setDataSource(fd, offset, length);
				success = true;
			}
		} catch (Exception e) {
			errorMessage( "Problem setting sequence from MIDI file in method 'setSequence' via FileDescriptor." );
			printStackTrace(e);
		} finally {
			try {fdw.close();} catch (Exception ignore) {}
		}

		// If FileDescriptor fails, try URL string
		if(!success) {
			try {
				String sURL = filenameURL.getURL().toString();
				mp.setDataSource(sURL);
				message("Loaded '" + sURL + "' using a String URL");
				success = true;
			} catch (Exception e) {
				errorMessage( "Problem setting sequence from MIDI file in method 'setSequence' via a String URL." );
				printStackTrace(e);
			}
		}
	}

	private boolean prepare() {
		try {
			mp.prepare();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void cleanup() {
		loading( SET, true );

		setLooping(true);

		if(mp != null) {
			try {
				mp.release();
			} catch (Exception e) {}
		}
		mp = null;
		dataSourceLoaded = false;

		logger = null;

		synchronized(sequenceQueueLock) {
			if(sequenceQueue != null)
				sequenceQueue.clear();
			sequenceQueue = null;
		}

		// End the fade effects thread if it exists:
		if(fadeThread != null) {
			boolean killException = false;
			try {
				fadeThread.kill(); // end the fade effects thread.
				fadeThread.interrupt(); // wake the thread up so it can end.
			} catch (Exception e) {
				killException = true;
			}

			if(!killException) {
				// wait up to 5 seconds for fade effects thread to end:
				for(int i = 0; i < 50; i++) {
					if(!fadeThread.alive())
						break;
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {}
				}
			}

			// Let user know if there was a problem ending the fade thread
			if(killException || fadeThread.alive()) {
				errorMessage("MIDI fade effects thread did not die!");
				message("Ignoring errors... continuing clean-up.");
			}
		}

		fadeThread = null;

		loading( SET, false );
	}

	public void queueSound(FilenameURL filenameURL) {
		if(filenameURL == null) {
			errorMessage("Filename/URL not specified in method 'queueSound'");
			return;
		}

		synchronized(sequenceQueueLock) {
			if(sequenceQueue == null)
				sequenceQueue = new LinkedList<FilenameURL>();
			sequenceQueue.add(filenameURL);
		}
	}

	public void dequeueSound(String filename) {
		if(filename == null || filename.equals("")) {
			errorMessage("Filename not specified in method 'dequeueSound'");
			return;
		}

		synchronized(sequenceQueueLock) {
			if(sequenceQueue != null) {
				ListIterator<FilenameURL> i = sequenceQueue.listIterator();
				while(i.hasNext()) {
					if(i.next().getFilename().equals(filename)) {
						i.remove();
						break;
					}
				}
			}
		}
	}

	public void fadeOut(FilenameURL filenameURL, long milis) {
		if(milis < 0) {
			errorMessage("Miliseconds may not be negative in method " + "'fadeOut'.");
			return;
		}

		fadeOutMilis = milis;
		fadeInMilis = 0;
		fadeOutGain = 1.0f;
		lastFadeCheck = System.currentTimeMillis();

		synchronized(sequenceQueueLock) {
			if(sequenceQueue != null)
				sequenceQueue.clear();

			if(filenameURL != null) {
				if(sequenceQueue == null)
					sequenceQueue = new LinkedList<FilenameURL>();
				sequenceQueue.add(filenameURL);
			}
		}
		if(fadeThread == null) {
			fadeThread = new FadeThread();
			fadeThread.start();
		}
		fadeThread.interrupt();
	}

	public void fadeOutIn(FilenameURL filenameURL, long milisOut, long milisIn) {
		if(filenameURL == null) {
			errorMessage("Filename/URL not specified in method 'fadeOutIn'.");
			return;
		}
		if(milisOut < 0 || milisIn < 0) {
			errorMessage("Miliseconds may not be negative in method " + "'fadeOutIn'.");
			return;
		}

		fadeOutMilis = milisOut;
		fadeInMilis = milisIn;
		fadeOutGain = 1.0f;
		lastFadeCheck = System.currentTimeMillis();

		synchronized(sequenceQueueLock) {
			if(sequenceQueue == null)
				sequenceQueue = new LinkedList<FilenameURL>();
			sequenceQueue.clear();
			sequenceQueue.add(filenameURL);
		}
		if(fadeThread == null) {
			fadeThread = new FadeThread();
			fadeThread.start();
		}
		fadeThread.interrupt();
	}

	/**
	 * Resets this source's volume if it is fading out or in.  Returns true if this
	 * source is currently in the process of fading out.  When fade-out completes,
	 * this method transitions the source to the next sound in the sound sequence
	 * queue if there is one.  This method has no effect on non-streaming sources.
	 * @return True if this source is in the process of fading out.
	 */
	private synchronized boolean checkFadeOut() {
		if(fadeOutGain == -1.0f && fadeInGain == 1.0f)
			return false;

		long currentTime = System.currentTimeMillis();
		long milisPast = currentTime - lastFadeCheck;
		lastFadeCheck = currentTime;

		if(fadeOutGain >= 0.0f) {
			if(fadeOutMilis == 0) {
				fadeOutGain = 0.0f;
				fadeInGain = 0.0f;
				if(!incrementSequence())
					stop();
				rewind();
				resetGain();
				return false;
			} else {
				float fadeOutReduction = ((float) milisPast) / ((float) fadeOutMilis);

				fadeOutGain -= fadeOutReduction;
				if(fadeOutGain <= 0.0f) {
					fadeOutGain = -1.0f;
					fadeInGain = 0.0f;
					if(!incrementSequence())
						stop();
					rewind();
					resetGain();
					return false;
				}
			}
			resetGain();
			return true;
		}

		if(fadeInGain < 1.0f) {
			fadeOutGain = -1.0f;
			if(fadeInMilis == 0) {
				fadeOutGain = -1.0f;
				fadeInGain = 1.0f;
			} else {
				float fadeInIncrease = ((float) milisPast) / ((float) fadeInMilis);
				fadeInGain += fadeInIncrease;
				if(fadeInGain >= 1.0f) {
					fadeOutGain = -1.0f;
					fadeInGain = 1.0f;
				}
			}
			resetGain();
		}

		return false;
	}

	/**
	 * Removes the next sequence from the queue and assigns it to the sequencer.
	 * @return True if there was something in the queue.
	 */
	private boolean incrementSequence() {
		synchronized(sequenceQueueLock) {
			// Is there a queue, and if so, is there anything in it:
			if(sequenceQueue != null && sequenceQueue.size() > 0) {
				// grab the next filename/URL from the queue:
				filenameURL(SET, sequenceQueue.remove(0));

				// Let everyone know we are busy loading:
				loading(SET, true);

				// Stop and reset the instance of MediaPlayer:
				stop();
				// wait a bit for the MediaPlayer to shut down and rewind:
				try {Thread.sleep(100);} catch (InterruptedException e) {}

				// start playing again:
				play();
				// make sure we play at the correct volume:
				// (TODO: This doesn't always work??)
				resetGain();

				// Finished loading:
				loading(SET, false);

				// We successfully moved to the next sequence:
				return true;
			}
		}

		// Nothing left to load
		return false;
	}

	public void play() {
		if(!loading()) {
			// Make sure there is a MediaPlayer:
			if(mp == null)
				return;

			try {
				// Start playing
				if(!dataSourceLoaded) {
					// Reload the MIDI after a reset
					setDataSourceToMediaPlayer();
				}
				mp.setLooping(toLoop(GET, XXX));
				resetGain();	// set volume
				mp.start();
			} catch (Exception e) {
				errorMessage("Exception in method 'play'");
				printStackTrace(e);
				SoundSystemException sse = new SoundSystemException(e.getMessage());
				SoundSystem.setException(sse);
			}
		}
	}

	public void stop() {
		if(mp != null) {
			try {
				mp.stop();
				// we can't replay the MIDI unless reset is used
				mp.reset();
				dataSourceLoaded = false;
			} catch (Exception e) {}
		}
	}

	public void pause() {
		if(mp != null) {
			try {
				mp.pause();
			} catch (Exception e) {}
		}
	}

	public void rewind() {
		if(mp != null) {
			try {
				// rewind to the beginning:
				mp.seekTo(0);
			} catch (Exception e) {}
		}
	}

	public void setVolume(float value) {
		gain = value;
		resetGain();
	}

	public float getVolume() {
		return gain;
	}

	public void switchSource(boolean toLoop, String sourcename, FilenameURL filenameURL) {
		// Let everyone know we are busy loading:
		loading(SET, true);

		// save information about the source:
		filenameURL(SET, filenameURL);
		sourcename(SET, sourcename);
		setLooping(toLoop);

		reset();

		// Finished loading:
		loading(SET, false);
	}

	/**
	 * Stops and rewinds the MediaPlayer, and resets the MediaPlayer.
	 */
	private void reset() {
		synchronized(sequenceQueueLock) {
			if(sequenceQueue != null)
				sequenceQueue.clear();
		}

		// Stop and reset the instance of MediaPlayer:
		stop();
		// wait a bit for the MediaPlayer to shut down and rewind:
		try {Thread.sleep(100);} catch (InterruptedException e) {}

		// start playing again:
		play();
		// make sure we play at the correct volume:
		resetGain();
	}

	public void setLooping(boolean value) {
		toLoop(SET, value);
	}

	public boolean getLooping() {
		return toLoop(GET, XXX);
	}

	/**
	 * Sets or returns the value of boolean 'toLoop'.
	 * @param action GET or SET.
	 * @param value New value if action == SET, or XXX if action == GET.
	 * @return True while looping.
	 */
	private synchronized boolean toLoop(boolean action, boolean value) {
		if(action == SET)
			toLoop = value;
		return toLoop;
	}

	public boolean loading() {
		return (loading(GET, XXX));
	}

	/**
	 * Sets or returns the value of boolean 'loading'.
	 * @param action GET or SET.
	 * @param value New value if action == SET, or XXX if action == GET.
	 * @return True while a MIDI file is in the process of loading.
	 */
	private synchronized boolean loading(boolean action, boolean value) {
		if(action == SET)
			loading = value;
		return loading;
	}

	public void setSourcename(String value) {
		sourcename(SET, value);
	}

	public String getSourcename() {
		return sourcename(GET, null);
	}

	/**
	 * Sets or returns the value of String 'sourcename'.
	 * @param action GET or SET.
	 * @param value New value if action == SET, or null if action == GET.
	 * @return The source's name.
	 */
	private synchronized String sourcename(boolean action, String value) {
		if(action == SET)
			sourcename = value;
		return sourcename;
	}

	public void setFilenameURL(FilenameURL value) {
		filenameURL(SET, filenameURL);
	}

	public String getFilename() {
		return filenameURL(GET, null).getFilename();
	}

	public FilenameURL getFilenameURL() {
		return filenameURL(GET, null);
	}

	/**
	 * Sets or returns the value of filenameURL.
	 * @param action GET or SET.
	 * @param value New value if action == SET, or null if action == GET.
	 * @return Path to the MIDI file.
	 */
	private synchronized FilenameURL filenameURL(boolean action, FilenameURL value)
	{
		if( action == SET )
			filenameURL = value;
		return filenameURL;
	}

    public void resetGain() {
		// make sure the value for gain is valid (between 0 and 1)
		if(gain < 0.0f)
			gain = 0.0f;
		if(gain > 1.0f)
			gain = 1.0f;

		float vol = gain * SoundSystemConfig.getMasterGain() * Math.abs( fadeOutGain ) * fadeInGain;
		if(vol < 0.0f) vol = 0.0f;
		if(vol > 1.0f) vol = 1.0f;

		if(mp != null) {
			try {
				mp.setVolume(vol, vol);
			} catch (Exception e) {}
		}
	}

	/**
	 * Prints a message.
	 * @param message Message to print.
	 */
	protected void message(String message) {
		logger.message(message, 0);
	}

	/**
	 * Prints an important message.
	 * @param message Message to print.
	 */
	protected void importantMessage(String message) {
		logger.importantMessage(message, 0);
	}

	/**
	 * Prints the specified message if error is true.
	 * @param error True or False.
	 * @param message Message to print if error is true.
	 * @return True if error is true.
	 */
	protected boolean errorCheck(boolean error, String message) {
		return logger.errorCheck(error, "MPMidiChannel", message, 0);
	}

	/**
	 * Prints an error message.
	 * @param message Message to print.
	 */
	protected void errorMessage(String message) {
		logger.errorMessage("MPMidiChannel", message, 0);
	}

	/**
	 * Prints an exception's error message followed by the stack trace.
	 * @param e Exception containing the information to print.
	 */
	protected void printStackTrace(Exception e) {
		logger.printStackTrace(e, 1);
	}

	/**
	 * The FadeThread class handles sequence changing, timing, and volume change messages in the background.
	 */
	private class FadeThread extends SimpleThread {
		/**
		 * Runs in the background, timing fade in and fade out, changing the sequence,
		 * and issuing the appropriate volume change messages.
		 */
		@Override
		public void run() {
			while(!dying()) {
				// if not currently fading in or out, put the thread to sleep
				if(fadeOutGain == -1.0f && fadeInGain == 1.0f)
					snooze(3600000);
				checkFadeOut();
				// only update every 50 miliseconds (no need to peg the cpu)
				snooze(50);
			}
			// Important!
			cleanup();
		}
	}
}
