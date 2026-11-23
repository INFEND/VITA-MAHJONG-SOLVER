package dev.infend.vitasolver;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import dev.infend.vitasolver.core.Tile;

/**
 * Template-free prototype for clean, exposed Mahjong tile faces.
 * Does not reliably infer obscured/stacked pieces or every Vita skin.
 */
public final class TileVision {
    private static final int MAX_W=820;
    private static final int S=18;
    private TileVision(){}

    public static final class Detection {
        public final List<Tile> tiles;
        public final float scale;
        public final int visibleCandidates;
        Detection(List<Tile> tiles,float scale,int count){this.tiles=tiles;this.scale=scale;visibleCandidates=count;}
    }

    public static Detection detect(Bitmap screenshot) {
        float factor=Math.min(1f,(float)MAX_W/screenshot.getWidth());
        int w=Math.max(1,Math.round(screenshot.getWidth()*factor));
        int h=Math.max(1,Math.round(screenshot.getHeight()*factor));
        Bitmap source=factor==1f?screenshot:Bitmap.createScaledBitmap(screenshot,w,h,true);
        int[] px=new int[w*h];
        source.getPixels(px,0,w,0,0,w,h);
        if(source!=screenshot)source.recycle();
        byte[] mask=new byte[w*h];
        for(int y=(int)(h*0.07f);y<(int)(h*0.92f);y++){
            int off=y*w;
            for(int x=0;x<w;x++) {
                int c=px[off+x],r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
                if (r>156 && g>153 && b>147 && max-min<74) mask[off+x]=1;
            }
        }
        int[] queue=new int[w*h];
        List<RectCandidate> candidates=new ArrayList<>();
        int minDim=Math.max(18,w/32),maxDim=Math.max(120,w/4);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int idx=y*w+x;
            if(mask[idx]!=1)continue;
            int head=0,tail=1;queue[0]=idx;mask[idx]=2;
            int minX=x,maxX=x,minY=y,maxY=y;
            while(head<tail){
                int t=queue[head++],xx=t%w,yy=t/w;
                if(xx<minX)minX=xx;if(xx>maxX)maxX=xx;
                if(yy<minY)minY=yy;if(yy>maxY)maxY=yy;
                if(xx>0){int n=t-1;if(mask[n]==1){mask[n]=2;queue[tail++]=n;}}
                if(xx+1<w){int n=t+1;if(mask[n]==1){mask[n]=2;queue[tail++]=n;}}
                if(yy>0){int n=t-w;if(mask[n]==1){mask[n]=2;queue[tail++]=n;}}
                if(yy+1<h){int n=t+w;if(mask[n]==1){mask[n]=2;queue[tail++]=n;}}
            }
            int tw=maxX-minX+1,th=maxY-minY+1;
            if(tw<minDim||th<minDim||tw>maxDim||th>maxDim*1.5)continue;
            if((float)tw/th<0.50||(float)tw/th>1.40)continue;
            if((float)tail/(tw*th)<0.28f)continue;
            candidates.add(new RectCandidate(minX,minY,tw,th,tail));
        }
        if(candidates.isEmpty())return new Detection(Collections.emptyList(),factor,0);
        // Reject outliers (text panels, small buttons, backgrounds): real tiles repeat a size.
        List<Integer> widths=new ArrayList<>(),heights=new ArrayList<>();
        for(RectCandidate c:candidates){widths.add(c.w);heights.add(c.h);}
        Collections.sort(widths);Collections.sort(heights);
        int medianW=widths.get(widths.size()/2),medianH=heights.get(heights.size()/2);
        List<RectCandidate> selected=new ArrayList<>();
        for(RectCandidate c:candidates){
            if(c.w<medianW*0.70||c.w>medianW*1.35||c.h<medianH*0.70||c.h>medianH*1.35)continue;
            selected.add(c);
        }
        selected.sort(Comparator.comparingInt((RectCandidate c)->c.y).thenComparingInt(c->c.x));
        List<Tile> result=new ArrayList<>();
        for(RectCandidate c:selected){
            boolean duplicate=false;
            for(Tile t:result)if(Math.abs(t.centerX()-(c.x+c.w/2))<c.w*0.3 && Math.abs(t.centerY()-(c.y+c.h/2))<c.h*0.3){duplicate=true;break;}
            if(duplicate)continue;
            float[] signature=signature(px,w,h,c);
            result.add(new Tile(result.size(),c.x,c.y,c.w,c.h,signature));
        }
        return new Detection(result,factor,candidates.size());
    }

    private static float[] signature(int[] pixels,int width,int height,RectCandidate c) {
        float[] out=new float[S*S*4];
        // Crop away borders/shadow, matching the symbol rather than tile outlines.
        for(int gy=0;gy<S;gy++)for(int gx=0;gx<S;gx++){
            int x=Math.min(width-1,c.x+Math.round(c.w*(0.15f+0.70f*(gx+0.5f)/S)));
            int y=Math.min(height-1,c.y+Math.round(c.h*(0.14f+0.69f*(gy+0.5f)/S)));
            int rgb=pixels[y*width+x];
            float r=Color.red(rgb)/255f,g=Color.green(rgb)/255f,b=Color.blue(rgb)/255f;
            float low=Math.min(r,Math.min(g,b));
            float dark=Math.max(0f,(0.77f-low)/0.77f);
            float red=Math.max(0f,r-Math.max(g,b)-0.04f);
            float green=Math.max(0f,g-Math.max(r,b)-0.04f);
            float blue=Math.max(0f,b-Math.max(r,g)-0.04f);
            int i=(gy*S+gx)*4;
            out[i]=Math.min(1,dark);out[i+1]=Math.min(1,red*2f);
            out[i+2]=Math.min(1,green*2f);out[i+3]=Math.min(1,blue*2f);
        }
        return out;
    }

    private static final class RectCandidate {
        final int x,y,w,h,area;
        RectCandidate(int x,int y,int w,int h,int area){this.x=x;this.y=y;this.w=w;this.h=h;this.area=area;}
    }
}
