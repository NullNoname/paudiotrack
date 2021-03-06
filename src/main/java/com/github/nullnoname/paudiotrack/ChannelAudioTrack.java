package com.github.nullnoname.paudiotrack;

import java.util.LinkedList;
import java.util.List;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import paulscode.sound.Channel;
import paulscode.sound.PAudioFormat;
import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;

/**
 * The ChannelAudioTrack class is used to reserve a sound-card voice using
 * Android's AudioTrack. Channels can be either normal or streaming channels.
 * This is basically an Android port of ChannelJavaSound, so I guess the same license would apply.
 *<br><br>
 *<b><i>    SoundSystem LibraryJavaSound License:</b></i><br><b><br>
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
 * @author NullNoname (port to Android)
 * @author Paul Lamb (original codes)
 */
public class ChannelAudioTrack extends Channel {
	/**
	 * Default stream buffer size (0 to set automatically)
	 */
	private static int defaultStreamBufferSize = 0;

	/**
	 * Default multiplier for stream buffer size (used when defaultStreamBufferSize == 0)
	 */
	private static int defaultStreamBufferSizeMultiplier = 4;

	/**
	 * The Android AudioTrack instance which is used for both normal and stream modes.
	 */
	private AudioTrack audioTrack;

	// NORMAL SOURCE VARRIABLES:
	/**
	 * The paulscode.sound.SoundBuffer containing the sound data to play for a normal source.
	 */
	SoundBuffer soundBuffer;
	// END NORMAL SOURCE VARRIABLES

	// STREAMING SOURCE VARRIABLES:
	/**
	 * List of paulscode.sound.SoundBuffer, used to queue chunks of sound data to be streamed.
	 */
	private List<SoundBuffer> streamBuffers;
	/**
	 * Number of queued stream-buffers that have finished being processed.
	 */
	private int processed = 0;

	// END STREAMING SOURCE VARRIABLES:
	/**
	 * Format to use when playing back the assigned source.
	 */
	private PAudioFormat myFormat = null;

	/**
	 * The initial decible change (start at normal volume).
	 */
	//private float initialGain = 0.0f;

	/**
	 * The initial sample rate for this channel.
	 */
	private float initialSampleRate = 0.0f;

	/**
	 * When toLoop is true, the assigned source is immediately replayed when the end is reached.
	 */
	private boolean toLoop = false;

	/**
	 * Current Gain
	 */
	private float currentGain = 1.0f;

	/**
	 * Current Pan
	 */
	private float currentPan = 0.0f;

	/**
	 * @return Default stream buffer size (0 to set automatically)
	 */
	public static int getDefaultStreamBufferSize() {
		return defaultStreamBufferSize;
	}

	/**
	 * Set the deault stream buffer size
	 * @param defaultStreamBufferSize Default stream buffer size (0 to set automatically)
	 */
	public static void setDefaultStreamBufferSize(int defaultStreamBufferSize) {
		ChannelAudioTrack.defaultStreamBufferSize = defaultStreamBufferSize;
	}

	/**
	 * @return Default multiplier for stream buffer size (used when defaultStreamBufferSize == 0)
	 */
	public static int getDefaultStreamBufferSizeMultiplier() {
		return defaultStreamBufferSizeMultiplier;
	}

	/**
	 * Set the default multiplier for stream buffer size (used when defaultStreamBufferSize == 0)
	 * @param defaultStreamBufferSizeMultiplier Default multiplier for stream buffer size
	 */
	public static void setDefaultStreamBufferSizeMultiplier(int defaultStreamBufferSizeMultiplier) {
		ChannelAudioTrack.defaultStreamBufferSizeMultiplier = defaultStreamBufferSizeMultiplier;
	}

	public ChannelAudioTrack(int type) {
		super(type);
		libraryType = LibraryAudioTrack.class;

		streamBuffers = new LinkedList<SoundBuffer>();
	}

	/**
	 * Empties the streamBuffers list, shuts the channel down and removes
	 * references to all instantiated objects.
	 */
	@Override
	public void cleanup() {
		close();
		if(streamBuffers != null) {
			SoundBuffer buf = null;
			while(!streamBuffers.isEmpty()) {
				buf = streamBuffers.remove(0);
				buf.cleanup();
				buf = null;
			}
			streamBuffers.clear();
		}
		soundBuffer = null;
		myFormat = null;
		streamBuffers = null;
		super.cleanup();
	}

	public static int getAudioEncoding(PAudioFormat format) {
		return (format.getSampleSizeInBits() == 8) ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;
	}

	public static int getChannelOutputType(PAudioFormat format) {
		return (format.getChannels() == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
	}

	/**
	 * Attaches the SoundBuffer to be played back for a normal source.
	 * @param buffer SoundBuffer containing the wave data and format to attach
	 * @return False if an error occurred.
	 */
	public boolean attachBuffer( SoundBuffer buffer ) {
		// Can only attach a buffer to a normal source:
		if(errorCheck(channelType != SoundSystemConfig.TYPE_NORMAL, "Buffers may only be attached to non-streaming " + "sources"))
			return false;

		// make sure the buffer exists:
		if(errorCheck(buffer == null, "Buffer null in method 'attachBuffer'"))
			return false;

		// make sure the buffer exists:
		if(errorCheck(buffer.audioData == null, "Buffer missing audio data in method " + "'attachBuffer'"))
			return false;

		// make sure there is format information about this sound buffer:
		if(errorCheck(buffer.audioFormat == null, "Buffer missing format information in method " + "'attachBuffer'"))
			return false;

		AudioTrack newAudioTrack = null;
		try {
			newAudioTrack = new AudioTrack(
				AudioManager.STREAM_MUSIC, (int)buffer.audioFormat.getSampleRate(),
				getChannelOutputType(buffer.audioFormat), getAudioEncoding(buffer.audioFormat), buffer.audioData.length, AudioTrack.MODE_STATIC
			);
		} catch (Exception e) {
			errorMessage("Unable to create AudioTrack in method 'attachBuffer'");
			printStackTrace(e);
			return false;
		}

		if(errorCheck(newAudioTrack == null, "New AudioTrack null in method 'attachBuffer'"))
			return false;

		// if there was already a clip playing on this channel, remove it now:
		if(audioTrack != null) {
			audioStop();
			audioFlush();
			audioRelease();
		}

		// Update the clip and format varriables:
		audioTrack = newAudioTrack;
		soundBuffer = buffer;
		myFormat = buffer.audioFormat;
		newAudioTrack = null;

		try {
			audioTrack.write(buffer.audioData, 0, buffer.audioData.length);
		} catch (Exception e) {
			errorMessage("Unable to attach buffer to clip in method " + "'attachBuffer'");
			printStackTrace(e);
			return false;
		}

		resetControls();

		// Success:
		return true;
	}

	@Override
	public void setAudioFormat(PAudioFormat audioFormat) {
		resetStream(audioFormat);
	}

	/**
	 * Sets the channel up to be streamed using the specified AudioFormat.
	 * @param format Format to use when playing the stream data.
	 * @return False if an error occurred.
	 */
	public boolean resetStream(PAudioFormat format) {
		// make sure a format was specified:
		if(errorCheck(format == null, "AudioFormat null in method 'resetStream'"))
			return false;

		AudioTrack newAudioTrack = null;
		try {
			//message("format.getSampleRate():" + format.getSampleRate());
			//message("format.getChannels():" + format.getChannels());
			//message("format.getSampleSizeInBits():" + format.getSampleSizeInBits());

			// Get the minimum buffer size
			int minBufferSize = AudioTrack.getMinBufferSize((int)format.getSampleRate(), getChannelOutputType(format), getAudioEncoding(format));

			// If streamBufferSize == 0, use minBufferSize. Otherwise use streamBufferSize as is.
			int bufSize = (getDefaultStreamBufferSize() == 0) ? (minBufferSize*getDefaultStreamBufferSizeMultiplier()) : getDefaultStreamBufferSize();
			message("Using stream mode with " + bufSize + " buffer size");

			newAudioTrack = new AudioTrack(
				AudioManager.STREAM_MUSIC, (int)format.getSampleRate(), getChannelOutputType(format), getAudioEncoding(format), bufSize, AudioTrack.MODE_STREAM
			);
		} catch (Exception e) {
			errorMessage("Unable to create AudioTrack in method 'attachBuffer'");
			printStackTrace(e);
			return false;
		}

		if(errorCheck(newAudioTrack == null, "New AudioTrack null in method 'attachBuffer'"))
			return false;

		streamBuffers.clear();
		processed = 0;

		// if there was already something playing on this channel, remove it:
		if(audioTrack != null) {
			audioStop();
			audioFlush();
			audioRelease();
		}

		// Update the clip and format varriables:
		audioTrack = newAudioTrack;
		myFormat = format;
		newAudioTrack = null;

		resetControls();

		// Success:
		return true;
	}

	/**
	 * (Re)Creates the pan and gain controls for this channel.
	 */
	private void resetControls() {
		if(audioTrack == null) return;
		//initialGain = 1f;
		initialSampleRate = audioTrack.getPlaybackRate();
		currentGain = 1.0f;
		currentPan = 0.0f;
		setAudioGainAndPan();
	}

	/**
	 * Defines whether playback should loop or just play once.
	 * @param value Loop or not.
	 */
	public void setLooping( boolean value )
	{
		toLoop = value;
	}

	/**
	 * Changes the pan between left and right speaker to the specified value.
	 * -1 = left speaker only.  0 = middle, both speakers.  1 = right speaker only.
	 * @param p Pan value to use.
	 */
	public void setPan(float p) {
		// Make sure there is a pan control
		if(audioTrack == null)
			return;
		float pan = p;
		// make sure the value is valid (between -1 and 1)
		if(pan < -1.0f)
			pan = -1.0f;
		if(pan > 1.0f)
			pan = 1.0f;
		// Update the pan:
		currentPan = pan;
		setAudioGainAndPan();
	}

	/**
	 * Changes the volume.
	 * 0 = no volume.  1 = maximum volume (initial gain)
	 * @param g Gain value to use.
	 */
	public void setGain(float g) {
		// Make sure there is a gain control
		if(audioTrack == null)
			return;

		// make sure the value is valid (between 0 and 1)
		float gain = g;
		if(gain < 0.0f)
			gain = 0.0f;
		if(gain > 1.0f)
			gain = 1.0f;

		// Update the gain:
		currentGain = gain;
		setAudioGainAndPan();
	}

	/**
	 * Set the current gain and pan to the AudioTrack
	 */
	private void setAudioGainAndPan() {
		// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/speech/tts/BlockingAudioTrack.java#318
		if(audioTrack == null)
			return;

		float volLeft = currentGain;
		float volRight = currentGain;
		if (currentPan > 0.0f) {
			volLeft *= (1.0f - currentPan);
		} else if (currentPan < 0.0f) {
			volRight *= (1.0f + currentPan);
		}

		if(audioTrack.setStereoVolume(volLeft, volRight) != AudioTrack.SUCCESS) {
			errorMessage("Failed to set volume to " + volLeft + "," + volRight);
		}
	}

	/**
	 * Changes the pitch to the specified value.
	 * @param p Float value between 0.5f and 2.0f.
	 */
	public void setPitch(float p) {
		// Make sure there is a pan control
		if(audioTrack == null) {
			return;
		}
		float sampleRate = p;

		// make sure the value is valid (between 0.5f and 2.0f)
		if(sampleRate < 0.5f)
			sampleRate = 0.5f;
		if(sampleRate > 2.0f)
			sampleRate = 2.0f;

		sampleRate = sampleRate * initialSampleRate;

		// Update the pitch:
		if(audioTrack.setPlaybackRate((int)sampleRate) != AudioTrack.SUCCESS) {
			errorMessage("Failed to set pitch to " + sampleRate);
		}
	}

	/**
	 * Queues up the initial byte[] buffers of data to be streamed.
	 * @param bufferList List of the first buffers to be played for a streaming source.
	 * @return False if problem occurred or end of stream was reached.
	 */
	@Override
	public boolean preLoadBuffers(LinkedList<byte[]> bufferList) {
		// Stream buffers can only be queued for streaming sources:
		if(errorCheck(channelType != SoundSystemConfig.TYPE_STREAMING, "Buffers may only be queued for streaming sources."))
			return false;

		// Make sure we have a AudioTrack:
		if(errorCheck(audioTrack == null, "AudioTrack null in method 'preLoadBuffers'."))
			return false;

		//sourceDataLine.start();
		audioPlay();	// start now for smooth start

		if(bufferList.isEmpty())
			return true;

		// preload one stream buffer worth of data:
		byte[] preLoad = bufferList.remove(0);

		// Make sure we have some data:
		if(errorCheck(preLoad == null, "Missing sound-bytes in method 'preLoadBuffers'."))
			return false;

		// If we are using more than one stream buffer, pre-load the
		// remaining ones now:
		while(!bufferList.isEmpty()) {
			streamBuffers.add(new SoundBuffer(bufferList.remove(0), myFormat));
		}

		// Pre-load the first stream buffer into the dataline:
		audioTrack.write(preLoad, 0, preLoad.length);

		processed = 0;
		return true;
	}

	/**
	 * Queues up a byte[] buffer of data to be streamed.
	 * @param buffer The next buffer to be played for a streaming source.
	 * @return False if an error occurred or if the channel is shutting down.
	 */
	@Override
	public boolean queueBuffer(byte[] buffer) {
		// Stream buffers can only be queued for streaming sources:
		if(errorCheck(channelType != SoundSystemConfig.TYPE_STREAMING, "Buffers may only be queued for streaming sources."))
			return false;

		// Make sure we have a AudioTrack:
		if(errorCheck(audioTrack == null, "AudioTrack null in method 'queueBuffer'."))
			return false;

		// make sure a format was specified:
		if(errorCheck(myFormat == null, "AudioFormat null in method 'queueBuffer'"))
			return false;

		// Queue a new buffer:
		streamBuffers.add(new SoundBuffer(buffer, myFormat));

		// Dequeue a buffer and process it:
		processBuffer();

		processed = 0;

		return true;
	}

	/**
	 * Plays the next queued byte[] buffer.  This method is run from the seperate
	 * {@link paulscode.sound.StreamThread StreamThread}.
	 * @return False when no more buffers are left to process.
	 */
	@Override
	public boolean processBuffer() {
		// Stream buffers can only be queued for streaming sources:
		if(errorCheck(channelType != SoundSystemConfig.TYPE_STREAMING, "Buffers are only processed for streaming sources."))
			return false;

		// Make sure we have a AudioTrack:
		if(errorCheck(audioTrack == null, "AudioTrack null in method 'processBuffer'."))
			return false;

		if(streamBuffers == null || streamBuffers.isEmpty())
			return false;

		// Dequeue a buffer and feed it to the SourceDataLine:
		SoundBuffer nextBuffer = streamBuffers.remove(0);

		audioTrack.write(nextBuffer.audioData, 0, nextBuffer.audioData.length);
		if(!playing())
			audioPlay();
		nextBuffer.cleanup();
		nextBuffer = null;
		return true;
	}

	/**
	 * Feeds raw data to the stream.
	 * @param buffer Buffer containing raw audio data to stream.
	 * @return Number of prior buffers that have been processed, or -1 if error.
	 */
	@Override
	public int feedRawAudioData(byte[] buffer) {
		// Stream buffers can only be queued for streaming sources:
		if(errorCheck(channelType != SoundSystemConfig.TYPE_STREAMING, "Raw audio data can only be processed by streaming sources."))
			return -1;

		if(errorCheck(streamBuffers == null, "StreamBuffers queue null in method 'feedRawAudioData'."))
			return -1;

		streamBuffers.add(new SoundBuffer(buffer, myFormat));

		return buffersProcessed();
	}

	/**
	 * Returns the number of queued byte[] buffers that have finished playing.
	 * @return Number of buffers processed.
	 */
	@Override
	public int buffersProcessed() {
		processed = 0;

		// Stream buffers can only be queued for streaming sources:
		if(errorCheck(channelType != SoundSystemConfig.TYPE_STREAMING, "Buffers may only be queued for streaming sources.")) {
			if(streamBuffers != null)
				streamBuffers.clear();
			return 0;
		}

		// Make sure we have a SourceDataLine:
		if(audioTrack == null) {
			importantMessage("buffersProcessed audioTrack == null");
			if(streamBuffers != null)
				streamBuffers.clear();
			return 0;
		}

		//TODO: Does AudioTrack have an equivalent?
		//if(sourceDataLine.available() > 0) {
			processed = 1;
		//}

		return processed;
	}

	/**
	 * Dequeues all previously queued data.
	 */
	@Override
	public void flush() {
		// only a streaming source can be flushed:
		// Only streaming sources process buffers:
		if(channelType != SoundSystemConfig.TYPE_STREAMING)
			return;

		// Make sure we have a AudioTrack:
		if(errorCheck(audioTrack == null, "AudioTrack null in method 'flush'."))
			return;

		audioStop();
		audioFlush();
		//sourceDataLine.drain();

		streamBuffers.clear();
		processed = 0;
	}

	/**
	 * Stops the channel, dequeues any queued data, and closes the channel.
	 */
	@Override
	public void close() {
		switch(channelType) {
			case SoundSystemConfig.TYPE_NORMAL:
				if(audioTrack != null) {
					audioStop();
					audioFlush();
					audioRelease();
				}
				break;
			case SoundSystemConfig.TYPE_STREAMING:
				if(audioTrack != null) {
					flush();
					audioRelease();
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Plays the currently attached normal source, opens this channel up for
	 * streaming, or resumes playback if this channel was paused.
	 */
	@Override
	public void play() {
		switch(channelType) {
			case SoundSystemConfig.TYPE_NORMAL:
				if(audioTrack != null) {
					audioStop();
					int errorCode;
					if(toLoop && soundBuffer != null && soundBuffer.audioFormat != null && soundBuffer.audioData != null) {
						int bytesPerFrame = soundBuffer.audioFormat.getSampleSizeInBits() / 8;
						errorCode = audioTrack.setLoopPoints(0, soundBuffer.audioData.length / bytesPerFrame, -1);
					} else {
						errorCode = audioTrack.setLoopPoints(0, 0, 0);
					}
					if(errorCode != AudioTrack.SUCCESS) {
						errorMessage("Problem setting loop points (errorCode:" + errorCode + ")");
					}
					audioPlay();
				}
				break;
			case SoundSystemConfig.TYPE_STREAMING:
				if(audioTrack != null) {
					audioPlay();
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Temporarily stops playback for this channel.
	 */
	@Override
	public void pause() {
		if(audioTrack != null) {
			audioTrack.pause();
		}
	}

	/**
	 * Stops playback for this channel and rewinds the attached source to the
	 * beginning.
	 */
	@Override
	public void stop() {
		if(audioTrack != null) {
			audioStop();
			if(channelType == SoundSystemConfig.TYPE_NORMAL) audioTrack.reloadStaticData();
		}
	}

	/**
	 * Rewinds the attached source to the beginning.  Stops the source if it was
	 * paused.
	 */
	@Override
	public void rewind() {
		switch(channelType) {
			case SoundSystemConfig.TYPE_NORMAL:
				if(audioTrack != null) {
					boolean rePlay = playing();
					audioStop();
					audioTrack.reloadStaticData();
					if(rePlay) {
						int errorCode;
						if(toLoop && soundBuffer != null && soundBuffer.audioFormat != null && soundBuffer.audioData != null) {
							int bytesPerFrame = soundBuffer.audioFormat.getSampleSizeInBits() / 8;
							errorCode = audioTrack.setLoopPoints(0, soundBuffer.audioData.length / bytesPerFrame, -1);
						} else {
							errorCode = audioTrack.setLoopPoints(0, 0, 0);
						}
						if(errorCode != AudioTrack.SUCCESS) {
							errorMessage("Problem setting loop points (errorCode:" + errorCode + ")");
						}
						audioPlay();
					}
				}
				break;
			case SoundSystemConfig.TYPE_STREAMING:
				// rewinding for streaming sources is handled elsewhere
				break;
			default:
				break;
		}
	}

	/**
	 * Calculates the number of milliseconds since the channel began playing.
	 * @return Milliseconds, or -1 if unable to calculate.
	 */
	@Override
	public float millisecondsPlayed() {
		if(audioTrack == null) return -1;
		float p = ( audioTrack.getPlaybackHeadPosition() / myFormat.getSampleRate() ) * 1000f;
		return p;
	}

	/**
	 * Used to determine if a channel is actively playing a source.  This method
	 * will return false if the channel is paused or stopped and when no data is
	 * queued to be streamed.
	 * @return True if this channel is playing a source.
	 */
	@Override
	public boolean playing() {
		// Make sure an AudioTrack exists
		if(audioTrack == null)
			return false;

		// Make sure it is in playing state
		if(audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
			return false;

		// In stream mode, check if we have something to play
		if(channelType == SoundSystemConfig.TYPE_STREAMING) {
			if(streamBuffers == null || streamBuffers.isEmpty())
				return false;
		}

		return true;
	}

	/**
	 * Stop the current channel. Report errors when something goes wrong.
	 */
	private void audioPlay() {
		if(audioTrack != null) {
			try {
				audioTrack.play();
			} catch (IllegalStateException e) {
				// Channel not ready
			} catch (Exception e) {
				errorMessage("Problem during audioTrack.play()");
				printStackTrace(e);
			}
		}
	}

	/**
	 * Stop the current channel. Report warnings when something goes horribly wrong.
	 */
	private void audioStop() {
		if(audioTrack != null) {
			try {
				audioTrack.stop();
			} catch (IllegalStateException e) {
				// Stopping an already stopped channel causes this
			} catch (Exception e) {
				importantMessage("Problem during audioTrack.stop()");
				printStackTrace(e);
			}
		}
	}

	/**
	 * Flush the current channel. Report warnings when something goes wrong.
	 */
	private void audioFlush() {
		if(audioTrack != null) {
			try {
				audioTrack.flush();
			} catch (Exception e) {
				importantMessage("Problem during audioTrack.flush()");
				printStackTrace(e);
			}
		}
	}

	/**
	 * Release the current channel. Report warnings when something goes wrong.
	 */
	private void audioRelease() {
		if(audioTrack != null) {
			try {
				audioTrack.release();
			} catch (Exception e) {
				importantMessage("Problem during audioTrack.release()");
				printStackTrace(e);
			}
		}
	}
}
