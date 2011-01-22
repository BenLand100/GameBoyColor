package gameboy.memory;

/**
 *
 * @author benland100
 */
public class RamPage extends Segment {

    private int mem[];

    public RamPage(int length) {
        mem = new int[length];
    }

    public int[] getBuffer() {
        return mem;
    }

    public int readB(int pos) {
        return mem[pos];
    }

    public void writeB(int pos, int val) {
        mem[pos] = val;
    }

}
