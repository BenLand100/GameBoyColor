package gameboy.memory;

/**
 *
 * @author benland100
 */
public class RomPage extends Segment {

    private int mem[] = new int[0x4000];

    public RomPage(byte[] data) {
        for (int i = 0; i < 0x4000; i++) {
            mem[i] = (int) (data[i] & 0xFF);
        }
    }

    public int readB(int pos) {
        return mem[pos];
    }

}
