package gameboy;

import gameboy.memory.RamPage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 *
 * @author benland100
 */
public class LCD extends JFrame {

    private static final int buff_w = 256;
    private static final int buff_h = 256;
    private static final int scrn_w = 160;
    private static final int scrn_h = 144;

    int[] buffer_0_a = new int[buff_h*buff_w];
    int[] buffer_0_b = new int[buff_h*buff_w];
    int[] buffer_1_a = new int[buff_h*buff_w];
    int[] buffer_1_b = new int[buff_h*buff_w];
    
    BufferedImage screen = new BufferedImage(scrn_w,scrn_h,BufferedImage.TYPE_INT_RGB);
    RamPage vram;
    RamPage spriteram;
    IOPorts ports;
    Sprite[] sprite_data = new Sprite[40];

    public LCD(RamPage vram, RamPage spriteram, IOPorts ports) {
        super("GameBoy Color Emulator");
        this.vram = vram;
        this.spriteram = spriteram;
        this.ports = ports;
        for (int i = 0; i < sprite_data.length; i++) {
            sprite_data[i] = new Sprite();
        }
        add(new JLabel(new ImageIcon(screen)));
        pack();
        setVisible(true);
        this.setAlwaysOnTop(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    public static final int[] SHADES_OF_GREY = new int[] { 0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000 };
    public static final int[] BLANK_LINE = new int[160];
    static {
        for (int i = 0; i < 160; i++) BLANK_LINE[i] = 0xFFFFFF;
    }

    public void renderLine(int line) {
        int ctrl = ports.LCD_CTRL;
        if ((ctrl & 0x80) == 0) {
            screen.setRGB(0, line, scrn_w, 1, BLANK_LINE, 0, scrn_w);
        } else {
            int[] shades = new int[4];
            int reg = ports.LCD_BGP;
            for (int i = 0; i < 4; i++) {
                shades[i] = SHADES_OF_GREY[(reg >> (i*2)) & 0x3];
            }

            if ((ctrl & 0x01) != 0) {
                int bgx = ports.LCD_SCX;
                int bgy = (ports.LCD_SCY + line) % buff_h;

                int[] line_colors = new int[buff_w];
                if ((ctrl & 0x10) != 0) {
                    if ((ctrl & 0x08) == 0) {
                        for (int i = 0; i < buff_w; i++) {
                            line_colors[i] = shades[0x3 & buffer_0_a[i+buff_w*bgy]];
                        }
                    } else {
                        for (int i = 0; i < buff_w; i++) {
                            line_colors[i] = shades[0x3 & buffer_0_b[i+buff_w*bgy]];
                        }
                    }
                } else {
                    if ((ctrl & 0x08) == 0) {
                        for (int i = 0; i < buff_w; i++) {
                            line_colors[i] = shades[0x3 & buffer_1_a[i+buff_w*bgy]];
                        }
                    } else {
                        for (int i = 0; i < buff_w; i++) {
                            line_colors[i] = shades[0x3 & buffer_1_b[i+buff_w*bgy]];
                        }
                    }
                }

                int w = buff_w - bgx;
                if (w >= scrn_w) {
                    screen.setRGB(0, line, scrn_w, 1, line_colors, bgx, buff_w);
                } else {
                    screen.setRGB(0, line, w, 1, line_colors, bgx, buff_w);
                    screen.setRGB(w, line, scrn_w - w, 1, line_colors, 0, buff_w);
                }
            }

            if ((ctrl & 0x10) == 0) {
                int[] pal0 = new int[4];
                int[] pal1 = new int[4];
                for (int i = 1; i < 4; i++) {
                    pal0[i] = SHADES_OF_GREY[(ports.LCD_OBP0 >> (i*2)) & 0x3];
                    pal1[i] = SHADES_OF_GREY[(ports.LCD_OBP1 >> (i*2)) & 0x3];
                }
                pal0[0] = 0xffffff;
                pal1[0] = 0xffffff;
                int[] sprite_line = new int[8];
                for (int i = 0; i < 40; i++) {
                    Sprite sprite = sprite_data[i];
                    if (sprite.y >= line - 7 && sprite.y <= line && sprite.x >= -7 && sprite.x < scrn_w) {
                        int soff = 8*(line - sprite.y);
                        if (sprite.pal1) {
                            for (int c = 0; c < 8; c++) {
                                sprite_line[c] = pal1[0x3 & sprite.data[c+soff]];
                            }
                        } else {
                            for (int c = 0; c < 8; c++) {
                                sprite_line[c] = pal0[0x3 & sprite.data[c+soff]];
                            }
                        }
                        if (sprite.x < 0) {
                            screen.setRGB(0, line, 8 + sprite.x, 1, sprite_line, 0 - sprite.x, 8);
                        } else if (sprite.x + 7 >= scrn_w) {
                            screen.setRGB(sprite.x, line, scrn_w - sprite.x + 1, 1, sprite_line, 0, 8);
                        } else {
                            screen.setRGB(sprite.x, line, 8, 1, sprite_line, 0, 8);
                        }
                    }
                }
            }

            if ((ctrl & 0x20) != 0) {
                int wx = ports.LCD_WX;
                int wy = ports.LCD_WY;
                if (line >= wy) {
                    wy = line - wy;
                    int[] line_colors = new int[buff_w];
                    if ((ctrl & 0x10) != 0) {
                        if ((ctrl & 0x08) == 0) {
                            for (int i = 0; i < scrn_w; i++) {
                                line_colors[i] = shades[0x3 & buffer_0_a[i+buff_w*wy]];
                            }
                        } else {
                            for (int i = 0; i < scrn_w; i++) {
                                line_colors[i] = shades[0x3 & buffer_0_b[i+buff_w*wy]];
                            }
                        }
                    } else {
                        if ((ctrl & 0x08) == 0) {
                            for (int i = 0; i < scrn_w; i++) {
                                line_colors[i] = shades[0x3 & buffer_1_a[i+buff_w*wy]];
                            }
                        } else {
                            for (int i = 0; i < scrn_w; i++) {
                                line_colors[i] = shades[0x3 & buffer_1_b[i+buff_w*wy]];
                            }
                        }
                    }
                    wx = wx - 7 < 0 ? 0 : wx - 7;
                    screen.setRGB(wx, line, scrn_w - wx, 1, line_colors, 0, buff_w);

                }
            }
        }
    }

    public void gatherData() {
        repaint();
        int[] buffer;
        buffer = vram.getBuffer();
        //WINDOW & BACKGROUND TILES
        int[][] tiles_a = new int[32][32];
        int[][] tiles_b = new int[32][32];
        for (int i = 0; i < 32*32; i++) {
            tiles_a[i%32][i/32] = buffer[0x1800+i];
            tiles_b[i%32][i/32] = buffer[0x1C00+i];
        }
        //WINDOW & BACKGROUND DATA
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                int pos_a_0 = (tiles_a[x][y]) * 16;
                int pos_b_0 = (tiles_b[x][y]) * 16;
                int pos_a_1 = ((byte)tiles_a[x][y]) * 16 + 0x1000;
                int pos_b_1 = ((byte)tiles_b[x][y]) * 16 + 0x1000;
                for (int i = 0, c = x*8+y*8*buff_w; i < 8; i++, c+=buff_w-8) {
                    int h_a_0 = buffer[pos_a_0];
                    int h_a_1 = buffer[pos_a_1];
                    int h_b_0 = buffer[pos_b_0];
                    int h_b_1 = buffer[pos_b_1];
                    pos_a_0++;
                    pos_b_0++;
                    pos_a_1++;
                    pos_b_1++;
                    int l_a_0 = buffer[pos_a_0];
                    int l_a_1 = buffer[pos_a_1];
                    int l_b_0 = buffer[pos_b_0];
                    int l_b_1 = buffer[pos_b_1];
                    pos_a_0++;
                    pos_b_0++;
                    pos_a_1++;
                    pos_b_1++;
                    buffer_0_a[c  ] = ((h_a_0 >> 7) & 1) | ((l_a_0 >> 6) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 7) & 1) | ((l_b_0 >> 6) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 7) & 1) | ((l_a_1 >> 6) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 7) & 1) | ((l_b_1 >> 6) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 6) & 1) | ((l_a_0 >> 5) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 6) & 1) | ((l_b_0 >> 5) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 6) & 1) | ((l_a_1 >> 5) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 6) & 1) | ((l_b_1 >> 5) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 5) & 1) | ((l_a_0 >> 4) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 5) & 1) | ((l_b_0 >> 4) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 5) & 1) | ((l_a_1 >> 4) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 5) & 1) | ((l_b_1 >> 4) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 4) & 1) | ((l_a_0 >> 3) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 4) & 1) | ((l_b_0 >> 3) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 4) & 1) | ((l_a_1 >> 3) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 4) & 1) | ((l_b_1 >> 3) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 3) & 1) | ((l_a_0 >> 2) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 3) & 1) | ((l_b_0 >> 2) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 3) & 1) | ((l_a_1 >> 2) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 3) & 1) | ((l_b_1 >> 2) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 2) & 1) | ((l_a_0 >> 1) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 2) & 1) | ((l_b_0 >> 1) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 2) & 1) | ((l_a_1 >> 1) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 2) & 1) | ((l_b_1 >> 1) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 1) & 1) | ((l_a_0 >> 0) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 1) & 1) | ((l_b_0 >> 0) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 1) & 1) | ((l_a_1 >> 0) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 1) & 1) | ((l_b_1 >> 0) & 2);
                    buffer_0_a[c  ] = ((h_a_0 >> 0) & 1) | ((l_a_0 << 1) & 2);
                    buffer_0_b[c  ] = ((h_b_0 >> 0) & 1) | ((l_b_0 << 1) & 2);
                    buffer_1_a[c  ] = ((h_a_1 >> 0) & 1) | ((l_a_1 << 1) & 2);
                    buffer_1_b[c++] = ((h_b_1 >> 0) & 1) | ((l_b_1 << 1) & 2);
                }
            }
        }
        //SPRITES
        int[] sprites = spriteram.getBuffer();
        for (int s = 0; s < 40; s++) {
            Sprite sprite = sprite_data[s];
            sprite.y = sprites[s*4+0] - 16;
            sprite.x = sprites[s*4+1] - 8;
            int pos = sprites[s*4+2] * 16;
            int flags = sprites[s*4+3];
            sprite.priority = (flags&0x80)!=0;
            sprite.yflip = (flags&0x40)!=0;
            sprite.xflip = (flags&0x20)!=0;
            sprite.pal1 = (flags&0x10)!=0;
            int[] data = sprite.data;
            for (int i = 0, c = 0; i < 8; i++) {
                int a = buffer[pos++];
                int b = buffer[pos++];
                data[c++] = ((a >> 7) & 1) | ((b >> 6) & 2);
                data[c++] = ((a >> 6) & 1) | ((b >> 5) & 2);
                data[c++] = ((a >> 5) & 1) | ((b >> 4) & 2);
                data[c++] = ((a >> 4) & 1) | ((b >> 3) & 2);
                data[c++] = ((a >> 3) & 1) | ((b >> 2) & 2);
                data[c++] = ((a >> 2) & 1) | ((b >> 1) & 2);
                data[c++] = ((a >> 1) & 1) | ((b >> 0) & 2);
                data[c++] = ((a >> 0) & 1) | ((b << 1) & 2);
            }
        }
    }

    private static class Sprite {
        int x,y;
        int[] data = new int[64];
        boolean priority;
        boolean xflip, yflip;
        boolean pal1;
    }

}
