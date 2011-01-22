/**
 *  Copyright 2010 by Benjamin J. Land (a.k.a. BenLand100)
 *
 *  This file is part of GameBoyColor.
 *
 *  GameBoyColor is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  GameBoyColor is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with GameBoyColor. If not, see <http://www.gnu.org/licenses/>.
 */

package gameboy;

import gameboy.memory.Segment;

/**
 *
 * @author benland100
 */
public class IOPorts extends Segment {

    public int LCD_CTRL = 0x91;
    public int LCD_STAT = 0;
    public int LCD_SCY = 0;
    public int LCD_SCX = 0;
    public int LCD_LY = 0;
    public int LCD_LYC = 0;
    public int LCD_WY = 0;
    public int LCD_WX = 0;
    public int LCD_BGP = 0xFC;
    public int LCD_OBP0 = 0xFF;
    public int LCD_OBP1 = 0xFF;

    public int JOYPAD = 0;

    public int INTERRUPT_FLAG = 0;

    public int TIMER_CNTR = 0;
    public int TIMER_INIT = 0;
    public int TIMER_CTRL = 0;

    public int LCD_DMA = 0;

    public int readB(int pos) {
        switch (pos) {
            case 0x00: { return JOYPAD; }
            case 0x01: { /*IO DISABLED*/ } return 0;
            case 0x02: { /*IO DISABLED*/ } return 0;
            case 0x03: { } break;
            case 0x04: { } break;
            case 0x05: { return TIMER_CNTR; }
            case 0x06: { return TIMER_INIT; }
            case 0x07: { return TIMER_CTRL; }
            case 0x08: { } break;
            case 0x09: { } break;
            case 0x0A: { } break;
            case 0x0B: { } break;
            case 0x0C: { } break;
            case 0x0D: { } break;
            case 0x0E: { } break;
            case 0x0F: { return INTERRUPT_FLAG; }
            /* SOUND */
            case 0x27: { } break;
            case 0x28: { } break;
            case 0x29: { } break;
            case 0x2A: { } break;
            case 0x2B: { } break;
            case 0x2C: { } break;
            case 0x2D: { } break;
            case 0x2E: { } break;
            case 0x2F: { } break;
            /* WAVE */
            case 0x40: { return LCD_CTRL; }
            case 0x41: { return LCD_STAT; }
            case 0x42: { return LCD_SCY; }
            case 0x43: { return LCD_SCX; }
            case 0x44: { return LCD_LY; }
            case 0x45: { return LCD_LYC; }
            case 0x46: { } break;
            case 0x47: { return LCD_BGP; }
            case 0x48: { return LCD_OBP0; }
            case 0x49: { return LCD_OBP1; }
            case 0x4A: { return LCD_WY; }
            case 0x4B: { return LCD_WX; }
            case 0x4C: { } break;
            case 0x4D: { } break;
            case 0x4E: { } break;
            case 0x4F: { /*RETURN VRAM BANK*/ } break;
            case 0x50: { } break;
            case 0x51: { } break;
            case 0x52: { } break;
            case 0x53: { } break;
            case 0x54: { } break;
            case 0x55: { } break;
            case 0x56: { } break;
            case 0x57: { } break;
            case 0x58: { } break;
            case 0x59: { } break;
            case 0x5A: { } break;
            case 0x5B: { } break;
            case 0x5C: { } break;
            case 0x5D: { } break;
            case 0x5E: { } break;
            case 0x5F: { } break;
            case 0x60: { } break;
            case 0x61: { } break;
            case 0x62: { } break;
            case 0x63: { } break;
            case 0x64: { } break;
            case 0x65: { } break;
            case 0x66: { } break;
            case 0x67: { } break;
            case 0x68: { } break;
            case 0x69: { } break;
            case 0x6A: { } break;
            case 0x6B: { } break;
            case 0x6C: { } break;
            case 0x6D: { } break;
            case 0x6E: { } break;
            case 0x6F: { } break;
            case 0x70: { } break;
            case 0x71: { } break;
            case 0x72: { } break;
            case 0x73: { } break;
            case 0x74: { } break;
            case 0x75: { } break;
            case 0x76: { } break;
            case 0x77: { } break;
            case 0x78: { } break;
            case 0x79: { } break;
            case 0x7A: { } break;
            case 0x7B: { } break;
            case 0x7C: { } break;
            case 0x7D: { } break;
            case 0x7E: { } break;
            case 0x7F: { } break;
            default:
                //System.out.println("Ignored: Read - 0x" + Integer.toHexString(pos));
                return 0;
        }
        System.out.println("Ignored: Read - 0x" + Integer.toHexString(pos));
        return (int)(Math.random() * 0xFF);
        //throw new RuntimeException("God Damn Ports! Read - " + pos);
    }

    public void writeB(int pos, int val) {        switch (pos) {
            case 0x00: { JOYPAD = val; } return;
            case 0x01: { /*IO DISABLED*/ } return;
            case 0x02: { /*IO DISABLED*/ } return;
            case 0x03: { } break;
            case 0x04: { } break;
            case 0x05: { TIMER_CNTR = val; } return;
            case 0x06: { TIMER_INIT = val; } return;
            case 0x07: { TIMER_CTRL = val; } return;
            case 0x08: { } break;
            case 0x09: { } break;
            case 0x0A: { } break;
            case 0x0B: { } break;
            case 0x0C: { } break;
            case 0x0D: { } break;
            case 0x0E: { } break;
            case 0x0F: { INTERRUPT_FLAG = val; } return;
            /* SOUND */
            case 0x27: { } break;
            case 0x28: { } break;
            case 0x29: { } break;
            case 0x2A: { } break;
            case 0x2B: { } break;
            case 0x2C: { } break;
            case 0x2D: { } break;
            case 0x2E: { } break;
            case 0x2F: { } break;
            /* WAVE */
            case 0x40: { LCD_CTRL = val; } return;
            case 0x41: { LCD_STAT = val; } return;
            case 0x42: { LCD_SCY = val; } return;
            case 0x43: { LCD_SCX = val; } return;
            case 0x44: { LCD_LY = 0; } return;
            case 0x45: { LCD_LYC = val; } return;
            case 0x46: { LCD_DMA = val; } return;
            case 0x47: { LCD_BGP = val; } return;
            case 0x48: { LCD_OBP0 = val; } return;
            case 0x49: { LCD_OBP1 = val; } return;
            case 0x4A: { LCD_WY = val; } return;
            case 0x4B: { LCD_WX = val; } return;
            case 0x4C: { } break;
            case 0x4D: { System.out.println("SPEED SWITCH!?"); } return;
            case 0x4E: { } break;
            case 0x4F: { /*SWITCH VRAM BANK*/ } break;
            case 0x50: { } break;
            case 0x51: { } break;
            case 0x52: { } break;
            case 0x53: { } break;
            case 0x54: { } break;
            case 0x55: { } break;
            case 0x56: { } break;
            case 0x57: { } break;
            case 0x58: { } break;
            case 0x59: { } break;
            case 0x5A: { } break;
            case 0x5B: { } break;
            case 0x5C: { } break;
            case 0x5D: { } break;
            case 0x5E: { } break;
            case 0x5F: { } break;
            case 0x60: { } break;
            case 0x61: { } break;
            case 0x62: { } break;
            case 0x63: { } break;
            case 0x64: { } break;
            case 0x65: { } break;
            case 0x66: { } break;
            case 0x67: { } break;
            case 0x68: { System.out.print("BG Palette Index"); } return;
            case 0x69: { System.out.print("BG Palette Data"); } return;
            case 0x6A: { System.out.print("Sprite Palette Index"); } return;
            case 0x6B: { System.out.print("Sprite Palette Data"); } return;
            case 0x6C: { } break;
            case 0x6D: { } break;
            case 0x6E: { } break;
            case 0x6F: { } break;
            case 0x70: { } break;
            case 0x71: { } break;
            case 0x72: { } break;
            case 0x73: { } break;
            case 0x74: { } break;
            case 0x75: { } break;
            case 0x76: { } break;
            case 0x77: { } break;
            case 0x78: { } break;
            case 0x79: { } break;
            case 0x7A: { } break;
            case 0x7B: { } break;
            case 0x7C: { } break;
            case 0x7D: { } break;
            case 0x7E: { } break;
            case 0x7F: { } break;
            default:
                /* IGNORE SOUND */
                //System.out.println("Ignored: Write - 0x" + Integer.toHexString(pos));
                return;
        }
        System.out.println("WRITING TO AN UNBOUND PORT! - 0x" + Integer.toHexString(pos));
        //throw new RuntimeException("God Damn Ports! Write - 0x" + Integer.toHexString(pos));
    }

}
