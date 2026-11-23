package dev.infend.vitasolver.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Greedy lookahead over visible free tiles. No claim of perfect hidden-layer knowledge.
 * The vision engine supplies signatures; a move is executed only if the pair is close.
 */
public final class MahjongSolver {
    public static final class Move {
        public final Tile first, second;
        public final float distance;
        public final double score;
        public Move(Tile a, Tile b, float distance, double score) {
            first=a;second=b;this.distance=distance;this.score=score;
        }
    }

    private MahjongSolver() {}

    /** A piece is free when the left or the right side is not physically occupied. */
    public static boolean isFree(Tile tile, List<Tile> tiles) {
        boolean left=false,right=false;
        for (Tile n:tiles) {
            if (tile.id==n.id) continue;
            double ydist=Math.abs(tile.centerY()-n.centerY());
            if (ydist > Math.min(tile.height,n.height)*0.44) continue;
            double diff=n.centerX()-tile.centerX();
            // Only peers in the same row. Heavily overlapping layers remain uncertain.
            double step=(tile.width+n.width)*0.5;
            if (diff < -step*0.64 && diff > -step*1.42) left=true;
            if (diff > step*0.64 && diff < step*1.42) right=true;
        }
        return !(left&&right);
    }

    /** Distance = difference of colored/dark strokes, normalized to ink content. */
    public static float distance(float[] a,float[] b) {
        if(a.length==0||a.length!=b.length) return 1f;
        double diff=0, ink=0;
        for(int i=0;i<a.length;i++) {
            diff+=Math.abs(a[i]-b[i]);
            ink+=Math.max(a[i],b[i]);
        }
        if(ink<0.5) return 1f; // Never match empty/undetected symbols.
        return (float)(diff/(ink+0.001));
    }

    public static Move findMove(List<Tile> tiles,float maxDistance,Set<String> banned) {
        List<Tile> free=new ArrayList<>();
        for(Tile t:tiles) if(isFree(t,tiles)) free.add(t);
        Move best=null;
        for(int i=0;i<free.size();i++) for(int j=i+1;j<free.size();j++) {
            Tile a=free.get(i),b=free.get(j);
            String key=pairKey(a,b);
            if (banned.contains(key)) continue;
            float d=distance(a.signature,b.signature);
            if (d>maxDistance) continue;
            List<Tile> remaining=new ArrayList<>(tiles);
            remaining.remove(a);remaining.remove(b);
            int released=0;
            for(Tile t:remaining) if(!isFree(t,tiles)&&isFree(t,remaining)) released++;
            double score=released*0.7 + (a.centerX()+b.centerX())*0.00001 + (1-d)*4.0;
            if(best==null || score>best.score)best=new Move(a,b,d,score);
        }
        return best;
    }

    public static String pairKey(Tile a,Tile b) {
        int lo=Math.min(a.id,b.id),hi=Math.max(a.id,b.id);
        return lo+":"+hi;
    }

    public static Set<String> emptyBanList(){return new HashSet<>();}
}
