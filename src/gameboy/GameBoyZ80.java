package gameboy;

public class GameBoyZ80 {

    private volatile int A,  F,  B,  C,  D,  E,  H,  L;
    private volatile int I,  R;
    private volatile int SP,  PC;
    private volatile int cycle = 0;
    private boolean cpuRunning = false,  halt = false,  interrupts = true;
    Cartridge cart = new Cartridge("./test.rom");
    private IOPorts ports = new IOPorts();
    private GameBoyMemory memory = new GameBoyMemory(ports, cart);
    private LCD lcd = new LCD(memory.videoRam(),memory.spriteRam(),ports);

    /**
     * 0x400000 Cycle/Sec single speed
     * 0x800000 Cycle/Sec double speed
     */
    private int cycles_sec = 0x400000;
    private int vblank_period = 70224;//0..153
    private int lcd_ly_period = vblank_period / 154;
    private int timer_modes[] = new int[]{1024, 16, 64, 256};

    public GameBoyZ80() {
        reset();
    }

    private void interrupt(int pos) {
        push(PC);
        interrupts = false;
        PC = pos;
    }

    public void start() {
        cpuRunning = true;
        halt = false;
        reset();
        Class curClass = getClass();
        cycle = cycles_sec;
        int last = cycle;
        int lcd_ly_cycle = lcd_ly_period;
        int timer_cycle = 0;
        long count = 0;
        while (cpuRunning) {
            if (ports.LCD_DMA != 0) {
                int source = 0x100 * ports.LCD_DMA;
                int dest = 0xFE00;
                for (int i = 0; i < 0xA0; i++) {
                    memory.writeB(dest+i,memory.readB(source+i));
                }
                ports.LCD_DMA = 0;
            }
            int elapsed = last - cycle;
            if (cycle <= 0) {
                //System.out.println(System.currentTimeMillis() / 1000);
                cycle += cycles_sec;
                //try { Thread.sleep(500); } catch (Exception e) { }
            }
            if ((ports.TIMER_CTRL & 0x4) != 0) {
                timer_cycle += elapsed;
                if (timer_cycle >= timer_modes[ports.TIMER_CTRL & 0x3]) {
                    if (++ports.TIMER_CNTR > 0xFF) {
                        ports.TIMER_CNTR -= 0xFF;
                        ports.TIMER_CNTR += ports.TIMER_INIT;
                        if (interrupts && checkBit(readB(0xFFFF), 2)) {
                            /*TIMER INTERRUPT*/
                            interrupt(0x50);
                        }
                    }
                }
            }
            lcd_ly_cycle -= elapsed;
            if (lcd_ly_cycle <= 0) {
                lcd_ly_cycle += lcd_ly_period;
                if (ports.LCD_LY < 144) {
                    lcd.renderLine(ports.LCD_LY);
                }
                if (++ports.LCD_LY == 154) {
                    /*VBLANK OVER*/
                    lcd.gatherData();
                    ports.LCD_LY = 0;
                }
                if (interrupts) {
                    if (ports.LCD_LY == ports.LCD_LYC && checkBit(readB(0xFFFF), 1)) {
                        /*STAT INTERUPT*/
                        interrupt(0x48);
                    }
                    if (ports.LCD_LY == 144 && checkBit(readB(0xFFFF), 0)) {
                        /*VBLANK INTERRUPT*/
                        interrupt(0x40);
                    }
                }
            }
            if (halt) {
                R++;
                cycle -= 4;
                continue;
            }
            count++;
            int op = readB(PC++);
            R++;
            last = cycle;
            if (count % 1000 == 0) System.out.println(count);
            //System.out.println(opcodes[op]);
            op(op);

        }
    }

    public boolean checkBit(int value, int bit) {
        return ((1 << bit) & value) != 0;
    }

    public int setBit(int value, int bit) {
        return (1 << bit) | value;
    }

    public int resetBit(int value, int bit) {
        return ~(1 << bit) & value;
    }

    public boolean testBit(int x, int bit) {
        x &= 1 << bit;
        zero(x == 0);
        subtract(false);
        half(true);
        return x != 0;
    }

    public int pop() {
        return ((memory.readB(SP++) & 0xFF) << 8) | (memory.readB(SP++) & 0xFF);
    }

    public void push(int value) {
        memory.writeB(--SP, value & 0xFF);
        memory.writeB(--SP, (value & 0xFF00) >> 8);
    }

    public int sign16(int value) {
        int result = (short) value;
        return result;
    }

    public int sign8(int value) {
        byte result = (byte) value;
        return result;
    }

    public int getParity16(int value) {
        int count = 0;
        for (int i = 0; i < 16; i++) {
            if (checkBit(value, i)) {
                count++;
            }
        }
        return count;
    }

    public int getParity8(int value) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
            if (checkBit(value, i)) {
                count++;
            }
        }
        return count;
    }

    public int complement(int a) {
        int temp = 0xFF & ~a;
        half(true);
        subtract(true);
        return temp;
    }

    public int decimalAdjust(int a) {
        int add = 0;
        int h = (a & 0xF0) >> 4;
        int l = a & 0x0F;
        if (subtract()) {
            if (carry()) {
                if (h >= 0x6 && h <= 0xF && half() && l >= 0x6 && l <= 0xF) {
                    add = 0x9A;
                    carry(true);
                } else if (h >= 0x7 && h <= 0xF && !half() && l >= 0x0 && l <= 0x9) {
                    add = 0xA0;
                    carry(true);
                }
            } else {
                if (h >= 0x0 && h <= 0x8 && half() && l >= 0x6 && l <= 0xF) {
                    add = 0xFA;
                    carry(false);
                } else if (h >= 0x0 && h <= 0x9 && !half() && l >= 0x0 && l <= 0x9) {
                    add = 0x00;
                    carry(false);
                }
            }
        } else {
            if (carry()) {
                if (h >= 0x0 && h <= 0x3 && half() && l >= 0x0 && l <= 0x3) {
                    add = 0x66;
                    carry(true);
                } else if (h >= 0x0 && h <= 0x2 && !half() && l >= 0xA && l <= 0xF) {
                    add = 0x66;
                    carry(true);
                } else if (h >= 0x0 && h <= 0x2 && !half() && l >= 0x0 && l <= 0x9) {
                    add = 0x60;
                    carry(true);
                }
            } else {
                if (h >= 0xA && h <= 0xF && half() && l >= 0x0 && l <= 0x3) {
                    add = 0x66;
                    carry(true);
                } else if (h >= 0x9 && h <= 0xF && !half() && l >= 0xA && l <= 0xF) {
                    add = 0x66;
                    carry(true);
                } else if (h >= 0xA && h <= 0xF && !half() && l >= 0x0 && l <= 0x9) {
                    add = 0x60;
                    carry(true);
                } else if (h >= 0x0 && h <= 0x9 && half() && l >= 0x0 && l <= 0x3) {
                    add = 0x06;
                    carry(false);
                } else if (h >= 0x0 && h <= 0x8 && !half() && l >= 0xA && l <= 0xF) {
                    add = 0x06;
                    carry(false);
                } else if (h >= 0x0 && h <= 0x9 && !half() && l >= 0x0 && l <= 0x9) {
                    add = 0x00;
                    carry(false);
                }
            }
        }
        int temp = (a + add) & 0xFF;
        zero(temp == 0);
        half(checkBit(temp, 3));
        return temp;
    }

    public int add16(int a, int b) {
        int temp = a + b;
        carry(checkBit(temp,16));
        subtract(false);
        half(checkBit(a, 11) && checkBit(b, 11));
        return temp;
    }

    public int add8(int a, int b) {
        int temp = a + b;
        carry(checkBit(temp,8));
        zero(temp == 0);
        subtract(false);
        half(checkBit(a, 3) && checkBit(b, 3));
        return temp;
    }

    public int adc16(int a, int b) {
        int temp = a + b;
        if (carry()) {
            temp++;
        }
        carry(checkBit(temp,16));
        zero(temp == 0);
        subtract(false);
        half(checkBit(a, 11) && checkBit(b, 11));
        return temp;
    }

    public int adc8(int a, int b) {
        int temp = a + b;
        if (carry()) {
            temp++;
        }
        carry(checkBit(temp,8));
        zero(temp == 0);
        subtract(false);
        half(checkBit(a, 3) && checkBit(b, 3));
        return temp;
    }

    public int sub8(int a, int b) {
        int temp = a - b;
        carry(temp < 0);
        zero(temp == 0);
        subtract(true);
        half(!checkBit(a, 3) && checkBit(b, 3));
        return temp;
    }

    public int sub16(int a, int b) {
        int temp = a - b;
        carry(temp < 0);
        zero(temp == 0);
        subtract(true);
        half(!checkBit(a, 3) && checkBit(b, 3));
        return temp;
    }

    public int sbc16(int a, int b) {
        int temp = a - b;
        if (carry()) {
            temp--;
        }
        carry(temp < 0);
        zero(temp == 0);
        subtract(true);
        half(!checkBit(a, 11) && checkBit(b, 11));
        return temp;
    }

    public int sbc8(int a, int b) {
        int temp = a - b;
        if (carry()) {
            temp--;
        }
        carry(temp < 0);
        zero(temp == 0);
        subtract(true);
        half(!checkBit(a, 3) && checkBit(b, 3));
        return temp;
    }

    public int rotateLeftCarry8(int value) {
        carry(checkBit(value, 7));
        value = value << 1;
        value = ((value & (1 << 7)) >> 7) | (value & ~0x1);
        subtract(false);
        half(false);
        zero(false);
        return value;
    }

    public int rotateLeft8(int value) {
        carry(checkBit(value, 7));
        value = value << 1;
        value = ((value & (1 << 8)) >> 8) | (value & ~0x1);
        subtract(false);
        half(false);
        zero(false);
        return value;
    }

    public int shiftLeft8(int value) {
        carry(checkBit(value, 7));
        value = (value << 1) & 0xFF;
        subtract(false);
        half(false);
        zero(0 == value);
        return value;
    }

    public int rotateRightCarry8(int value) {
        carry(checkBit(value, 0));
        int carry = (value & 0x1) << 7;
        value = ((value >> 1) & ~(1 << 7)) | carry;
        value = (value & ~(1 << 6)) | (carry >> 1);
        subtract(false);
        half(false);
        zero(false);
        return value;
    }

    public int rotateRight8(int value) {
        carry(checkBit(value, 0));
        int carry = (value & 0x1) << 7;
        value = ((value >> 1) & ~(1 << 7)) | carry;
        subtract(false);
        half(false);
        zero(false);
        return value;
    }

    public int shiftRightA8(int value) {
        carry(checkBit(value, 0));
        value = ((value >> 1) & ~(1 << 7)) | (value & (1 << 7));
        subtract(false);
        half(false);
        zero(0 == value);
        return value;
    }

    public int shiftRightL8(int value) {
        carry(checkBit(value, 0));
        value = ((value >> 1) & ~(1 << 7));
        subtract(false);
        half(false);
        zero(0 == value);
        return value;
    }

    public int and8(int a, int b) {
        int x = (a & b) & 0xFF;
        zero(x == 0);
        half(true);
        carry(false);
        subtract(false);
        return x;
    }

    public int or8(int a, int b) {
        int x = (a | b) & 0xFF;
        zero(x == 0);
        half(false);
        carry(false);
        subtract(false);
        return x;
    }

    public int xor8(int a, int b) {
        int x = (a ^ b) & 0xFF;
        zero(x == 0);
        half(false);
        carry(false);
        subtract(false);
        return x;
    }

    private void error(String error) {
        throw new RuntimeException(error);
    }

    public void reset() {
        AF(0x01B0);
        BC(0x0013);
        DE(0x00D8);
        HL(0x014D);
        SP(0xFFFE);
        I = R = 0;
        PC = 0x0100;
        interrupts = true;
    }

    protected synchronized int read(int pos) {
        return memory.readB(pos & 0xFFFF) & 0xFF;
    }

    protected synchronized void write(int pos, int value) {
        memory.writeB(pos & 0xFFFF, value & 0xFF);
    }

    public void writeB(int pos, int value) {
        write(pos, value);
    }

    public void writeW(int pos, int value) {
        write(pos, value);
        write(pos + 1, value >> 8);
    }

    public int readB(int pos) {
        return read(pos);
    }

    public int readW(int pos) {
        return read(pos) | (read(pos + 1) << 8);
    }

    protected void op(int op) {
         switch (op) {
            case 0x00: // NOP
                cycle -= 4;
                break;
            case 0x01: // LD BC, nn
                BC(readW(PC++));
                PC++;
                cycle -= 12;
                break;
            case 0x02: // LD (BC), A
                writeB(BC(), A());
                cycle -= 8;
                break;
            case 0x03: // INC BC
                BC(add16(BC(), 1));
                cycle -= 8;
                break;
            case 0x04: // INC B
                B(add8(B(), 1));
                cycle -= 4;
                break;
            case 0x05: // DEC B
                B(sub8(B(), 1));
                cycle -= 4;
                break;
            case 0x06: // LD B, n
                B(readB(PC++));
                cycle -= 8;
                break;
            case 0x07: // RLCA
                A(rotateLeftCarry8(A()));
                cycle -= 4;
                break;
            case 0x08: // LD (nn), SP
                writeW(PC++, SP());
                PC++;
                cycle -= 20;
                break;
            case 0x09: // ADD HL, BC
                HL(add16(HL(), BC()));
                cycle -= 8;
                break;
            case 0x0A: // LD A, (BC)
                A(readB(BC()));
                cycle -= 8;
                break;
            case 0x0B: // DEC BC
                BC(sub16(BC(), 1));
                cycle -= 8;
                break;
            case 0x0C: // INC C
                C(add8(C(), 1));
                cycle -= 4;
                break;
            case 0x0D: // DEC C
                C(sub8(C(), 1));
                cycle -= 4;
                break;
            case 0x0E: // LD C, n
                C(readB(PC++));
                cycle -= 8;
                break;
            case 0x0F: // RRCA
                A(rotateRightCarry8(A()));
                cycle -= 4;
                break;
            case 0x10: // STOP
                cycle -= 4;
                break;
            case 0x11: // LD DE, nn
                DE(readW(PC++));
                PC++;
                cycle -= 12;
                break;
            case 0x12: // LD (DE), A
                writeB(DE(), A());
                cycle -= 8;
                break;
            case 0x13: // INC DE
                DE(add16(DE(), 1));
                cycle -= 8;
                break;
            case 0x14: // INC D
                D(add8(D(), 1));
                cycle -= 4;
                break;
            case 0x15: // DEC D
                D(sub8(D(), 1));
                cycle -= 4;
                break;
            case 0x16: // LD D, n
                D(readB(PC++));
                cycle -= 8;
                break;
            case 0x17: // RLA
                A(rotateLeft8(A()));
                cycle -= 4;
                break;
            case 0x18: // JR n
                PC += sign8(readB(PC)) + 1;
                cycle -= 16;
                break;
            case 0x19: // ADD HL, DE
                HL(add16(HL(), DE()));
                cycle -= 8;
                break;
            case 0x1A: // LD A, (DE)
                A(readB(DE()));
                cycle -= 8;
                break;
            case 0x1B: // DEC DE
                DE(sub16(DE(), 1));
                cycle -= 8;
                break;
            case 0x1C: // INC E
                E(add8(E(), 1));
                cycle -= 4;
                break;
            case 0x1D: // DEC E
                E(sub8(E(), 1));
                cycle -= 4;
                break;
            case 0x1E: // LD E, n
                E(readB(PC++));
                cycle -= 8;
                break;
            case 0x1F: // RRA
                A(rotateRight8(A()));
                cycle -= 4;
                break;
            case 0x20: // JR NZ, n
                if (!zero()) {
                    PC += sign8(readB(PC)) + 1;
                    cycle -= 12;
                } else {
                    PC++;
                    cycle -= 8;
                }
                break;
            case 0x21: // LD HL, nn
                HL(readW(PC++));
                PC++;
                cycle -= 12;
                break;
            case 0x22: // LDI (HL), A
                writeB(HL(), A());
                HL(add16(HL(), 1));
                cycle -= 8;
                break;
            case 0x23: // INC HL
                HL(add16(HL(), 1));
                cycle -= 8;
                break;
            case 0x24: // INC H
                H(add8(H(), 1));
                cycle -= 4;
                break;
            case 0x25: // DEC H
                H(sub8(H(), 1));
                cycle -= 4;
                break;
            case 0x26: // LD H, n
                H(readB(PC++));
                cycle -= 8;
                break;
            case 0x27: // DAA
                A(decimalAdjust(A()));
                cycle -= 4;
                break;
            case 0x28: // JR Z, n
                if (zero()) {
                    PC += sign8(readB(PC)) + 1;
                    cycle -= 12;
                } else {
                    PC++;
                    cycle -= 8;
                }
                break;
            case 0x29: // ADD HL, HL
                HL(add16(HL(), HL()));
                cycle -= 8;
                break;
            case 0x2A: // LDI A, (HL)
                A(readB(HL()));
                HL(add16(HL(), 1));
                cycle -= 8;
                break;
            case 0x2B: // DEC HL
                HL(sub16(HL(), 1));
                cycle -= 8;
                break;
            case 0x2C: // INC L
                L(add8(L(), 1));
                cycle -= 4;
                break;
            case 0x2D: // DEC L
                L(sub8(L(), 1));
                cycle -= 4;
                break;
            case 0x2E: // LD L, n
                L(readB(PC++));
                cycle -= 8;
                break;
            case 0x2F: // CPL
                A(complement(A()));
                cycle -= 4;
                break;
            case 0x30: // JR NC, n
                if (!carry()) {
                    PC += sign8(readB(PC)) + 1;
                    cycle -= 12;
                } else {
                    PC++;
                    cycle -= 8;
                }
                break;
            case 0x31: // LD SP, nn
                SP(readW(PC++));
                PC++;
                cycle -= 12;
                break;
            case 0x32: // LDD (HL), A
                writeB(HL(), A());
                HL(sub16(HL(), 1));
                cycle -= 8;
                break;
            case 0x33: // INC SP
                SP(add16(SP(), 1));
                cycle -= 8;
                break;
            case 0x34: // INC (HL)
                writeB(HL(), add8(readB(HL()), 1));
                cycle -= 12;
                break;
            case 0x35: // DEC (HL)
                writeB(HL(), sub8(readB(HL()), 1));
                cycle -= 12;
                break;
            case 0x36: // LD (HL), n
                writeB(HL(), readB(PC++));
                cycle -= 12;
                break;
            case 0x37: // SCF
                carry(true);
                half(false);
                subtract(false);
                cycle -= 4;
                break;
            case 0x38: // JR C, n
                if (carry()) {
                    PC += sign8(readB(PC)) + 1;
                    cycle -= 12;
                } else {
                    PC++;
                    cycle -= 8;
                }
                break;
            case 0x39: // ADD HL, SP
                HL(add16(HL(), SP()));
                cycle -= 8;
                break;
            case 0x3A: // LDD A, (HL)
                A(readB(HL()));
                HL(sub16(HL(), 1));
                cycle -= 8;
                break;
            case 0x3B: // DEC SP
                SP(sub16(SP(), 1));
                cycle -= 8;
                break;
            case 0x3C: // INC A
                A(add8(A(), 1));
                cycle -= 4;
                break;
            case 0x3D: // DEC A
                A(sub8(A(), 1));
                cycle -= 4;
                break;
            case 0x3E: // LD A, n
                A(readB(PC++));
                cycle -= 8;
                break;
            case 0x3F: // CCF
                carry(!carry());
                subtract(false);
                half(false);
                cycle -= 4;
                break;
            case 0x40: // LD B, B
                B(B());
                cycle -= 4;
                break;
            case 0x41: // LD B, C
                B(C());
                cycle -= 4;
                break;
            case 0x42: // LD B, D
                B(D());
                cycle -= 4;
                break;
            case 0x43: // LD B, E
                B(E());
                cycle -= 4;
                break;
            case 0x44: // LD B, H
                B(H());
                cycle -= 4;
                break;
            case 0x45: // LD B, L
                B(L());
                cycle -= 4;
                break;
            case 0x46: // LD B, (HL)
                B(readB(HL()));
                cycle -= 8;
                break;
            case 0x47: // LD B, A
                B(A());
                cycle -= 4;
                break;
            case 0x48: // LD C, B
                C(B());
                cycle -= 4;
                break;
            case 0x49: // LD C, C
                C(C());
                cycle -= 4;
                break;
            case 0x4A: // LD C, D
                C(D());
                cycle -= 4;
                break;
            case 0x4B: // LD C, E
                C(E());
                cycle -= 4;
                break;
            case 0x4C: // LD C, H
                C(H());
                cycle -= 4;
                break;
            case 0x4D: // LD C, L
                C(L());
                cycle -= 4;
                break;
            case 0x4E: // LD C, (HL)
                C(readB(HL()));
                cycle -= 8;
                break;
            case 0x4F: // LD C, A
                C(A());
                cycle -= 4;
                break;
            case 0x50: // LD D, B
                D(B());
                cycle -= 4;
                break;
            case 0x51: // LD D, C
                D(C());
                cycle -= 4;
                break;
            case 0x52: // LD D, D
                D(D());
                cycle -= 4;
                break;
            case 0x53: // LD D, E
                D(E());
                cycle -= 4;
                break;
            case 0x54: // LD D, H
                D(H());
                cycle -= 4;
                break;
            case 0x55: // LD D, L
                D(L());
                cycle -= 4;
                break;
            case 0x56: // LD D, (HL)
                D(readB(HL()));
                cycle -= 8;
                break;
            case 0x57: // LD D, A
                D(A());
                cycle -= 4;
                break;
            case 0x58: // LD E, B
                E(B());
                cycle -= 4;
                break;
            case 0x59: // LD E, C
                E(C());
                cycle -= 4;
                break;
            case 0x5A: // LD E, D
                E(D());
                cycle -= 4;
                break;
            case 0x5B: // LD E, E
                E(E());
                cycle -= 4;
                break;
            case 0x5C: // LD E, H
                E(H());
                cycle -= 4;
                break;
            case 0x5D: // LD E, L
                E(L());
                cycle -= 4;
                break;
            case 0x5E: // LD E, (HL)
                E(readB(HL()));
                cycle -= 8;
                break;
            case 0x5F: // LD E, A
                E(A());
                cycle -= 4;
                break;
            case 0x60: // LD H, B
                H(B());
                cycle -= 4;
                break;
            case 0x61: // LD H, C
                H(C());
                cycle -= 4;
                break;
            case 0x62: // LD H, D
                H(D());
                cycle -= 4;
                break;
            case 0x63: // LD H, E
                H(E());
                cycle -= 4;
                break;
            case 0x64: // LD H, H
                H(H());
                cycle -= 4;
                break;
            case 0x65: // LD H, L
                H(L());
                cycle -= 4;
                break;
            case 0x66: // LD H, (HL)
                H(readB(HL()));
                cycle -= 8;
                break;
            case 0x67: // LD H, A
                H(A());
                cycle -= 4;
                break;
            case 0x68: // LD L, B
                L(B());
                cycle -= 4;
                break;
            case 0x69: // LD L, C
                L(C());
                cycle -= 4;
                break;
            case 0x6A: // LD L, D
                L(D());
                cycle -= 4;
                break;
            case 0x6B: // LD L, E
                L(E());
                cycle -= 4;
                break;
            case 0x6C: // LD L, H
                L(H());
                cycle -= 4;
                break;
            case 0x6D: // LD L, L
                L(L());
                cycle -= 4;
                break;
            case 0x6E: // LD L, (HL)
                L(readB(HL()));
                cycle -= 8;
                break;
            case 0x6F: // LD L, A
                L(A());
                cycle -= 4;
                break;
            case 0x70: // LD (HL), B
                writeB(HL(), B());
                cycle -= 8;
                break;
            case 0x71: // LD (HL), C
                writeB(HL(), C());
                cycle -= 8;
                break;
            case 0x72: // LD (HL), D
                writeB(HL(), D());
                cycle -= 8;
                break;
            case 0x73: // LD (HL), E
                writeB(HL(), E());
                cycle -= 8;
                break;
            case 0x74: // LD (HL), H
                writeB(HL(), H());
                cycle -= 8;
                break;
            case 0x75: // LD (HL), L
                writeB(HL(), L());
                cycle -= 8;
                break;
            case 0x76: // HALT
                halt = true;
                cycle -= 4;
                break;
            case 0x77: // LD (HL), A
                writeB(HL(), A());
                cycle -= 8;
                break;
            case 0x78: // LD A, B
                A(B());
                cycle -= 4;
                break;
            case 0x79: // LD A, C
                A(C());
                cycle -= 4;
                break;
            case 0x7A: // LD A, D
                A(D());
                cycle -= 4;
                break;
            case 0x7B: // LD A, E
                A(E());
                cycle -= 4;
                break;
            case 0x7C: // LD A, H
                A(H());
                cycle -= 4;
                break;
            case 0x7D: // LD A, L
                A(L());
                cycle -= 4;
                break;
            case 0x7E: // LD A, (HL)
                A(readB(HL()));
                cycle -= 8;
                break;
            case 0x7F: // LD A, A
                A(A());
                cycle -= 4;
                break;
            case 0x80: // ADD A, B
                A(add8(A(), B()));
                cycle -= 4;
                break;
            case 0x81: // ADD A, C
                A(add8(A(), C()));
                cycle -= 4;
                break;
            case 0x82: // ADD A, D
                A(add8(A(), D()));
                cycle -= 4;
                break;
            case 0x83: // ADD A, E
                A(add8(A(), E()));
                cycle -= 4;
                break;
            case 0x84: // ADD A, H
                A(add8(A(), H()));
                cycle -= 4;
                break;
            case 0x85: // ADD A, L
                A(add8(A(), L()));
                cycle -= 4;
                break;
            case 0x86: // ADD A, (HL)
                A(add8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0x87: // ADD A, A
                A(add8(A(), A()));
                cycle -= 4;
                break;
            case 0x88: // ADC A, B
                A(adc8(A(), B()));
                cycle -= 4;
                break;
            case 0x89: // ADC A, C
                A(adc8(A(), C()));
                cycle -= 4;
                break;
            case 0x8A: // ADC A, D
                A(adc8(A(), D()));
                cycle -= 4;
                break;
            case 0x8B: // ADC A, E
                A(adc8(A(), E()));
                cycle -= 4;
                break;
            case 0x8C: // ADC A, H
                A(adc8(A(), H()));
                cycle -= 4;
                break;
            case 0x8D: // ADC A, L
                A(adc8(A(), L()));
                cycle -= 4;
                break;
            case 0x8E: // ADC A, (HL)
                A(adc8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0x8F: // ADC A, A
                A(adc8(A(), A()));
                cycle -= 4;
                break;
            case 0x90: // SUB B
                A(sub8(A(), B()));
                cycle -= 4;
                break;
            case 0x91: // SUB C
                A(sub8(A(), C()));
                cycle -= 4;
                break;
            case 0x92: // SUB D
                A(sub8(A(), D()));
                cycle -= 4;
                break;
            case 0x93: // SUB E
                A(sub8(A(), E()));
                cycle -= 4;
                break;
            case 0x94: // SUB H
                A(sub8(A(), H()));
                cycle -= 4;
                break;
            case 0x95: // SUB L
                A(sub8(A(), L()));
                cycle -= 4;
                break;
            case 0x96: // SUB (HL)
                A(sub8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0x97: // SUB A
                A(sub8(A(), A()));
                cycle -= 4;
                break;
            case 0x98: // SBC A, B
                A(sbc8(A(), B()));
                cycle -= 4;
                break;
            case 0x99: // SBC A, C
                A(sbc8(A(), C()));
                cycle -= 4;
                break;
            case 0x9A: // SBC A, D
                A(sbc8(A(), D()));
                cycle -= 4;
                break;
            case 0x9B: // SBC A, E
                A(sbc8(A(), E()));
                cycle -= 4;
                break;
            case 0x9C: // SBC A, H
                A(sbc8(A(), H()));
                cycle -= 4;
                break;
            case 0x9D: // SBC A, L
                A(sbc8(A(), L()));
                cycle -= 4;
                break;
            case 0x9E: // SBC A, (HL)
                A(sbc8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0x9F: // SBC A, A
                A(sbc8(A(), A()));
                cycle -= 4;
                break;
            case 0xA0: // AND B
                A(and8(A(), B()));
                cycle -= 4;
                break;
            case 0xA1: // AND C
                A(and8(A(), C()));
                cycle -= 4;
                break;
            case 0xA2: // AND D
                A(and8(A(), D()));
                cycle -= 4;
                break;
            case 0xA3: // AND E
                A(and8(A(), E()));
                cycle -= 4;
                break;
            case 0xA4: // AND H
                A(and8(A(), H()));
                cycle -= 4;
                break;
            case 0xA5: // AND L
                A(and8(A(), L()));
                cycle -= 4;
                break;
            case 0xA6: // AND (HL)
                A(and8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0xA7: // AND A
                A(and8(A(), A()));
                cycle -= 4;
                break;
            case 0xA8: // XOR B
                A(xor8(A(), B()));
                cycle -= 4;
                break;
            case 0xA9: // XOR C
                A(xor8(A(), C()));
                cycle -= 4;
                break;
            case 0xAA: // XOR D
                A(xor8(A(), D()));
                cycle -= 4;
                break;
            case 0xAB: // XOR E
                A(xor8(A(), E()));
                cycle -= 4;
                break;
            case 0xAC: // XOR H
                A(xor8(A(), H()));
                cycle -= 4;
                break;
            case 0xAD: // XOR L
                A(xor8(A(), L()));
                cycle -= 4;
                break;
            case 0xAE: // XOR (HL)
                A(xor8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0xAF: // XOR A
                A(xor8(A(), A()));
                cycle -= 4;
                break;
            case 0xB0: // OR B
                A(or8(A(), B()));
                cycle -= 4;
                break;
            case 0xB1: // OR C
                A(or8(A(), C()));
                cycle -= 4;
                break;
            case 0xB2: // OR D
                A(or8(A(), D()));
                cycle -= 4;
                break;
            case 0xB3: // OR E
                A(or8(A(), E()));
                cycle -= 4;
                break;
            case 0xB4: // OR H
                A(or8(A(), H()));
                cycle -= 4;
                break;
            case 0xB5: // OR L
                A(or8(A(), L()));
                cycle -= 4;
                break;
            case 0xB6: // OR (HL)
                A(or8(A(), readB(HL())));
                cycle -= 8;
                break;
            case 0xB7: // OR A
                A(or8(A(), A()));
                cycle -= 4;
                break;
            case 0xB8: // CP B
                sub8(A(), B());
                cycle -= 4;
                break;
            case 0xB9: // CP C
                sub8(A(), C());
                cycle -= 4;
                break;
            case 0xBA: // CP D
                sub8(A(), D());
                cycle -= 4;
                break;
            case 0xBB: // CP E
                sub8(A(), E());
                cycle -= 4;
                break;
            case 0xBC: // CP H
                sub8(A(), H());
                cycle -= 4;
                break;
            case 0xBD: // CP L
                sub8(A(), L());
                cycle -= 4;
                break;
            case 0xBE: // CP (HL)
                sub8(A(), readB(HL()));
                cycle -= 8;
                break;
            case 0xBF: // CP A
                sub8(A(), A());
                cycle -= 4;
                break;
            case 0xC0: // RET NZ
                if (!zero()) {
                    PC(pop());
                    cycle -= 20;
                } else {
                    cycle -= 8;
                }
                break;
            case 0xC1: // POP BC
                BC(pop());
                cycle -= 12;
                break;
            case 0xC2: // JP NZ, nn
                if (!zero()) {
                    PC(readW(PC));
                    cycle -= 16;
                } else {
                    PC++;
                    PC++;
                    cycle -= 12;
                }
                break;
            case 0xC3: // JP nn
                PC = readW(PC);
                cycle -= 10;
                break;
            case 0xC4: // CALL NZ, nn
                if (!zero()) {
                    push(PC+2);
                    PC(readW(PC));
                    cycle -= 24;
                } else {
                    cycle -= 12;
                    PC++;
                    PC++;
                }
                break;
            case 0xC5: // PUSH BC
                push(BC());
                cycle -= 12;
                break;
            case 0xC6: // ADD A, n
                A(add8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xC7: // RST 00h
                push(PC+1);
                PC(0x00);
                cycle -= 16;
                break;
            case 0xC8: // RET Z
                if (zero()) {
                    PC(pop());
                    cycle -= 20;
                } else {
                    cycle -= 8;
                }
                break;
            case 0xC9: // RET
                PC(pop());
                cycle -= 16;
                break;
            case 0xCA: // JP Z, nn
                if (zero()) {
                    PC(readW(PC));
                    cycle -= 16;
                } else {
                    PC++;
                    PC++;
                    cycle -= 12;
                }
                break;
            case 0xCB:
                switch (readB(PC++)) {
                    case 0x00: //RLC  B
                        B(rotateLeftCarry8(B()));
                        cycle -= 8;
                        break;
                    case 0x01:  //RLC  C
                        C(rotateLeftCarry8(C()));
                        cycle -= 8;
                        break;
                    case 0x02:  //RLC  D
                        D(rotateLeftCarry8(D()));
                        cycle -= 8;
                        break;
                    case 0x03:  //RLC  E
                        E(rotateLeftCarry8(E()));
                        cycle -= 8;
                        break;
                    case 0x04:  //RLC  H
                        H(rotateLeftCarry8(H()));
                        cycle -= 8;
                        break;
                    case 0x05:  //RLC  L
                        L(rotateLeftCarry8(L()));
                        cycle -= 8;
                        break;
                    case 0x06:  //RLC  (HL)
                        writeB(HL(), rotateLeftCarry8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x07:  //RLC  A
                        A(rotateLeftCarry8(A()));
                        cycle -= 8;
                        break;
                    case 0x08:  //RRC  B
                        B(rotateRightCarry8(B()));
                        cycle -= 8;
                        break;
                    case 0x09:  //RRC  C
                        C(rotateRightCarry8(C()));
                        cycle -= 8;
                        break;
                    case 0x0A:  //RRC  D
                        D(rotateRightCarry8(D()));
                        cycle -= 8;
                        break;
                    case 0x0B:  //RRC  E
                        E(rotateRightCarry8(E()));
                        cycle -= 8;
                        break;
                    case 0x0C:  //RRC  H
                        H(rotateRightCarry8(H()));
                        cycle -= 8;
                        break;
                    case 0x0D:  //RRC  L
                        L(rotateRightCarry8(L()));
                        cycle -= 8;
                        break;
                    case 0x0E:  //RRC  (HL)
                        writeB(HL(), rotateRightCarry8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x0F:  //RRC  A
                        A(rotateRightCarry8(A()));
                        cycle -= 8;
                        break;
                    case 0x10:  //RL  B
                        B(rotateLeft8(B()));
                        cycle -= 8;
                        break;
                    case 0x11:  //RL  C
                        C(rotateLeft8(C()));
                        cycle -= 8;
                        break;
                    case 0x12:  //RL  D
                        D(rotateLeft8(D()));
                        cycle -= 8;
                        break;
                    case 0x13:  //RL  E
                        E(rotateLeft8(E()));
                        cycle -= 8;
                        break;
                    case 0x14:  //RL  H
                        H(rotateLeft8(H()));
                        cycle -= 8;
                        break;
                    case 0x15:  //RL  L
                        L(rotateLeft8(L()));
                        cycle -= 8;
                        break;
                    case 0x16:  //RL  (HL)
                        writeB(HL(), rotateLeft8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x17:  //RL  A
                        A(rotateLeft8(A()));
                        cycle -= 8;
                        break;
                    case 0x18:  //RR  B
                        B(rotateRight8(B()));
                        cycle -= 8;
                        break;
                    case 0x19:  //RR  C
                        C(rotateRight8(C()));
                        cycle -= 8;
                        break;
                    case 0x1A:  //RR  D
                        D(rotateRight8(D()));
                        cycle -= 8;
                        break;
                    case 0x1B:  //RR  E
                        E(rotateRight8(E()));
                        cycle -= 8;
                        break;
                    case 0x1C:  //RR  H
                        H(rotateRight8(H()));
                        cycle -= 8;
                        break;
                    case 0x1D:  //RR  L
                        L(rotateRight8(L()));
                        cycle -= 8;
                        break;
                    case 0x1E:  //RR  (HL)
                        writeB(HL(), rotateRight8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x1F:  //RR  A
                        A(rotateRight8(A()));
                        cycle -= 8;
                        break;
                    case 0x20:  //SLA  B
                        B(shiftLeft8(B()));
                        cycle -= 8;
                        break;
                    case 0x21:  //SLA  C
                        C(shiftLeft8(C()));
                        cycle -= 8;
                        break;
                    case 0x22:  //SLA  D
                        D(shiftLeft8(D()));
                        cycle -= 8;
                        break;
                    case 0x23:  //SLA  E
                        E(shiftLeft8(E()));
                        cycle -= 8;
                        break;
                    case 0x24:  //SLA  H
                        H(shiftLeft8(H()));
                        cycle -= 8;
                        break;
                    case 0x25:  //SLA  L
                        L(shiftLeft8(L()));
                        cycle -= 8;
                        break;
                    case 0x26:  //SLA  (HL)
                        writeB(HL(), shiftLeft8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x27:  //SLA  A
                        A(shiftLeft8(A()));
                        cycle -= 8;
                        break;
                    case 0x28:  //SRA  B
                        B(shiftRightA8(B()));
                        cycle -= 8;
                        break;
                    case 0x29:  //SRA  C
                        C(shiftRightA8(C()));
                        cycle -= 8;
                        break;
                    case 0x2A:  //SRA  D
                        D(shiftRightA8(D()));
                        cycle -= 8;
                        break;
                    case 0x2B:  //SRA  E
                        E(shiftRightA8(E()));
                        cycle -= 8;
                        break;
                    case 0x2C:  //SRA  H
                        H(shiftRightA8(H()));
                        cycle -= 8;
                        break;
                    case 0x2D:  //SRA  L
                        L(shiftRightA8(L()));
                        cycle -= 8;
                        break;
                    case 0x2E:  //SRA  (HL)
                        writeB(HL(), shiftRightA8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x2F:  //SRA  A
                        A(shiftRightA8(A()));
                        cycle -= 8;
                        break;
                    case 0x30:  //SWAP B
                        B(((B() & 0xF) << 4) | ((B() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x31:  //SWAP C
                        C(((C() & 0xF) << 4) | ((C() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x32:  //SWAP D
                        D(((D() & 0xF) << 4) | ((D() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x33:  //SWAP E
                        E(((E() & 0xF) << 4) | ((E() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x34:  //SWAP H
                        H(((H() & 0xF) << 4) | ((H() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x35:  //SWAP L
                        L(((L() & 0xF) << 4) | ((L() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x36:  //SWAP (HL)
                        int x = readB(HL());
                        x = ((x & 0xF) << 4) | ((x & 0xF0) >> 4);
                        writeB(HL(), x);
                        cycle -= 16;
                        break;
                    case 0x37:  //SWAP A
                        A(((A() & 0xF) << 4) | ((A() & 0xF0) >> 4));
                        cycle -= 8;
                        break;
                    case 0x38:  //SRL  B
                        B(shiftLeft8(B()));
                        cycle -= 8;
                        break;
                    case 0x39:  //SRL  C
                        C(shiftLeft8(C()));
                        cycle -= 8;
                        break;
                    case 0x3A:  //SRL  D
                        D(shiftLeft8(D()));
                        cycle -= 8;
                        break;
                    case 0x3B:  //SRL  E
                        E(shiftLeft8(E()));
                        cycle -= 8;
                        break;
                    case 0x3C:  //SRL  H
                        H(shiftLeft8(H()));
                        cycle -= 8;
                        break;
                    case 0x3D:  //SRL  L
                        L(shiftLeft8(L()));
                        cycle -= 8;
                        break;
                    case 0x3E:  //SRL  (HL)
                        writeB(HL(), shiftLeft8(readB(HL())));
                        cycle -= 16;
                        break;
                    case 0x3F:  //SRL  A
                        A(shiftLeft8(A()));
                        cycle -= 8;
                        break;
                    case 0x40:  //BIT  0,B
                        testBit(B(), 0);
                        cycle -= 8;
                        break;
                    case 0x41:  //BIT  0,C
                        testBit(C(), 0);
                        cycle -= 8;
                        break;
                    case 0x42:  //BIT  0,D
                        testBit(D(), 0);
                        cycle -= 8;
                        break;
                    case 0x43:  //BIT  0,E
                        testBit(E(), 0);
                        cycle -= 8;
                        break;
                    case 0x44:  //BIT  0,H
                        testBit(H(), 0);
                        cycle -= 8;
                        break;
                    case 0x45:  //BIT  0,L
                        testBit(L(), 0);
                        cycle -= 8;
                        break;
                    case 0x46:  //BIT  0,(HL)
                        testBit(HL(), 0);
                        cycle -= 16;
                        break;
                    case 0x47:  //BIT  0,A
                        testBit(A(), 0);
                        cycle -= 8;
                        break;
                    case 0x48:  //BIT  1,B
                        testBit(B(), 1);
                        cycle -= 8;
                        break;
                    case 0x49:  //BIT  1,C
                        testBit(C(), 1);
                        cycle -= 8;
                        break;
                    case 0x4A:  //BIT  1,D
                        testBit(D(), 1);
                        cycle -= 8;
                        break;
                    case 0x4B:  //BIT  1,E
                        testBit(E(), 1);
                        cycle -= 8;
                        break;
                    case 0x4C:  //BIT  1,H
                        testBit(H(), 1);
                        cycle -= 8;
                        break;
                    case 0x4D:  //BIT  1,L
                        testBit(L(), 1);
                        cycle -= 8;
                        break;
                    case 0x4E:  //BIT  1,(HL)
                        testBit(HL(), 1);
                        cycle -= 16;
                        break;
                    case 0x4F:  //BIT  1,A
                        testBit(A(), 1);
                        cycle -= 8;
                        break;
                    case 0x50:  //BIT  2,B
                        testBit(B(), 2);
                        cycle -= 8;
                        break;
                    case 0x51:  //BIT  2,C
                        testBit(C(), 2);
                        cycle -= 8;
                        break;
                    case 0x52:  //BIT  2,D
                        testBit(D(), 2);
                        cycle -= 8;
                        break;
                    case 0x53:  //BIT  2,E
                        testBit(E(), 2);
                        cycle -= 8;
                        break;
                    case 0x54:  //BIT  2,H
                        testBit(H(), 2);
                        cycle -= 8;
                        break;
                    case 0x55:  //BIT  2,L
                        testBit(L(), 2);
                        cycle -= 8;
                        break;
                    case 0x56:  //BIT  2,(HL)
                        testBit(HL(), 2);
                        cycle -= 16;
                        break;
                    case 0x57:  //BIT  2,A
                        testBit(A(), 2);
                        cycle -= 8;
                        break;
                    case 0x58:  //BIT  3,B
                        testBit(B(), 3);
                        cycle -= 8;
                        break;
                    case 0x59:  //BIT  3,C
                        testBit(C(), 3);
                        cycle -= 8;
                        break;
                    case 0x5A:  //BIT  3,D
                        testBit(D(), 3);
                        cycle -= 8;
                        break;
                    case 0x5B:  //BIT  3,E
                        testBit(E(), 3);
                        cycle -= 8;
                        break;
                    case 0x5C:  //BIT  3,H
                        testBit(H(), 3);
                        cycle -= 8;
                        break;
                    case 0x5D:  //BIT  3,L
                        testBit(L(), 3);
                        cycle -= 8;
                        break;
                    case 0x5E:  //BIT  3,(HL)
                        testBit(HL(), 3);
                        cycle -= 16;
                        break;
                    case 0x5F:  //BIT  3,A
                        testBit(A(), 3);
                        cycle -= 8;
                        break;
                    case 0x60:  //BIT  4,B
                        testBit(B(), 4);
                        cycle -= 8;
                        break;
                    case 0x61:  //BIT  4,C
                        testBit(C(), 4);
                        cycle -= 8;
                        break;
                    case 0x62:  //BIT  4,D
                        testBit(D(), 4);
                        cycle -= 8;
                        break;
                    case 0x63:  //BIT  4,E
                        testBit(E(), 4);
                        cycle -= 8;
                        break;
                    case 0x64:  //BIT  4,H
                        testBit(H(), 4);
                        cycle -= 8;
                        break;
                    case 0x65:  //BIT  4,L
                        testBit(L(), 4);
                        cycle -= 8;
                        break;
                    case 0x66:  //BIT  4,(HL)
                        testBit(HL(), 4);
                        cycle -= 16;
                        break;
                    case 0x67:  //BIT  4,A
                        testBit(A(), 4);
                        cycle -= 8;
                        break;
                    case 0x68:  //BIT  5,B
                        testBit(B(), 5);
                        cycle -= 8;
                        break;
                    case 0x69:  //BIT  5,C
                        testBit(C(), 5);
                        cycle -= 8;
                        break;
                    case 0x6A:  //BIT  5,D
                        testBit(D(), 5);
                        cycle -= 8;
                        break;
                    case 0x6B:  //BIT  5,E
                        testBit(E(), 5);
                        cycle -= 8;
                        break;
                    case 0x6C:  //BIT  5,H
                        testBit(H(), 5);
                        cycle -= 8;
                        break;
                    case 0x6D:  //BIT  5,L
                        testBit(L(), 5);
                        cycle -= 8;
                        break;
                    case 0x6E:  //BIT  5,(HL)
                        testBit(HL(), 5);
                        cycle -= 16;
                        break;
                    case 0x6F:  //BIT  5,A
                        testBit(A(), 5);
                        cycle -= 8;
                        break;
                    case 0x70:  //BIT  6,B
                        testBit(B(), 6);
                        cycle -= 8;
                        break;
                    case 0x71:  //BIT  6,C
                        testBit(C(), 6);
                        cycle -= 8;
                        break;
                    case 0x72:  //BIT  6,D
                        testBit(D(), 6);
                        cycle -= 8;
                        break;
                    case 0x73:  //BIT  6,E
                        testBit(E(), 6);
                        cycle -= 8;
                        break;
                    case 0x74:  //BIT  6,H
                        testBit(H(), 6);
                        cycle -= 8;
                        break;
                    case 0x75:  //BIT  6,L
                        testBit(L(), 6);
                        cycle -= 8;
                        break;
                    case 0x76:  //BIT  6,(HL)
                        testBit(HL(), 6);
                        cycle -= 16;
                        break;
                    case 0x77:  //BIT  6,A
                        testBit(A(), 6);
                        cycle -= 8;
                        break;
                    case 0x78:  //BIT  7,B
                        testBit(B(), 7);
                        cycle -= 8;
                        break;
                    case 0x79:  //BIT  7,C
                        testBit(C(), 7);
                        cycle -= 8;
                        break;
                    case 0x7A:  //BIT  7,D
                        testBit(D(), 7);
                        cycle -= 8;
                        break;
                    case 0x7B:  //BIT  7,E
                        testBit(E(), 7);
                        cycle -= 8;
                        break;
                    case 0x7C:  //BIT  7,H
                        testBit(H(), 7);
                        cycle -= 8;
                        break;
                    case 0x7D:  //BIT  7,L
                        testBit(L(), 7);
                        cycle -= 8;
                        break;
                    case 0x7E:  //BIT  7,(HL)
                        testBit(HL(), 7);
                        cycle -= 16;
                        break;
                    case 0x7F:  //BIT  7,A
                        testBit(A(), 7);
                        cycle -= 8;
                        break;
                    case 0x80:  //RES  0,B
                        B(resetBit(B(), 0));
                        cycle -= 8;
                        break;
                    case 0x81:  //RES  0,C
                        C(resetBit(C(), 0));
                        cycle -= 8;
                        break;
                    case 0x82:  //RES  0,D
                        D(resetBit(D(), 0));
                        cycle -= 8;
                        break;
                    case 0x83:  //RES  0,E
                        E(resetBit(E(), 0));
                        cycle -= 8;
                        break;
                    case 0x84:  //RES  0,H
                        H(resetBit(H(), 0));
                        cycle -= 8;
                        break;
                    case 0x85:  //RES  0,L
                        L(resetBit(L(), 0));
                        cycle -= 8;
                        break;
                    case 0x86:  //RES  0,(HL)
                        writeB(HL(), resetBit(readB(HL()), 8));
                        cycle -= 16;
                        break;
                    case 0x87:  //RES  0,A
                        A(resetBit(A(), 0));
                        cycle -= 8;
                        break;
                    case 0x88:  //RES  1,B
                        B(resetBit(B(), 1));
                        cycle -= 8;
                        break;
                    case 0x89:  //RES  1,C
                        C(resetBit(C(), 1));
                        cycle -= 8;
                        break;
                    case 0x8A:  //RES  1,D
                        D(resetBit(D(), 1));
                        cycle -= 8;
                        break;
                    case 0x8B:  //RES  1,E
                        E(resetBit(E(), 1));
                        cycle -= 8;
                        break;
                    case 0x8C:  //RES  1,H
                        H(resetBit(H(), 1));
                        cycle -= 8;
                        break;
                    case 0x8D:  //RES  1,L
                        L(resetBit(L(), 1));
                        cycle -= 8;
                        break;
                    case 0x8E:  //RES  1,(HL)
                        writeB(HL(), resetBit(readB(HL()), 1));
                        cycle -= 16;
                        break;
                    case 0x8F:  //RES  1,A
                        A(resetBit(A(), 1));
                        cycle -= 8;
                        break;
                    case 0x90:  //RES  2,B
                        B(resetBit(B(), 2));
                        cycle -= 8;
                        break;
                    case 0x91:  //RES  2,C
                        C(resetBit(C(), 2));
                        cycle -= 8;
                        break;
                    case 0x92:  //RES  2,D
                        D(resetBit(D(), 2));
                        cycle -= 8;
                        break;
                    case 0x93:  //RES  2,E
                        E(resetBit(E(), 2));
                        cycle -= 8;
                        break;
                    case 0x94:  //RES  2,H
                        H(resetBit(H(), 2));
                        cycle -= 8;
                        break;
                    case 0x95:  //RES  2,L
                        L(resetBit(L(), 2));
                        cycle -= 8;
                        break;
                    case 0x96:  //RES  2,(HL)
                        writeB(HL(), resetBit(readB(HL()), 2));
                        cycle -= 16;
                        break;
                    case 0x97:  //RES  2,A
                        A(resetBit(A(), 2));
                        cycle -= 8;
                        break;
                    case 0x98:  //RES  3,B
                        B(resetBit(B(), 3));
                        cycle -= 8;
                        break;
                    case 0x99:  //RES  3,C
                        C(resetBit(C(), 3));
                        cycle -= 8;
                        break;
                    case 0x9A:  //RES  3,D
                        D(resetBit(D(), 3));
                        cycle -= 8;
                        break;
                    case 0x9B:  //RES  3,E
                        E(resetBit(E(), 3));
                        cycle -= 8;
                        break;
                    case 0x9C:  //RES  3,H
                        H(resetBit(H(), 3));
                        cycle -= 8;
                        break;
                    case 0x9D:  //RES  3,L
                        L(resetBit(L(), 3));
                        cycle -= 8;
                        break;
                    case 0x9E:  //RES  3,(HL)
                        writeB(HL(), resetBit(readB(HL()), 3));
                        cycle -= 16;
                        break;
                    case 0x9F:  //RES  3,A
                        A(resetBit(A(), 3));
                        cycle -= 8;
                        break;
                    case 0xA0:  //RES  4,B
                        B(resetBit(B(), 4));
                        cycle -= 8;
                        break;
                    case 0xA1:  //RES  4,C
                        C(resetBit(C(), 4));
                        cycle -= 8;
                        break;
                    case 0xA2:  //RES  4,D
                        D(resetBit(D(), 4));
                        cycle -= 8;
                        break;
                    case 0xA3:  //RES  4,E
                        E(resetBit(E(), 4));
                        cycle -= 8;
                        break;
                    case 0xA4:  //RES  4,H
                        H(resetBit(H(), 4));
                        cycle -= 8;
                        break;
                    case 0xA5:  //RES  4,L
                        L(resetBit(L(), 4));
                        cycle -= 8;
                        break;
                    case 0xA6:  //RES  4,(HL)
                        writeB(HL(), resetBit(readB(HL()), 4));
                        cycle -= 16;
                        break;
                    case 0xA7:  //RES  4,A
                        A(resetBit(A(), 4));
                        cycle -= 8;
                        break;
                    case 0xA8:  //RES  5,B
                        B(resetBit(B(), 5));
                        cycle -= 8;
                        break;
                    case 0xA9:  //RES  5,C
                        C(resetBit(C(), 5));
                        cycle -= 8;
                        break;
                    case 0xAA:  //RES  5,D
                        D(resetBit(D(), 5));
                        cycle -= 8;
                        break;
                    case 0xAB:  //RES  5,E
                        E(resetBit(E(), 5));
                        cycle -= 8;
                        break;
                    case 0xAC:  //RES  5,H
                        H(resetBit(H(), 5));
                        cycle -= 8;
                        break;
                    case 0xAD:  //RES  5,L
                        L(resetBit(L(), 5));
                        cycle -= 8;
                        break;
                    case 0xAE:  //RES  5,(HL)
                        writeB(HL(), resetBit(readB(HL()), 5));
                        cycle -= 16;
                        break;
                    case 0xAF:  //RES  5,A
                        A(resetBit(A(), 5));
                        cycle -= 8;
                        break;
                    case 0xB0:  //RES  6,B
                        B(resetBit(B(), 6));
                        cycle -= 8;
                        break;
                    case 0xB1:  //RES  6,C
                        C(resetBit(C(), 6));
                        cycle -= 8;
                        break;
                    case 0xB2:  //RES  6,D
                        D(resetBit(D(), 6));
                        cycle -= 8;
                        break;
                    case 0xB3:  //RES  6,E
                        E(resetBit(E(), 6));
                        cycle -= 8;
                        break;
                    case 0xB4:  //RES  6,H
                        H(resetBit(H(), 6));
                        cycle -= 8;
                        break;
                    case 0xB5:  //RES  6,L
                        L(resetBit(L(), 6));
                        cycle -= 8;
                        break;
                    case 0xB6:  //RES  6,(HL)
                        writeB(HL(), resetBit(readB(HL()), 6));
                        cycle -= 16;
                        break;
                    case 0xB7:  //RES  6,A
                        A(resetBit(A(), 6));
                        cycle -= 8;
                        break;
                    case 0xB8:  //RES  7,B
                        B(resetBit(B(), 7));
                        cycle -= 8;
                        break;
                    case 0xB9:  //RES  7,C
                        C(resetBit(C(), 7));
                        cycle -= 8;
                        break;
                    case 0xBA:  //RES  7,D
                        D(resetBit(D(), 7));
                        cycle -= 8;
                        break;
                    case 0xBB:  //RES  7,E
                        E(resetBit(E(), 7));
                        cycle -= 8;
                        break;
                    case 0xBC:  //RES  7,H
                        H(resetBit(H(), 7));
                        cycle -= 8;
                        break;
                    case 0xBD:  //RES  7,L
                        L(resetBit(L(), 7));
                        cycle -= 8;
                        break;
                    case 0xBE:  //RES  7,(HL)
                        writeB(HL(), resetBit(readB(HL()), 7));
                        cycle -= 16;
                        break;
                    case 0xBF:  //RES  7,A
                        A(resetBit(A(), 7));
                        cycle -= 8;
                        break;
                    case 0xC0:  //SET  0,B
                        B(setBit(B(), 0));
                        cycle -= 8;
                        break;
                    case 0xC1:  //SET  0,C
                        C(setBit(C(), 0));
                        cycle -= 8;
                        break;
                    case 0xC2:  //SET  0,D
                        D(setBit(D(), 0));
                        cycle -= 8;
                        break;
                    case 0xC3:  //SET  0,E
                        E(setBit(E(), 0));
                        cycle -= 8;
                        break;
                    case 0xC4:  //SET  0,H
                        H(setBit(H(), 0));
                        cycle -= 8;
                        break;
                    case 0xC5:  //SET  0,L
                        L(setBit(L(), 0));
                        cycle -= 8;
                        break;
                    case 0xC6:  //SET  0,(HL)
                        writeB(HL(), setBit(readB(HL()), 0));
                        cycle -= 16;
                        break;
                    case 0xC7:  //SET  0,A
                        A(setBit(A(), 0));
                        cycle -= 8;
                        break;
                    case 0xC8:  //SET  1,B
                        B(setBit(B(), 1));
                        cycle -= 8;
                        break;
                    case 0xC9:  //SET  1,C
                        C(setBit(C(), 1));
                        cycle -= 8;
                        break;
                    case 0xCA:  //SET  1,D
                        D(setBit(D(), 1));
                        cycle -= 8;
                        break;
                    case 0xCB:  //SET  1,E
                        E(setBit(E(), 1));
                        cycle -= 8;
                        break;
                    case 0xCC:  //SET  1,H
                        H(setBit(H(), 1));
                        cycle -= 8;
                        break;
                    case 0xCD:  //SET  1,L
                        L(setBit(L(), 1));
                        cycle -= 8;
                        break;
                    case 0xCE:  //SET  1,(HL)
                        writeB(HL(), setBit(readB(HL()), 1));
                        cycle -= 16;
                        break;
                    case 0xCF:  //SET  1,A
                        A(setBit(A(), 1));
                        cycle -= 8;
                        break;
                    case 0xD0:  //SET  2,B
                        B(setBit(B(), 2));
                        cycle -= 8;
                        break;
                    case 0xD1:  //SET  2,C
                        C(setBit(C(), 2));
                        cycle -= 8;
                        break;
                    case 0xD2:  //SET  2,D
                        D(setBit(D(), 2));
                        cycle -= 8;
                        break;
                    case 0xD3:  //SET  2,E
                        E(setBit(E(), 2));
                        cycle -= 8;
                        break;
                    case 0xD4:  //SET  2,H
                        H(setBit(H(), 2));
                        cycle -= 8;
                        break;
                    case 0xD5:  //SET  2,L
                        L(setBit(L(), 2));
                        cycle -= 8;
                        break;
                    case 0xD6:  //SET  2,(HL)
                        writeB(HL(), setBit(readB(HL()), 2));
                        cycle -= 16;
                        break;
                    case 0xD7:  //SET  2,A
                        A(setBit(A(), 2));
                        cycle -= 8;
                        break;
                    case 0xD8:  //SET  3,B
                        B(setBit(B(), 3));
                        cycle -= 8;
                        break;
                    case 0xD9:  //SET  3,C
                        C(setBit(C(), 3));
                        cycle -= 8;
                        break;
                    case 0xDA:  //SET  3,D
                        D(setBit(D(), 3));
                        cycle -= 8;
                        break;
                    case 0xDB:  //SET  3,E
                        E(setBit(E(), 3));
                        cycle -= 8;
                        break;
                    case 0xDC:  //SET  3,H
                        H(setBit(H(), 3));
                        cycle -= 8;
                        break;
                    case 0xDD:  //SET  3,L
                        L(setBit(L(), 3));
                        cycle -= 8;
                        break;
                    case 0xDE:  //SET  3,(HL)
                        writeB(HL(), setBit(readB(HL()), 3));
                        cycle -= 16;
                        break;
                    case 0xDF:  //SET  3,A
                        A(setBit(A(), 3));
                        cycle -= 8;
                        break;
                    case 0xE0:  //SET  4,B
                        B(setBit(B(), 4));
                        cycle -= 8;
                        break;
                    case 0xE1:  //SET  4,C
                        C(setBit(C(), 4));
                        cycle -= 8;
                        break;
                    case 0xE2:  //SET  4,D
                        D(setBit(D(), 4));
                        cycle -= 8;
                        break;
                    case 0xE3:  //SET  4,E
                        E(setBit(E(), 4));
                        cycle -= 8;
                        break;
                    case 0xE4:  //SET  4,H
                        H(setBit(H(), 4));
                        cycle -= 8;
                        break;
                    case 0xE5:  //SET  4,L
                        L(setBit(L(), 4));
                        cycle -= 8;
                        break;
                    case 0xE6:  //SET  4,(HL)
                        writeB(HL(), setBit(readB(HL()), 4));
                        cycle -= 16;
                        break;
                    case 0xE7:  //SET  4,A
                        A(setBit(A(), 4));
                        cycle -= 8;
                        break;
                    case 0xE8:  //SET  5,B
                        B(setBit(B(), 5));
                        cycle -= 8;
                        break;
                    case 0xE9:  //SET  5,C
                        C(setBit(C(), 5));
                        cycle -= 8;
                        break;
                    case 0xEA:  //SET  5,D
                        D(setBit(D(), 5));
                        cycle -= 8;
                        break;
                    case 0xEB:  //SET  5,E
                        E(setBit(E(), 5));
                        cycle -= 8;
                        break;
                    case 0xEC:  //SET  5,H
                        H(setBit(H(), 5));
                        cycle -= 8;
                        break;
                    case 0xED:  //SET  5,L
                        L(setBit(L(), 5));
                        cycle -= 8;
                        break;
                    case 0xEE:  //SET  5,(HL)
                        writeB(HL(), setBit(readB(HL()), 5));
                        cycle -= 16;
                        break;
                    case 0xEF:  //SET  5,A
                        A(setBit(A(), 5));
                        cycle -= 8;
                        break;
                    case 0xF0:  //SET  6,B
                        B(setBit(B(), 6));
                        cycle -= 8;
                        break;
                    case 0xF1:  //SET  6,C
                        C(setBit(C(), 6));
                        cycle -= 8;
                        break;
                    case 0xF2:  //SET  6,D
                        D(setBit(D(), 6));
                        cycle -= 8;
                        break;
                    case 0xF3:  //SET  6,E
                        E(setBit(E(), 6));
                        cycle -= 8;
                        break;
                    case 0xF4:  //SET  6,H
                        H(setBit(H(), 6));
                        cycle -= 8;
                        break;
                    case 0xF5:  //SET  6,L
                        L(setBit(L(), 6));
                        cycle -= 8;
                        break;
                    case 0xF6:  //SET  6,(HL)
                        writeB(HL(), setBit(readB(HL()), 6));
                        cycle -= 16;
                        break;
                    case 0xF7:  //SET  6,A
                        A(setBit(A(), 6));
                        cycle -= 8;
                        break;
                    case 0xF8:  //SET  7,B
                        B(setBit(B(), 7));
                        cycle -= 8;
                        break;
                    case 0xF9:  //SET  7,C
                        C(setBit(C(), 7));
                        cycle -= 8;
                        break;
                    case 0xFA:  //SET  7,D
                        D(setBit(D(), 7));
                        cycle -= 8;
                        break;
                    case 0xFB:  //SET  7,E
                        E(setBit(E(), 7));
                        cycle -= 8;
                        break;
                    case 0xFC:  //SET  7,H
                        H(setBit(H(), 7));
                        cycle -= 8;
                        break;
                    case 0xFD:  //SET  7,L
                        L(setBit(L(), 7));
                        cycle -= 8;
                        break;
                    case 0xFE:  //SET  7,(HL)
                        writeB(HL(), setBit(readB(HL()), 7));
                        cycle -= 16;
                        break;
                    case 0xFF:  //SET  7,A
                        A(setBit(A(), 7));
                        cycle -= 8;
                        break;
                }
                break;
            case 0xCC: // CALL Z, nn
                if (zero()) {
                    push(PC + 2);
                    PC(readW(PC));
                    cycle -= 24;
                } else {
                    cycle -= 12;
                    PC++;
                    PC++;
                }
                break;
            case 0xCD: // CALL nn
                push(PC + 2);
                PC(readW(PC));
                cycle -= 24;
                break;
            case 0xCE: // ADC A, n
                A(adc8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xCF: // RST 08h
                push(PC+1);
                PC(0x08);
                cycle -= 16;
                break;
            case 0xD0: // RET NC
                if (!carry()) {
                    PC(pop());
                    cycle -= 20;
                } else {
                    cycle -= 8;
                }
                break;
            case 0xD1: // POP DE
                DE(pop());
                cycle -= 12;
                break;
            case 0xD2: // JP NC, nn
                if (!carry()) {
                    PC(readW(PC));
                    cycle -= 16;
                } else {
                    PC++;
                    PC++;
                    cycle -= 12;
                }
                break;
            case 0xD4: // CALL NC, nn
                if (!carry()) {
                    push(PC + 2);
                    PC(readW(PC));
                    cycle -= 24;
                } else {
                    cycle -= 12;
                    PC++;
                    PC++;
                }
                break;
            case 0xD5: // PUSH DE
                push(DE());
                cycle -= 16;
                break;
            case 0xD6: // SUB n
                A(sub8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xD7: // RST 10h
                push(PC+1);
                PC(0x10);
                cycle -= 16;
                break;
            case 0xD8: // RET C
                if (carry()) {
                    PC(pop());
                    cycle -= 20;
                } else {
                    cycle -= 8;
                }
                break;
            case 0xD9: // RETI
                PC(pop());
                cycle -= 16;
                interrupts = true;
                break;
            case 0xDA: // JP C, nn
                if (carry()) {
                    PC(readW(PC));
                    cycle -= 16;
                } else {
                    PC++;
                    PC++;
                    cycle -= 12;
                }
                break;
            case 0xDC: // CALL C, nn
                if (carry()) {
                    push(PC + 2);
                    PC(readW(PC));
                    cycle -= 24;
                } else {
                    cycle -= 12;
                    PC++;
                    PC++;
                }
                break;
            case 0xDE: // SBC A, n
                A(sbc8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xDF: // RST 18h
                push(PC+1);
                PC(0x18);
                cycle -= 16;
                break;
            case 0xE0: //LD (FF00 + nn), A
                writeB(0xFF00 + readB(PC++), A());
                cycle -= 12;
                break;
            case 0xE1: //POP HL
                HL(pop());
                cycle -= 12;
                break;
            case 0xE2: //LD (FF00 + C), A
                writeB(0xFF00 + C(), A());
                cycle -= 12;
                break;
            case 0xE5: //PUSH HL
                push(HL());
                cycle -= 16;
                break;
            case 0xE6: //AND n
                A(and8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xE7: //RST 20H
                push(PC+1);
                PC(0x20);
                cycle -= 16;
                break;
            case 0xE8: //ADD SP,nn
                SP(add16(SP(), sign8(readB(PC++))));
                cycle -= 16;
                break;
            case 0xE9: //JP (HL)
                PC(readW(HL()));
                cycle -= 4;
                break;
            case 0xEA: //LD (nnnn), A
                writeB(readW(PC++), A());
                PC++;
                cycle -= 16;
                break;
            case 0xEE: //XOR n
                A(xor8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xEF: //RST 28H
                push(PC+1);
                PC(0x28);
                cycle -= 16;
                break;
            case 0xF0: //LD A, (0xFF00 + nn)
                A(readB(0xFF00 + readB(PC++)));
                cycle -= 16; /*FLAGS*/ break;
            case 0xF1: //POP AF
                AF(pop());
                cycle -= 12;
                break;
            case 0xF3: //DI
                interrupts = false;
                cycle -= 4;
                break;
            case 0xF5: //PUSH AF
                push(AF());
                cycle -= 16;
                break;
            case 0xF6: //OR n
                A(or8(A(), readB(PC++)));
                cycle -= 8;
                break;
            case 0xF7: //RST 30H
                push(PC+1);
                PC(0x30);
                cycle -= 16;
                break;
            case 0xF8: //LD HL,SP+nn
                HL(add16(SP(), readB(PC++)));
                cycle -= 12;
                break;
            case 0xF9: //LD SP,HL
                SP(HL());
                cycle -= 12;
                break;
            case 0xFA: //LD A, (nnnn)
                A(readB(readW(PC++))); //NEEDS FLAGS ***
                PC++;
                cycle -= 16;
                break;
            case 0xFB: //EI
                interrupts = true;
                cycle -= 4;
                break;
            case 0xFE: //CP n
                sub8(A(), readB(PC++));
                cycle -= 8;
                break;
            case 0xFF: //RST 38H
                push(PC+1);
                PC(0x38);
                cycle -= 16;
                break;
        }
    }

    public int A() {
        return A;
    }

    public int F() {
        return F;
    }

    public int B() {
        return B;
    }

    public int C() {
        return C;
    }

    public int D() {
        return D;
    }

    public int E() {
        return E;
    }

    public int H() {
        return H;
    }

    public int L() {
        return L;
    }

    public int I() {
        return I;
    }

    public int R() {
        return R;
    }

    public int SP() {
        return SP;
    }

    public int PC() {
        return PC;
    }

    public int AF() {
        return (A << 8) | F;
    }

    public int BC() {
        return (B << 8) | C;
    }

    public int DE() {
        return (D << 8) | E;
    }

    public int HL() {
        return (H << 8) | L;
    }

    public void A(int val) {
        A = val & 0xFF;
    }

    public void F(int val) {
        F = val & 0xFF;
    }

    public void B(int val) {
        B = val & 0xFF;
    }

    public void C(int val) {
        C = val & 0xFF;
    }

    public void D(int val) {
        D = val & 0xFF;
    }

    public void E(int val) {
        E = val & 0xFF;
    }

    public void H(int val) {
        H = val & 0xFF;
    }

    public void L(int val) {
        L = val & 0xFF;
    }

    public void I(int val) {
        I = val & 0xFF;
    }

    public void R(int val) {
        R = val & 0xFF;
    }

    public void SP(int val) {
        SP = val & 0xFFFF;
    }

    public void PC(int val) {
        PC = val & 0xFFFF;
    }

    public void AF(int val) {
        A = (val & 0xFF00) >> 8;
        F = val & 0xFF;
    }

    public void BC(int val) {
        B = (val & 0xFF00) >> 8;
        C = val & 0xFF;
    }

    public void DE(int val) {
        D = (val & 0xFF00) >> 8;
        E = val & 0xFF;
    }

    public void HL(int val) {
        H = (val & 0xFF00) >> 8;
        L = val & 0xFF;
    }

    /*public boolean zero() {
        return zero;
    }

    public boolean carry() {
        return carry;
    }

    public boolean half() {
        return half;
    }

    public boolean subtract() {
        return sub;
    }

    public void zero(boolean zero) {
        this.zero = zero;
    }

    public void carry(boolean carry) {
        this.carry = carry;
    }

    public void half(boolean half) {
        this.half = half;
    }

    public void subtract(boolean sub) {
        this.sub = sub;
    }

    private boolean zero = false;
    private boolean carry = false;
    private boolean half = false;
    private boolean sub = false;*/

    public void zero(boolean set) {
        if (set) {
            F(setBit(F(), FZ));
        } else {
            F(resetBit(F(), FZ));
        }
    }

    public void carry(boolean set) {
        if (set) {
            F(setBit(F(), FC));
        } else {
            F(resetBit(F(), FC));
        }
    }

    public void half(boolean set) {
        if (set) {
            F(setBit(F(), FH));
        } else {
            F(resetBit(F(), FH));
        }
    }

    public void subtract(boolean set) {
        if (set) {
            F(setBit(F(), FN));
        } else {
            F(resetBit(F(), FN));
        }
    }

    public boolean zero() {
        return checkBit(F(), FZ);
    }

    public boolean carry() {
        return checkBit(F(), FC);
    }

    public boolean half() {
        return checkBit(F(), FH);
    }

    public boolean subtract() {
        return checkBit(F(), FN);
    }

    public static final int FZ = 7;
    public static final int FC = 6;
    public static final int FH = 5;
    public static final int FN = 4;
    public static final String[] opcodes = new String[]{
        "00    NOP",
        "01    LD   BC,nnnn",
        "02    LD   (BC),A",
        "03    INC  BC",
        "04    INC  B",
        "05    DEC  B",
        "06    LD   B,nn",
        "07    RLCA",
        "08    LD   (nnnn),SP",
        "09    ADD  HL,BC",
        "0A    LD   A,(BC)",
        "0B    DEC  BC",
        "0C    INC  C",
        "0D    DEC  C",
        "0E    LD   C,nn",
        "0F    RRCA",
        "10    STOP",
        "11    LD   DE,nnnn",
        "12    LD   (DE),A",
        "13    INC  DE",
        "14    INC  D",
        "15    DEC  D",
        "16    LD   D,nn",
        "17    RLA",
        "18    JR   disp",
        "19    ADD  HL,DE",
        "1A    LD   A,(DE)",
        "1B    DEC  DE",
        "1C    INC  E",
        "1D    DEC  E",
        "1E    LD   E,nn",
        "1F    RRA",
        "20    JR   NZ,disp",
        "21    LD   HL,nnnn",
        "22    LDI  (HL),A",
        "23    INC  HL",
        "24    INC  H",
        "25    DEC  H",
        "26    LD   H,nn",
        "27    DAA",
        "28    JR   Z,disp",
        "29    ADD  HL,HL",
        "2A    LDI  A,(HL)",
        "2B    DEC  HL",
        "2C    INC  L",
        "2D    DEC  L",
        "2E    LD   L,nn",
        "2F    CPL",
        "30    JR   NC,disp",
        "31    LD   SP,nnnn",
        "32    LDD  (HL),A",
        "33    INC  SP",
        "34    INC  (HL)",
        "35    DEC  (HL)",
        "36    LD   (HL),nn",
        "37    SCF",
        "38    JR   C,disp",
        "39    ADD  HL,SP",
        "3A    LDD  A,(HL)",
        "3B    DEC  SP",
        "3C    INC  A",
        "3D    DEC  A",
        "3E    LD   A,nn",
        "3F    CCF",
        "40    LD   B,B",
        "41    LD   B,C",
        "42    LD   B,D",
        "43    LD   B,E",
        "44    LD   B,H",
        "45    LD   B,L",
        "46    LD   B,(HL)",
        "47    LD   B,A",
        "48    LD   C,B",
        "49    LD   C,C",
        "4A    LD   C,D",
        "4B    LD   C,E",
        "4C    LD   C,H",
        "4D    LD   C,L",
        "4E    LD   C,(HL)",
        "4F    LD   C,A",
        "50    LD   D,B",
        "51    LD   D,C",
        "52    LD   D,D",
        "53    LD   D,E",
        "54    LD   D,H",
        "55    LD   D,L",
        "56    LD   D,(HL)",
        "57    LD   D,A",
        "58    LD   E,B",
        "59    LD   E,C",
        "5A    LD   E,D",
        "5B    LD   E,E",
        "5C    LD   E,H",
        "5D    LD   E,L",
        "5E    LD   E,(HL)",
        "5F    LD   E,A",
        "60    LD   H,B",
        "61    LD   H,C",
        "62    LD   H,D",
        "63    LD   H,E",
        "64    LD   H,H",
        "65    LD   H,L",
        "66    LD   H,(HL)",
        "67    LD   H,A",
        "68    LD   L,B",
        "69    LD   L,C",
        "6A    LD   L,D",
        "6B    LD   L,E",
        "6C    LD   L,H",
        "6D    LD   L,L",
        "6E    LD   L,(HL)",
        "6F    LD   L,A",
        "70    LD   (HL),B",
        "71    LD   (HL),C",
        "72    LD   (HL),D",
        "73    LD   (HL),E",
        "74    LD   (HL),H",
        "75    LD   (HL),L",
        "76    HALT",
        "77    LD   (HL),A",
        "78    LD   A,B",
        "79    LD   A,C",
        "7A    LD   A,D",
        "7B    LD   A,E",
        "7C    LD   A,H",
        "7D    LD   A,L",
        "7E    LD   A,(HL)",
        "7F    LD   A,A",
        "80    ADD  A,B",
        "81    ADD  A,C",
        "82    ADD  A,D",
        "83    ADD  A,E",
        "84    ADD  A,H",
        "85    ADD  A,L",
        "86    ADD  A,(HL)",
        "87    ADD  A,A",
        "88    ADC  A,B",
        "89    ADC  A,C",
        "8A    ADC  A,D",
        "8B    ADC  A,E",
        "8C    ADC  A,H",
        "8D    ADC  A,L",
        "8E    ADC  A,(HL)",
        "8F    ADC  A,A",
        "90    SUB  B",
        "91    SUB  C",
        "92    SUB  D",
        "93    SUB  E",
        "94    SUB  H",
        "95    SUB  L",
        "96    SUB  (HL)",
        "97    SUB  A",
        "98    SBC  A,B",
        "99    SBC  A,C",
        "9A    SBC  A,D",
        "9B    SBC  A,E",
        "9C    SBC  A,H",
        "9D    SBC  A,L",
        "9E    SBC  A,(HL)",
        "9F    SBC  A,A",
        "A0    AND  B",
        "A1    AND  C",
        "A2    AND  D",
        "A3    AND  E",
        "A4    AND  H",
        "A5    AND  L",
        "A6    AND  (HL)",
        "A7    AND  A",
        "A8    XOR  B",
        "A9    XOR  C",
        "AA    XOR  D",
        "AB    XOR  E",
        "AC    XOR  H",
        "AD    XOR  L",
        "AE    XOR  (HL)",
        "AF    XOR  A",
        "B0    OR   B",
        "B1    OR   C",
        "B2    OR   D",
        "B3    OR   E",
        "B4    OR   H",
        "B5    OR   L",
        "B6    OR   (HL)",
        "B7    OR   A",
        "B8    CP   B",
        "B9    CP   C",
        "BA    CP   D",
        "BB    CP   E",
        "BC    CP   H",
        "BD    CP   L",
        "BE    CP   (HL)",
        "BF    CP   A",
        "C0    RET  NZ",
        "C1    POP  BC",
        "C2    JP   NZ,nnnn",
        "C3    JP   nnnn",
        "C4    CALL NZ,nnnn",
        "C5    PUSH BC",
        "C6    ADD  A,nn",
        "C7    RST  00H",
        "C8    RET  Z",
        "C9    RET",
        "CA    JP   Z,nnnn",
        "CB    **SWITCH**",
        "CC    CALL Z,nnnn",
        "CD    CALL nnnn",
        "CE    ADC  A,nn",
        "CF    RST  8",
        "D0    RET  NC",
        "D1    POP  DE",
        "D2    JP   NC,nnnn",
        "D3    -",
        "D4    CALL NC,nnnn",
        "D5    PUSH DE",
        "D6    SUB  nn",
        "D7    RST  10H",
        "D8    RET  C",
        "D9    RETI",
        "DA    JP   C,nnnn",
        "DB    -",
        "DC    CALL C,nnnn",
        "DD    -",
        "DE    SBC  A,nn",
        "DF    RST  18H",
        "E0    LD   ($FF00+nn),A",
        "E1    POP  HL",
        "E2    LD   ($FF00+C),A",
        "E3    -",
        "E4    -",
        "E5    PUSH HL",
        "E6    AND  nn",
        "E7    RST  20H",
        "E8    ADD  SP,dd",
        "E9    JP   (HL)",
        "EA    LD   (nnnn),A",
        "EB    -",
        "EC    -",
        "ED    -",
        "EE    XOR  nn",
        "EF    RST  28H",
        "F0    LD   A,($FF00+nn)",
        "F1    POP  AF",
        "F2    LD   A,(C)",
        "F3    DI",
        "F4    -",
        "F5    PUSH AF",
        "F6    OR   nn",
        "F7    RST  30H",
        "F8    LD   HL,SP+dd",
        "F9    LD   SP,HL",
        "FA    LD   A,(nnnn)",
        "FB    EI",
        "FC    -",
        "FD    -",
        "FE    CP   nn",
        "FF    RST  38H"
    };
}