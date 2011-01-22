package gameboy;

import gameboy.memory.Access;
import gameboy.memory.Dummy;
import gameboy.memory.RamPage;
import gameboy.memory.RomPage;
import gameboy.memory.Segment;

/**
 *
 * @author benland100
 */
public class GameBoyMemory {

    private Cartridge cart;
    private RomPage[] romPages;
    private RamPage videoRam;
    private RamPage[] externRams;
    private RamPage[] internRams;
    private RamPage sprites;
    private IOPorts ports;
    private RamPage highRam;
    private InterruptReg inter;
    private int mem_type;
    private int rom_size;
    private int ram_size;


    public GameBoyMemory(IOPorts ports, Cartridge cart) {
        this.cart = cart;
        romPages = new RomPage[cart.pages()];
        for (int i = 0; i < romPages.length; i++) {
            romPages[i] = cart.page(i);
        }
        videoRam = new RamPage(0x2000);
        internRams = new RamPage[8];
        for (int i = 0; i < 8; i++) {
            internRams[i] = new RamPage(0x1000);
        }
        externRams = new RamPage[4];
        for (int i = 0; i < 4; i++) {
            externRams[i] = new RamPage(0x1000);
        }
        sprites = new RamPage(0x00A0);
        this.ports = ports;
        inter = new InterruptReg();
        highRam = new RamPage(0x80);

        mem_type = readB(0x0147);
        rom_size = readB(0x0148);
        ram_size = readB(0x0146);
        System.out.println("Cartridge Type: " + mem_type);
        System.out.println("ROM Size: " + rom_size);
        System.out.println("RAM Size: " + ram_size);

    }

    public RamPage videoRam() {
        return videoRam;
    }

    public RamPage spriteRam() {
        return sprites;
    }

    private boolean mode = false; // f:t::rom:ram
    int rombank = 1;
    int rambank = 1;

    public int read(int pos) {
        if (pos < 0x4000) {
            return romPages[0].readB(pos);
        } else if (pos < 0x8000) {
            return romPages[rombank].readB(pos & 0x3FFF);
        } else if (pos < 0xA000) {
            return videoRam.readB(pos & 0x1FFF);
        } else if (pos < 0xC000) {
            return externRams[rambank].readB(pos & 0x0FFF);
        } else if (pos < 0xD000) {
            return internRams[0].readB(pos & 0x0FFF);
        } else if (pos < 0xE000) {
            return internRams[rambank].readB(pos & 0x0FFF);
        } else if (pos < 0xF000) {
            return internRams[0].readB(pos & 0x0FFF);
        } else if (pos < 0xFE00) {
            return internRams[rambank].readB(pos & 0x0FFF);
        } else if (pos < 0xFEA0) {
            return sprites.readB(pos & 0x00FF);
        } else if (pos < 0xFF00) {
        } else if (pos < 0xFF80) {
            return ports.readB(pos & 0x00FF);
        } else if (pos < 0xFFFF) {
            return highRam.readB((pos & 0x00FF) - 0x80);
        } else if (pos == 0xFFFF) {
            return inter.readB(0);
        }
        System.out.println("SEGFAULT - READ - 0x" + Integer.toHexString(pos));
        return 0xFF & (int)(Math.random() * 0xFF);
        //throw new RuntimeException("SegFault: READ - 0x" + Integer.toHexString(pos));
    }

    public void write(int pos, int val) {
        if (pos < 0x8000) {
            switch (mem_type) {
                case 0x00:
                    System.out.println("NO MBC - WRITE DETECTED");
                    return;
                case 0x01:
                case 0x02:
                case 0x03:
                    if (pos < 0x2000) {
                        //ramstuff
                    } else if (pos < 0x4000) {
                        if (mode) {
                            rombank = (val & 0x1F);
                        } else {
                            rombank = (rombank & 0x6) | (val & 0x1F);
                        }
                    } else if (pos < 0x6000) {
                        if (mode) {
                            rambank = val & 0x3;
                        } else {
                            rombank = (rombank & 0x1F) | ((val & 0x3) << 5);
                        }
                    } else if (pos < 0x8000) {
                        mode = val != 0;
                    }
                    break;
                case 0x1B:
                    if (pos >= 0x2000 && pos < 0x3000) {
                        rombank = (rombank & 0x100) | (val & 0xFF);
                    } else if (pos < 0x4000) {
                        rombank = (rombank & 0xFF) | ((val & 0x01) << 9);
                    } else if (pos < 0x5000) {
                        rambank = val & 0x0F;
                    }
                    break;
                default:
                    throw new RuntimeException("Segfault: NO MBC");
            }
        } else if (pos < 0xA000) {
            videoRam.writeB(pos & 0x1FFF, val);
        } else if (pos < 0xC000) {
            externRams[rambank].writeB(pos & 0x0FFF, val);
        } else if (pos < 0xD000) {
            internRams[0].writeB(pos & 0x0FFF, val);
        } else if (pos < 0xE000) {
            internRams[rambank].writeB(pos & 0x0FFF, val);
        } else if (pos < 0xF000) {
            internRams[0].writeB(pos & 0x0FFF, val);
        } else if (pos < 0xFE00) {
            internRams[rambank].writeB(pos & 0x0FFF, val);
        } else if (pos < 0xFEA0) {
            sprites.writeB(pos & 0x00FF, val);
        } else if (pos < 0xFF00) {
            //throw new RuntimeException("SegFault: WRITE - 0x" + Integer.toHexString(pos));
        } else if (pos < 0xFF80) {
            ports.writeB(pos & 0x00FF, val);
        } else if (pos < 0xFFFF) {
            highRam.writeB((pos & 0x00FF) - 0x80, val);
        } else if (pos == 0xFFFF) {
            inter.writeB(0, val);
        }
    }

    public int readB(int pos) {
        int b = read(pos);
        //System.out.println("R:" + Integer.toHexString(pos) + " - V:" + Integer.toHexString(b));
        return b;
    }

    public void writeB(int pos, int val) {
        write(pos, val);
        //System.out.println("W:" + Integer.toHexString(pos) + " - V:" + Integer.toHexString(val));
    }
 

}
