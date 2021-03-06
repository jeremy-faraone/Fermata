package me.aap.fermata.media.engine;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;

import androidx.annotation.Nullable;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class AudioEffects {
	public static final boolean SUPPORTED;
	public static final boolean EQUALIZER_SUPPORTED;
	public static final boolean VIRTUALIZER_SUPPORTED;
	public static final boolean BASS_BOOST_SUPPORTED;
	private final Equalizer equalizer;
	private final Virtualizer virtualizer;
	private final BassBoost bassBoost;

	static {
		boolean supported = false;
		boolean eqSupported = false;
		boolean virtSupported = false;
		boolean bassSupported = false;

		for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
			if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) {
				supported = eqSupported = true;
			} else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) {
				supported = virtSupported = true;
			} else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) {
				supported = bassSupported = true;
			}
		}

		SUPPORTED = supported;
		EQUALIZER_SUPPORTED = eqSupported;
		VIRTUALIZER_SUPPORTED = virtSupported;
		BASS_BOOST_SUPPORTED = bassSupported;
	}

	private AudioEffects(int priority, int audioSessionId) {
		equalizer = EQUALIZER_SUPPORTED ? new Equalizer(priority, audioSessionId) : null;
		virtualizer = VIRTUALIZER_SUPPORTED ? new Virtualizer(priority, audioSessionId) : null;
		bassBoost = BASS_BOOST_SUPPORTED ? new BassBoost(priority, audioSessionId) : null;
	}

	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (!SUPPORTED) return null;

		try {
			return new AudioEffects(priority, audioSessionId);
		} catch (Exception ex) {
			// Sometimes it fails with RuntimeException: AudioEffect: set/get parameter error
			Log.w("Failed to create AudioEffects - retrying...");

			try {
				Thread.sleep(300);
				return new AudioEffects(priority, audioSessionId);
			} catch (Exception ex1) {
				Log.e(ex1, "Failed to create AudioEffects");
				return null;
			}
		}
	}

	@Nullable
	public Equalizer getEqualizer() {
		return equalizer;
	}

	@Nullable
	public Virtualizer getVirtualizer() {
		return virtualizer;
	}

	@Nullable
	public BassBoost getBassBoost() {
		return bassBoost;
	}

	public void release() {
		if (equalizer != null) equalizer.release();
		if (virtualizer != null) virtualizer.release();
		if (bassBoost != null) bassBoost.release();
	}
}
