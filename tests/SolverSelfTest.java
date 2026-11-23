import java.util.*;
import dev.infend.vitasolver.core.Tile;
import dev.infend.vitasolver.core.MahjongSolver;

/** Can be executed with plain Java 17 even without Android SDK. */
public class SolverSelfTest {
    static Tile tile(int id,int x,int type){
        float[] f=new float[40];f[type]=1;return new Tile(id,x,100,50,64,f);
    }
    public static void main(String[] args){
        List<Tile> line=List.of(tile(0,0,0),tile(1,50,1),tile(2,100,0),tile(3,150,1));
        require(MahjongSolver.isFree(line.get(0),line),"left exposed");
        require(!MahjongSolver.isFree(line.get(1),line),"inside locked");
        require(!MahjongSolver.isFree(line.get(2),line),"inside locked");
        require(MahjongSolver.isFree(line.get(3),line),"right exposed");
        require(MahjongSolver.distance(line.get(0).signature,line.get(2).signature)==0,"exact same");
        require(MahjongSolver.distance(line.get(0).signature,line.get(1).signature)>0.3,"different symbols");
        var move=MahjongSolver.findMove(line,0.2f,MahjongSolver.emptyBanList());
        require(move==null,"No matching pair currently free");
        List<Tile> open=List.of(tile(0,0,0),tile(1,50,1),tile(2,100,0));
        var choice=MahjongSolver.findMove(open,0.2f,MahjongSolver.emptyBanList());
        require(choice!=null && choice.first.id==0 && choice.second.id==2,"Two free equal edge pieces");
        Set<String> banned=MahjongSolver.emptyBanList();banned.add(MahjongSolver.pairKey(choice.first,choice.second));
        require(MahjongSolver.findMove(open,0.2f,banned)==null,"Ban failed candidate");
        System.out.println("SolverSelfTest: 9 assertions passed");
    }
    private static void require(boolean c,String message){if(!c)throw new AssertionError(message);}
}
