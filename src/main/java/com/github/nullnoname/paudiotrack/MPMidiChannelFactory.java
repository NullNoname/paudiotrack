package com.github.nullnoname.paudiotrack;

import paulscode.sound.FilenameURL;
import paulscode.sound.IMidiChannel;
import paulscode.sound.MidiChannelFactory;

/**
 * This factory class creates MPMidiChannel, which uses Android MediaPlayer to play MIDI files.
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
 * @author Paul Lamb (original codes)
 * @author NullNoname (modifications)
 */
public class MPMidiChannelFactory implements MidiChannelFactory {
	/**
	 * Create a new instance of MPMidiChannel which uses Android MediaPlayer.
	 */
	public IMidiChannel createMidiChannel(boolean toLoop, String sourcename, FilenameURL midiFilenameURL) {
		return new MPMidiChannel(toLoop, sourcename, midiFilenameURL);
	}
}
