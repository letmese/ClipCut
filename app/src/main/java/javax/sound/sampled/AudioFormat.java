package javax.sound.sampled;

import java.util.Collections;
import java.util.Map;

/**
 * Android compatibility stub for javax.sound.sampled.AudioFormat.
 * Satisfies Jump3r LAME MP3 encoder dependencies on Android runtime.
 */
public class AudioFormat {
    public static class Encoding {
        public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");
        public static final Encoding PCM_UNSIGNED = new Encoding("PCM_UNSIGNED");
        private final String name;
        public Encoding(String name) { this.name = name; }
        @Override
        public String toString() { return name; }
    }

    private final Encoding encoding;
    private final float sampleRate;
    private final int sampleSizeInBits;
    private final int channels;
    private final int frameSize;
    private final float frameRate;
    private final boolean bigEndian;
    private final Map<String, Object> properties;

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize, float frameRate, boolean bigEndian) {
        this(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian, Collections.emptyMap());
    }

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize, float frameRate, boolean bigEndian, Map<String, Object> properties) {
        this.encoding = encoding != null ? encoding : Encoding.PCM_SIGNED;
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
        this.properties = properties != null ? properties : Collections.emptyMap();
    }

    public AudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
        this(signed ? Encoding.PCM_SIGNED : Encoding.PCM_UNSIGNED, sampleRate, sampleSizeInBits, channels, ((sampleSizeInBits + 7) / 8) * channels, sampleRate, bigEndian);
    }

    public Encoding getEncoding() { return encoding; }
    public float getSampleRate() { return sampleRate; }
    public int getSampleSizeInBits() { return sampleSizeInBits; }
    public int getChannels() { return channels; }
    public int getFrameSize() { return frameSize; }
    public float getFrameRate() { return frameRate; }
    public boolean isBigEndian() { return bigEndian; }
    public Map<String, Object> properties() { return properties; }
}
