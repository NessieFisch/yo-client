package com.myclient.sky;

/** Add your own effects here, they show up in the menu automatically. */
public enum SkyEffect {
    //            label            tintTop     tintBottom  particle    vx     vy     count size streak stars
    CLEAR        ("Clear",         0,          0,          0,          0f,    0f,    0,    0,   false, false),
    SANDSTORM    ("Sandstorm",     0xAAC9A227, 0x66806A1E, 0xCCE6C36A, 1.4f,  0.05f, 260,  2,   true,  false),
    SNOWSTORM    ("Snowstorm",     0x88DDE8F2, 0x55AFC4D8, 0xFFFFFFFF, 0.5f,  0.35f, 350,  2,   false, false),
    SNOWY_GALAXY ("Snowy Galaxy",  0xAA1B0B3F, 0x552A6FBF, 0xFFFFFFFF, 0.03f, 0.12f, 120,  1,   false, true),
    NETHER_ASH   ("Nether Ash",    0x99601010, 0x66200505, 0xFFFF7A2A, 0.05f, -0.15f,120,  1,   false, false),
    TOXIC_FOG    ("Toxic Fog",     0x8855A62A, 0x6628501A, 0x88B6FF6A, 0.1f,  0.02f, 80,   3,   false, false),
    AURORA       ("Aurora",        0x8800C98A, 0x225A2BD1, 0,          0f,    0f,    0,    0,   false, true);

    public final String label;
    public final int tintTop, tintBottom, particle;
    public final float vx, vy;
    public final int count, size;
    public final boolean streak, stars;

    SkyEffect(String label, int tintTop, int tintBottom, int particle, float vx, float vy,
              int count, int size, boolean streak, boolean stars) {
        this.label = label; this.tintTop = tintTop; this.tintBottom = tintBottom; this.particle = particle;
        this.vx = vx; this.vy = vy; this.count = count; this.size = size; this.streak = streak; this.stars = stars;
    }
}
