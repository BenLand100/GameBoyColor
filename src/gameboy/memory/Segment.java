package gameboy.memory;

/**
 *
 * @author benland100
 */
public abstract class Segment {

    public int readB(int pos) {
        throw new RuntimeException("SegFault: Read on " + Integer.toHexString(pos));
    }

    public void writeB(int pos, int val) {
        throw new RuntimeException("SegFault: Write on " + Integer.toHexString(pos));
    }

}
