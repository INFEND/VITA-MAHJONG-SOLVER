package dev.infend.vitasolver.core;

public final class Tile {
    public final int id;
    public final int x, y, width, height;
    public final float[] signature;

    public Tile(int id, int x, int y, int width, int height, float[] signature) {
        this.id=id; this.x=x; this.y=y; this.width=width; this.height=height;
        this.signature=signature;
    }

    public int centerX() {return x+width/2;}
    public int centerY() {return y+height/2;}
}
