package com.myclient.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Tiny pixel-art icons drawn with fills ('#' = pixel). */
public final class Icons {
    public static final String[] MONITOR = {
            "###########", "#.........#", "#.........#", "#.........#", "#.........#", "###########", "....###....", "..#######.." };
    public static final String[] CLOUD = {
            "...###.....", "..#####.##.", ".##########", "###########", "###########", ".#########." };
    public static final String[] CLOCK = {
            "..#####..", ".#.....#.", "#...#...#", "#...#...#", "#...###.#", "#.......#", ".#.....#.", "..#####.." };
    public static final String[] HEAD = {
            "HHHHHHHH", "HHHHHHHH", "HSSSSSSH", "SESSSSES", "SSSSSSSS", "SSSMMSSS", "SSSSSSSS", ".SSSSSS." };
    public static final String[] ARROW = {
            "...#...", "..###..", ".#####.", "#######", "...#...", "...#..." };
    public static final String[] PREV = {
            "#....#.", "#...##.", "#..###.", "#.####.", "#..###.", "#...##.", "#....#." };
    public static final String[] NEXT = {
            ".#....#", ".##...#", ".###..#", ".####.#", ".###..#", ".##...#", ".#....#" };
    public static final String[] PAUSE = {
            "##.##", "##.##", "##.##", "##.##", "##.##", "##.##", "##.##" };
    public static final String[] PLAY = {
            "#....", "##...", "###..", "####.", "###..", "##...", "#...." };
    public static final String[] NOTE = {
            "..#####", "..#...#", "..#...#", "..#...#", ".##..##", "###.###", ".#...#." };
    public static final String[] CROWN = {
            "#...#...#", "##.###.##", "#########", "#########", "#########" };

    public static void draw(GuiGraphicsExtractor g, String[] p, int x, int y, int color) {
        for (int r = 0; r < p.length; r++) {
            String row = p[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) == '#') {
                    int s = c;
                    while (c < row.length() && row.charAt(c) == '#') c++;
                    g.fill(x + s, y + r, x + c, y + r + 1, color);
                } else c++;
            }
        }
    }

    public static void head(GuiGraphicsExtractor g, int x, int y) {
        for (int r = 0; r < HEAD.length; r++) {
            for (int c = 0; c < HEAD[r].length(); c++) {
                int col;
                switch (HEAD[r].charAt(c)) {
                    case 'H' -> col = 0xFF2B1B14;
                    case 'S' -> col = 0xFFD9A77C;
                    case 'E' -> col = 0xFF1A1A1A;
                    case 'M' -> col = 0xFF8A4B3B;
                    default -> { continue; }
                }
                g.fill(x + c, y + r, x + c + 1, y + r + 1, col);
            }
        }
    }

    public static int w(String[] p) { return p[0].length(); }
    public static int h(String[] p) { return p.length; }
}
