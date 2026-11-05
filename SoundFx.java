import javax.sound.sampled.*;

/** Tiny sound-effect helper. Generates short tones in code, so no audio files are needed. */
public class SoundFx {

    private static boolean muted = false;

    static boolean isMuted() { return muted; }
    static void toggleMuted() { muted = !muted; }

    /** Plays a sequence of tones given as {frequencyHz, durationMs, frequencyHz, durationMs, ...}. */
    private static void play(double... notes) {
        if (muted) return;
        Thread t = new Thread(() -> {
            try {
                float rate = 22050f;
                AudioFormat fmt = new AudioFormat(rate, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, 4096);
                line.start();
                for (int n = 0; n + 1 < notes.length; n += 2) {
                    double freq = notes[n];
                    int samples = (int) (rate * notes[n + 1] / 1000.0);
                    byte[] buf = new byte[samples];
                    for (int i = 0; i < samples; i++) {
                        double env = 1.0 - (double) i / samples;   // fade out
                        double wave = freq <= 0 ? 0 : Math.sin(2 * Math.PI * freq * i / rate);
                        buf[i] = (byte) (wave * env * 40);
                    }
                    line.write(buf, 0, buf.length);
                }
                line.drain();
                line.close();
            } catch (Exception e) {
                // No audio device available: ignore, the game still works silently.
            }
        });
        t.setDaemon(true);
        t.start();
    }

    static void click()     { play(660, 40); }
    static void deny()      { play(180, 120); }
    static void heal()      { play(520, 80, 660, 80, 780, 120); }
    static void save()      { play(500, 70, 750, 110); }
    static void coin()      { play(988, 60, 1318, 140); }
    static void levelUp()   { play(523, 100, 659, 100, 784, 100, 1047, 250); }
    static void victory()   { play(392, 100, 523, 100, 659, 160); }
    static void encounter() { play(300, 90, 220, 140); }
    static void flee()      { play(500, 60, 400, 60, 300, 80); }
    static void miss()      { play(700, 40, 500, 60); }
    static void hit()       { play(220, 60, 120, 70); }
    static void crit()      { play(900, 50, 600, 50, 300, 110); }
    static void guard()     { play(330, 80, 440, 100); }
    static void cast()      { play(400, 60, 600, 60, 900, 100); }
    static void hurt()      { play(160, 120, 110, 120); }
    static void defeat()    { play(300, 200, 240, 200, 180, 200, 120, 400); }
}
