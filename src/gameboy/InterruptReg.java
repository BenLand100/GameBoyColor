package gameboy;

import gameboy.memory.Segment;

/**
 *
 * @author benland100
 */
public class InterruptReg extends Segment {

    public static final int V_BLANK = 0x01;
    public static final int LCD_STAT = 0x02;
    public static final int TIMER = 0x4;
    public static final int SERIAL = 0x8;
    public static final int JOYPAD = 0x10;

    int reg;

    public InterruptReg() {
        reg = 0;
    }

    public boolean test(int mask) {
        return 0 != (reg & mask);
    }

    public void set(int mask) {
        reg |= mask;
    }

    public void reset(int mask) {
        reg &= ~mask;
    }

    public int readB(int pos) {
        return reg;
    }

    public void writeB(int pos, int val) {
        reg = val;
    }

}
