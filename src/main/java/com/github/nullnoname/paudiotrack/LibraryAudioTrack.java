package com.github.nullnoname.paudiotrack;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import paulscode.sound.Channel;
import paulscode.sound.FilenameURL;
import paulscode.sound.ICodec;
import paulscode.sound.Library;
import paulscode.sound.PAudioFormat;
import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.Source;

/**
 * The LibraryAudioTrack class interfaces the Android AudioTrack library.
 * This is basically an Android port of LibraryJavaSound, so I guess the same license would apply.
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
public class LibraryAudioTrack extends Library {
	/**
	 * The maximum safe size for a JavaSound clip.
	 */
	private final int maxClipSize = 1048576;

	public LibraryAudioTrack() throws SoundSystemException {
		super();
		reverseByteOrder = reversByteOrder();
	}

	/**
	 * Initialize LibraryAudioTrack.
	 */
	@Override
	public void init() throws SoundSystemException {
		super.init();
		message("AudioTrack library initialized.");
	}

	/**
	 * Checks if the AudioTrack library type is compatible.
	 * @return True or false.
	 */
	public static boolean libraryCompatible()
	{
		try {
			Class.forName("android.media.AudioTrack");
		} catch (Throwable e) {
			return false;
		}
		return true;
	}

	/**
	 * Creates a new channel of the specified type (normal or streaming).  Possible
	 * values for channel type can be found in the
	 * {@link paulscode.sound.SoundSystemConfig SoundSystemConfig} class.
	 * @param type Type of channel.
	 */
	@Override
	protected Channel createChannel(int type) {
		return new ChannelAudioTrack(type);
	}

	/**
	 * Stops all sources, and removes references to all instantiated objects.
	 */
	@Override
	public void cleanup() {
		super.cleanup();
	}

	/**
	 * Pre-loads a sound into memory.
	 * @param filenameURL Filename/URL of a sound file to load.
	 * @return True if the sound loaded properly.
	 */
	@Override
	public boolean loadSound(FilenameURL filenameURL) {
        // Make sure the buffer map exists:
        if( bufferMap == null )
        {
            bufferMap = new HashMap<String, SoundBuffer>();
            importantMessage( "Buffer Map was null in method 'loadSound'" );
        }

        // make sure they gave us a filename:
        if( errorCheck( filenameURL == null,
                          "Filename/URL not specified in method 'loadSound'" ) )
            return false;

        // check if it is already loaded:
        if( bufferMap.get( filenameURL.getFilename() ) != null )
            return true;

        ICodec codec = SoundSystemConfig.getCodec( filenameURL.getFilename() );
        if( errorCheck( codec == null, "No codec found for file '" +
                                       filenameURL.getFilename() +
                                       "' in method 'loadSound'" ) )
            return false;

        URL url = filenameURL.getURL();

        if( errorCheck( url == null, "Unable to open file '" +
                                     filenameURL.getFilename() +
                                     "' in method 'loadSound'" ) )
            return false;

        codec.reverseByteOrder(reverseByteOrder());
        codec.initialize( filenameURL );
        message("Now Loading:" + url.toString());
        SoundBuffer buffer = codec.readAll();
        message(filenameURL.getFilename() + " loaded");
        codec.cleanup();
        codec = null;
        if( buffer != null )
            bufferMap.put( filenameURL.getFilename(), buffer );
        else
            errorMessage( "Sound buffer null in method 'loadSound'" );

        return true;
	}

	/**
	 * Saves the specified sample data, under the specified identifier.  This
	 * identifier can be later used in place of 'filename' parameters to reference
	 * the sample data.
	 * @param buffer the sample data and audio format to save.
	 * @param identifier What to call the sample.
	 * @return True if there weren't any problems.
	 */
    @Override
    public boolean loadSound( SoundBuffer buffer, String identifier )
    {
        // Make sure the buffer map exists:
        if( bufferMap == null )
        {
            bufferMap = new HashMap<String, SoundBuffer>();
            importantMessage( "Buffer Map was null in method 'loadSound'" );
        }

        // make sure they gave us an identifier:
        if( errorCheck(identifier == null,
                          "Identifier not specified in method 'loadSound'" ) )
            return false;

        // check if it is already loaded:
        if( bufferMap.get( identifier ) != null )
            return true;

        // save it for later:
        if( buffer != null )
            bufferMap.put( identifier, buffer );
        else
            errorMessage( "Sound buffer null in method 'loadSound'" );

        return true;
    }

	/**
	 * Sets the overall volume to the specified value, affecting all sources.
	 * @param value New volume, float value ( 0.0f - 1.0f ).
	 */
    @Override
    public void setMasterVolume( float value )
    {
        super.setMasterVolume( value );

        Set<String> keys = sourceMap.keySet();
        Iterator<String> iter = keys.iterator();
        String sourcename;
        Source source;

        // loop through and update the volume of all sources:
        while( iter.hasNext() )
        {
            sourcename = iter.next();
            source = sourceMap.get( sourcename );
            if( source != null )
                source.positionChanged();
        }
    }

	/**
	 * Creates a new source and places it into the source map.
	 * @param priority Setting this to true will prevent other sounds from overriding this one.
	 * @param toStream Setting this to true will load the sound in pieces rather than all at once.
	 * @param toLoop Should this source loop, or play only once.
	 * @param sourcename A unique identifier for this source.  Two sources may not use the same sourcename.
	 * @param filenameURL Filename/URL of the sound file to play at this source.
	 * @param x X position for this source.
	 * @param y Y position for this source.
	 * @param z Z position for this source.
	 * @param attModel Attenuation model to use.
	 * @param distOrRoll Either the fading distance or rolloff factor, depending on the value of "attmodel".
	 */
	@Override
	public void newSource(boolean priority, boolean toStream, boolean toLoop, String sourcename, FilenameURL filenameURL, float x, float y, float z, int attModel, float distOrRoll) {
		SoundBuffer buffer = null;

		if(!toStream) {
			// Grab the audio data for this file:
			buffer = bufferMap.get(filenameURL.getFilename());
			// if not found, try loading it:
			if(buffer == null) {
				if(!loadSound(filenameURL)) {
					errorMessage("Source '" + sourcename + "' was not created " + "because an error occurred while loading " + filenameURL.getFilename());
					return;
				}
			}
			// try and grab the sound buffer again:
			buffer = bufferMap.get(filenameURL.getFilename());
			// see if it was there this time:
			if(buffer == null) {
				errorMessage("Source '" + sourcename + "' was not created " + "because audio data was not found for " + filenameURL.getFilename());
				return;
			}
		}

		if(!toStream && buffer != null)
			buffer.trimData(maxClipSize);

		sourceMap.put(sourcename, new SourceAudioTrack(listener, priority, toStream, toLoop, sourcename, filenameURL, buffer, x, y, z, attModel, distOrRoll, false));
	}

	/**
	 * Opens a direct line for streaming audio data.
	 * @param audioFormat Format that the data will be in.
	 * @param priority Setting this to true will prevent other sounds from overriding this one.
	 * @param sourcename A unique identifier for this source.  Two sources may not use the same sourcename.
	 * @param x X position for this source.
	 * @param y Y position for this source.
	 * @param z Z position for this source.
	 * @param attModel Attenuation model to use.
	 * @param distOrRoll Either the fading distance or rolloff factor, depending on the value of "attmodel".
	 */
	@Override
	public void rawDataStream(PAudioFormat audioFormat, boolean priority, String sourcename, float x, float y, float z, int attModel, float distOrRoll) {
		sourceMap.put(sourcename, new SourceAudioTrack(listener, audioFormat, priority, sourcename, x, y, z, attModel, distOrRoll));
	}

	/**
	 * Creates and immediately plays a new source.
	 * @param priority Setting this to true will prevent other sounds from overriding this one.
	 * @param toStream Setting this to true will load the sound in pieces rather than all at once.
	 * @param toLoop Should this source loop, or play only once.
	 * @param sourcename A unique identifier for this source.  Two sources may not use the same sourcename.
	 * @param filenameURL Filename/URL of the sound file to play at this source.
	 * @param x X position for this source.
	 * @param y Y position for this source.
	 * @param z Z position for this source.
	 * @param attModel Attenuation model to use.
	 * @param distOrRoll Either the fading distance or rolloff factor, depending on the value of "attmodel".
	 * @param temporary Whether or not this source should be removed after it finishes playing.
	 */
	@Override
	public void quickPlay(boolean priority, boolean toStream, boolean toLoop, String sourcename, FilenameURL filenameURL, float x, float y, float z, int attModel, float distOrRoll, boolean temporary) {
		SoundBuffer buffer = null;

		if(!toStream) {
			// Grab the audio data for this file:
			buffer = bufferMap.get(filenameURL.getFilename());
			// if not found, try loading it:
			if(buffer == null) {
				if(!loadSound(filenameURL)) {
					errorMessage("Source '" + sourcename + "' was not created " + "because an error occurred while loading " + filenameURL.getFilename());
					return;
				}
			}
			// try and grab the sound buffer again:
			buffer = bufferMap.get(filenameURL.getFilename());
			// see if it was there this time:
			if(buffer == null) {
				errorMessage("Source '" + sourcename + "' was not created " + "because audio data was not found for " + filenameURL.getFilename());
				return;
			}
		}

		if(!toStream && buffer != null)
			buffer.trimData(maxClipSize);

		sourceMap.put(sourcename, new SourceAudioTrack(listener, priority, toStream, toLoop, sourcename, filenameURL, buffer, x, y, z, attModel, distOrRoll, temporary));
	}

	/**
	 * Creates sources based on the source map provided.
	 * @param srcMap Sources to copy.
	 */
	@Override
	public void copySources(HashMap<String, Source> srcMap) {
		if(srcMap == null)
			return;
		Set<String> keys = srcMap.keySet();
		Iterator<String> iter = keys.iterator();
		String sourcename;
		Source source;

		// Make sure the buffer map exists:
		if(bufferMap == null) {
			bufferMap = new HashMap<String, SoundBuffer>();
			importantMessage("Buffer Map was null in method 'copySources'");
		}

		// remove any existing sources before starting:
		sourceMap.clear();

		SoundBuffer buffer;
		// loop through and copy all the sources:
		while(iter.hasNext()) {
			sourcename = iter.next();
			source = srcMap.get(sourcename);
			if(source != null) {
				buffer = null;
				if(!source.toStream) {
					loadSound(source.filenameURL);
					buffer = bufferMap.get(source.filenameURL.getFilename());
				}
				if(!source.toStream && buffer != null) {
					buffer.trimData(maxClipSize);
				}
				if(source.toStream || buffer != null) {
					sourceMap.put(sourcename, new SourceAudioTrack(listener, source, buffer));
				}
			}
		}
	}

	/**
	 * Sets the listener's velocity, for use in Doppler effect.
	 * @param x Velocity along world x-axis.
	 * @param y Velocity along world y-axis.
	 * @param z Velocity along world z-axis.
	 */
	@Override
	public void setListenerVelocity(float x, float y, float z) {
		super.setListenerVelocity(x, y, z);

		listenerMoved();
	}

	/**
	 * The Doppler parameters have changed.
	 */
	@Override
	public void dopplerChanged() {
		super.dopplerChanged();

		listenerMoved();
	}

	/**
	 * If byte reverse is needed (It seems so on Android) this method should return true.
	 * BUT! this isn't used by SoundSystemConfig! The static 'reversByteOrder' (sic) is used instead.
	 */
	@Override
	public boolean reverseByteOrder() {
		return reversByteOrder();
	}

	/**
	 * If byte reverse is needed (I don't know if needed on Android) this method should return true.
	 * Used by SoundSystemConfig.
	 * @return True if audio data should be reverse-ordered.
	 */
	public static boolean reversByteOrder() {
		return true;
	}

	/**
	 * Returns the short title of this library type.
	 * @return A short title.
	 */
	public static String getTitle() {
		return "AudioTrack";
	}

	/**
	 * Returns a longer description of this library type.
	 * @return A longer description.
	 */
	public static String getDescription() {
		return "The Android AudioTrack Sound API.";
	}

	/**
	 * Returns the name of the class.
	 * @return "Library" + library title.
	 */
	@Override
	public String getClassName() {
		return "LibraryAudioTrack";
	}
}
