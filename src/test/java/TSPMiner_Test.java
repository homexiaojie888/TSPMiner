import HUSP_SP.HUSP_SP_Algo;

import java.io.IOException;

/***
 * input 文件中的项必须是增长序排列的，否则会出问题
 */
public class TSPMiner_Test {

    public static void main(String[] args) throws IOException {

        double minUtilityRatio = 0.9;
//        BIBLE  Kosarak10k  Leviathan   SIGN    Yoochoose   DataBase_USpan  sequence1
//        String input = "./datasets/" + "BIBLE" + ".txt";
        String input = "./datasets/" + "sequence1" + ".txt";
        String output = "tspminer.txt";

        // 初始化
        TSPMiner_Algo tspMinerAlgo = new TSPMiner_Algo(input, minUtilityRatio, output);
        //挖
        tspMinerAlgo.runAlgo();

        tspMinerAlgo.printStatistics();
    }

}
