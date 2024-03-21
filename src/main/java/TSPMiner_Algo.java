import com.google.common.base.Joiner;

import java.io.*;
import java.util.*;


public class TSPMiner_Algo {
    protected double threshold;
    protected String pathname;
    protected double minUtility;
    protected long huspNum;
    protected long candidateNum;
    protected boolean isDebug;
//    protected ArrayList<String> patterns;
    protected Map<String,Integer> mapPattern2Util;
    protected boolean isWriteToFile;
    protected String output;
    protected long currentTime;
    protected boolean[] isRemove;
    boolean DBUpdated;
    protected BufferedWriter writer;
    ULinkList[] uLinkListDB;
    ArrayList<Integer> prefix;
    protected Boolean firstPEU;
    protected class LastId {
        public int swu;
        public ULinkList uLinkList;

        public LastId(int swu, ULinkList uLinkList) {
            this.swu = swu;
            this.uLinkList = uLinkList;
        }
    }

    //初始化
    public TSPMiner_Algo(String pathname, double threshold, String output) {
        this.pathname = pathname;
        this.threshold = threshold;
        this.output = output;

        huspNum = 0;
        candidateNum = 0;

        isDebug = false;
        isWriteToFile = true;

    }
    public void runAlgo() throws IOException {

        if (isWriteToFile){
//            patterns = new ArrayList<>();
            mapPattern2Util=new LinkedHashMap<>();
        }
        currentTime = System.currentTimeMillis();

        // reset maximum memory
        MemoryLogger.getInstance().reset();
        /***
         * 计算数据库的总效用U(D)
         * 构建DB的seq-array（uLinkListDB）（去掉不满足条件的单项序列IIP）
         */
        loadDB(pathname);

        MemoryLogger.getInstance().checkMemory();

        firstUSpan();

        MemoryLogger.getInstance().checkMemory();

        if (isWriteToFile){
            writeToFile();
        }
    }

    /***
     *
     * @param fileName
     * @throws IOException
     * 存数据到rawDB,获取minutil,计算单个序列的SWU到mapItemToSWU
     * 创建seq-array
     */
    void loadDB(String fileName) throws IOException {

        /***
         * 存数据到rawDB
         * 计算数据库的效用U(D)
         * 计算单个序列的SWU到mapItemToSWU
         */
        Map<Integer, Integer> mapItemToSWU = new HashMap<>();
        ArrayList<UItem[]> rawDB = new ArrayList<>();
        Long totalUtil = 0L;
        BufferedReader myInput = null;
        String thisLine;
        try {
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
            while ((thisLine = myInput.readLine()) != null) {
                //防止重复统计SWU
                HashSet<Integer> checkedItems = new HashSet<>();
                String str[] = thisLine.trim().split(" -2 ");
                String tokens[]=str[0].split(" ");
                int seqLen=tokens.length-1;
                UItem[] uItems = new UItem[seqLen];

                String SU_str = str[1];
                int SU = Integer.parseInt(SU_str.substring(SU_str.indexOf(":") + 1));
                totalUtil += SU;

                for (int i = 0; i < uItems.length; i++) {
                    String currentToken = tokens[i];
                    if (currentToken.charAt(0) != '-') {

                        Integer item = Integer.parseInt(currentToken.trim().substring(0, currentToken.trim().indexOf("[")));
                        Integer utility = Integer.parseInt(currentToken.trim().substring(currentToken.trim().indexOf("[") + 1, currentToken.trim().indexOf("]")));

                        uItems[i] = new UItem(item, utility);

                        if (!checkedItems.contains(item)) {
                            checkedItems.add(item);
                            Integer SWU = mapItemToSWU.get(item);
                            SWU = (SWU == null) ? SU : SWU + SU;
                            mapItemToSWU.put(item, SWU);
                        }
                    } else {
                        uItems[i] = new UItem(-1, -1);
                    }
                }

                rawDB.add(uItems);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }
        minUtility = totalUtil * threshold;
        MemoryLogger.getInstance().checkMemory();
        /***
         * 构建DB的seq-array（uLinkListDB）（去掉不满足条件的单项序列）
         */
        ArrayList<ULinkList> uLinkListDBs = new ArrayList<>();
        //只用来统计
        int maxItemName = 0;
        int maxSequenceLength = 0;

        Iterator<UItem[]> listIterator = rawDB.iterator();
        while (listIterator.hasNext()) {
            UItem[] uItems = listIterator.next();
            //item array & utility array
            ArrayList<UItem> newItems = new ArrayList<>();
            //索引计数
            int seqIndex = 0;
            //item---index(item-indices table)
            HashMap<Integer, ArrayList<Integer>> tempHeader = new HashMap<>();
            //element-index（只存储序列当中项集开始的索引，第一个项集索引默认为0不存）
            BitSet tempItemSetIndices = new BitSet(uItems.length);
            for (UItem uItem : uItems) {

                int item = uItem.itemName();

                if (item != -1) {
                    //IIP去掉low swu的单项
                    if (Double.compare(mapItemToSWU.get(item),minUtility)>=0){
                        newItems.add(uItem);
                        if (item > maxItemName) {
                            maxItemName = item;
                        }
                        if (tempHeader.containsKey(item)) {
                            tempHeader.get(item).add(seqIndex);
                        }else {
                            ArrayList<Integer> list = new ArrayList<>();
                            list.add(seqIndex);
                            tempHeader.put(item, list);
                        }
                        seqIndex++;
                    }

                }else{
                    if (seqIndex!=0){
                        tempItemSetIndices.set(seqIndex);
                    }

                }

            }
            int size = newItems.size();
            if (size > 0) {
                if (size > maxSequenceLength) {
                    maxSequenceLength = size;
                }
                ULinkList uLinkList = new ULinkList();
                uLinkList.seq = newItems.toArray(new UItem[size]);
                uLinkList.remainingUtility = new int[size];
                int remainingUtility = 0;
                for (int i = uLinkList.length() - 1; i >= 0; --i) {
                    uLinkList.setRemainUtility(i, remainingUtility);
                    remainingUtility += uLinkList.utility(i);
                }

                uLinkList.itemSetIndex = tempItemSetIndices;
                uLinkList.header = new int[tempHeader.size()];
                uLinkList.headerIndices = new Integer[tempHeader.size()][];
                int hIndex = 0;
                for (Map.Entry<Integer, ArrayList<Integer>> entry : tempHeader.entrySet()) {
                    uLinkList.header[hIndex++] = entry.getKey();
                }
                Arrays.sort(uLinkList.header);
                for (int i = 0; i < uLinkList.header.length; i++) {
                    int cItem = uLinkList.header[i];
                    ArrayList<Integer> indices = tempHeader.get(cItem);
                    uLinkList.headerIndices[i] = indices.toArray(new Integer[indices.size()]);
                }

                uLinkListDBs.add(uLinkList);
            }
        }
        prefix = new ArrayList<>(maxSequenceLength);
        isRemove = new boolean[maxItemName + 1];
        uLinkListDB =  uLinkListDBs.toArray(new ULinkList[uLinkListDBs.size()]);
        MemoryLogger.getInstance().checkMemory();

    }


    /**
     * Write to file
     */
    protected void writeToFile() throws IOException {
//        Collections.sort(patterns);
        writer=new BufferedWriter(new FileWriter(output));
//        for (int i = 0; i < patterns.size(); i++) {
//            writer.write(patterns.get(i));
//            writer.newLine();
//
//        }
        for (String seq:mapPattern2Util.keySet()) {
            writer.write(seq+" : "+mapPattern2Util.get(seq));
            writer.newLine();

        }
        writer.flush();
//        WriteFile.WriteFileByCharBuffer(this.output, patterns);
    }

    /**
     * First USpan
     */
     void firstUSpan() throws IOException {

         //计算 1-sequence 的TRSU
         HashMap<Integer, Integer> mapItem2TRSU = new HashMap<>();
         for (ULinkList uLinkList : uLinkListDB) {
             for (int item : uLinkList.header) {
                 Integer itemIndex_1 = uLinkList.getItemIndices(item)[0];
                 int trsu = uLinkList.utility(itemIndex_1) + uLinkList.remainUtility(itemIndex_1);
                 int TRSU = mapItem2TRSU.getOrDefault(item, 0);
                 mapItem2TRSU.put(item, trsu + TRSU);
             }
         }

         for (Map.Entry<Integer, Integer> entry : mapItem2TRSU.entrySet()) {

             if (Double.compare(entry.getValue(),minUtility) >= 0) {

                 candidateNum += 1;
                 int addItem = entry.getKey();
//                 System.out.println("current candidate:" +addItem);
                 /***
                  * 计算 1-sequence 的utility
                  * 计算 1-sequence 的PEU
                  * 计算 1-sequence 的seqPro（seq-array + Extension-list）
                  */
                 int sumUtility = 0;
                 int PEU = 0;
                 ArrayList<ProjectULinkList> newProjectULinkListDB = new ArrayList<>();

                 for (ULinkList uLinkList : uLinkListDB) {
                     Integer[] itemIndices = uLinkList.getItemIndices(addItem);
                     int utility = 0;
                     int peu = 0;
                     ArrayList<UPosition> newUPositions = new ArrayList<>();
                     if (itemIndices == null)
                     {
                         continue;
                     }
                     for (int index : itemIndices) {
                         int curUtility = uLinkList.utility(index);
                         utility = Math.max(utility, curUtility);
                         //计算PEU
                         peu = Math.max(peu, getUpperBound(uLinkList, index, curUtility));
                         newUPositions.add(new UPosition(index, curUtility));
                     }

                     // update the sumUtility and upper-bound
                     if (newUPositions.size() > 0) {
                         newProjectULinkListDB.add(new ProjectULinkList(uLinkList, newUPositions, utility));
                         sumUtility += utility;
                         PEU += peu;
                     }

                 }

                 if (Double.compare(sumUtility,minUtility)>=0){
                     huspNum += 1;
//                     patterns.add(String.valueOf(entry.getKey()));
                     mapPattern2Util.put(String.valueOf(entry.getKey()),sumUtility);
                 }
                 // PEU >= minUtility
                 if (Double.compare(PEU,minUtility)>=0){
                     prefix.add(addItem);
                     runHUSPspan(newProjectULinkListDB);
                     prefix.remove(prefix.size() - 1);
                 }
             }
         }

         MemoryLogger.getInstance().checkMemory();
    }


    /**
     * Run USpan algorithm
     *
     * @param projectULinkListDB
     */
    protected void runHUSPspan(ArrayList<ProjectULinkList> projectULinkListDB) throws IOException {
        //计算 （n+1）-sequence 的RSU
        HashMap<Integer, LastId> mapItemToRSU = new HashMap<>();
        for (ProjectULinkList projectULinkList : projectULinkListDB) {
            ULinkList uLinkList = projectULinkList.getULinkList();
            ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
            //获取前缀n-sequence的PEU
            int rsu = getPEUofTran(projectULinkList);
            UPosition uPosition = uPositions.get(0);
            //从第一个扩展位置开始一直到最后都是可扩展的item
            for (int i = uPosition.index() + 1; i < uLinkList.length(); ++i) {
                int item = uLinkList.itemName(i);
                if (!isRemove[item]) {//？？？？？
                    LastId lastId = mapItemToRSU.get(item);
                    if (lastId == null) {
                        mapItemToRSU.put(item, new LastId(rsu, uLinkList));
                    } else {
                        if (lastId.uLinkList != uLinkList) {
                            lastId.swu += rsu;
                            lastId.uLinkList = uLinkList;
                        }
                    }
                }
            }
        }
        // remove the item has low RSU
        //IIP
        for (Map.Entry<Integer, LastId> entry : mapItemToRSU.entrySet()) {
            int item = entry.getKey();
            int SWU = entry.getValue().swu;
            if (Double.compare(SWU,minUtility) < 0) {
                isRemove[item] = true;//可见性会不会有问题？？？
                DBUpdated = true;
            }
        }
        //重新计算remaining utility
        removeItem(projectULinkListDB);

        //计算 （n+1）-sequence 的TRSU
        HashMap<Integer, LastId> mapIitem2TRSU = getmapIitem2TRSU(projectULinkListDB);
        iConcatenation(projectULinkListDB, mapIitem2TRSU);

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();

        //计算 （n+1）-sequence 的TRSU
        HashMap<Integer, LastId> mapSitem2TRSU = getMapSitem2TRSU(projectULinkListDB);
        sConcatenation(projectULinkListDB, mapSitem2TRSU);


        for (Map.Entry<Integer, LastId> entry : mapItemToRSU.entrySet()) {
            int item = entry.getKey();
            int swu = entry.getValue().swu;
            if (Double.compare(swu,minUtility) < 0) {
                isRemove[item] = false;//恢复原来的设置
                DBUpdated = true;
            }
        }
        //恢复原来的remaining utility
        removeItem(projectULinkListDB);
    }

    /**
     * items appear after prefix in the same itemset in difference sequences;
     * SWU = the sum these sequence utilities for each item as their upper bounds under prefix
     * should not add sequence utility of same sequence more than once
     *
     * @param projectedDB: database
     * @return upper-bound
     */
    protected HashMap<Integer, LastId> getmapIitem2TRSU(ArrayList<ProjectULinkList> projectedDB) {
        HashMap<Integer, LastId> mapIitem2TRSU = new HashMap<>();
        for (ProjectULinkList projectULinkList : projectedDB) {
            ULinkList uLinkList = projectULinkList.getULinkList();
            ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
            //获取前缀n-sequence的PEU
            int rsu = getPEUofTran2(projectULinkList);//新的PEU（因为删除了某些low RSU的项）
            for (UPosition uPosition : uPositions) {
                int nextItemsetPos = uLinkList.nextItemsetPos(uPosition.index());
                if (nextItemsetPos == -1){
                    nextItemsetPos = uLinkList.length();
                }
                int middleU=0;
                for (int index = uPosition.index() + 1; index < nextItemsetPos; ++index) {
                    int item = uLinkList.itemName(index);
                    if (!isRemove[item]) {
                        // only find items in the same itemset, else break
                        LastId lastId = mapIitem2TRSU.get(item);
                        if (lastId == null) {
//                            int middleU = get_middleU(projectULinkList, index);
                            mapIitem2TRSU.put(item, new LastId(rsu - middleU, uLinkList));
                        } else {
                            // should not add sequence utility of same sequence more than once
                            // since many UPosition may have same item, [a b] [a b]
                            if (lastId.uLinkList != uLinkList) {
//                                int middleU = get_middleU(projectULinkList, index);
                                lastId.swu += (rsu - middleU);
                                lastId.uLinkList = uLinkList;
                            }
                        }
                        int itemUtil=uLinkList.utility(index);
                        middleU+=itemUtil;
                    }

                }
            }
        }

        return mapIitem2TRSU;
    }

//    private int get_middleU(ProjectULinkList projectULinkList, int index) {
//        if (!firstPEU) {
//            return 0;
//        }
//        ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
//        ULinkList uLinkList = projectULinkList.getULinkList();
//        int middleU = 0;
//        int pIndex = 0;
//        for (UPosition position : uPositions) {
//            if (position.index() < index) {
//                pIndex = position.index();
//            } else {
//                break;
//            }
//        }
//        middleU = uLinkList.remainUtility(pIndex) - uLinkList.remainUtility(index - 1);
//        return middleU >= 0 ? middleU : 0;
//    }

    private int get_NUB1(ProjectULinkList projectULinkList, int index) {

        ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
        ULinkList uLinkList = projectULinkList.getULinkList();
        int cur_peu=0;
        int NUB1=0;
        for (UPosition uPosition : uPositions) {
            if (uPosition.index() < index) {
                cur_peu = uPosition.utility() + uLinkList.remainUtility(uPosition.index());
                NUB1=Math.max(NUB1,cur_peu-(uLinkList.remainUtility(uPosition.index()) - uLinkList.remainUtility(index - 1)));
            }else {
                break;
            }
        }
        return NUB1 >= 0 ? NUB1 : 0;
    }
    private int get_NUB2(ProjectULinkList projectULinkList, int index, int localSWU) {
        ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
        ULinkList uLinkList = projectULinkList.getULinkList();
        int min_middleU = Integer.MAX_VALUE;
        int max_peu = 0;
        for (UPosition uPosition : uPositions) {
            if (uPosition.index() < index) {
                max_peu = Math.max(max_peu,uPosition.utility() + uLinkList.remainUtility(uPosition.index()));
                min_middleU = Math.min(min_middleU,(uLinkList.remainUtility(uPosition.index()) - uLinkList.remainUtility(index - 1)));
            } else {
                break;
            }
        }

        int NUB2=max_peu-min_middleU;
        return NUB2 < localSWU ? NUB2 : localSWU;
    }
    /**
     * items appear from the next itemset after prefix in difference sequences;
     * SWU = sum these sequence utilities for each item as their upper bounds under prefix
     * should not add sequence utility of same sequence more than once
     *
     * @param projectedDB: database
     * @return upper-bound
     */
    protected HashMap<Integer, LastId> getMapSitem2TRSU(ArrayList<ProjectULinkList> projectedDB) {
        HashMap<Integer, LastId> mapSitem2TRSU = new HashMap<Integer, LastId>();
        for (ProjectULinkList projectULinkList : projectedDB) {
            ULinkList uLinkList = projectULinkList.getULinkList();
            ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
            int localSwu = getPEUofTran2(projectULinkList);
            int addItemPos = uLinkList.nextItemsetPos(uPositions.get(0).index());
            // the second one is to traverse from the position of next itemset of addItem to
            // the end of transaction, which may repeat adding swu of item in the same transaction.
            for (int index = addItemPos; index < uLinkList.length() && index != -1; ++index) {
                int item = uLinkList.itemName(index);
                if (!isRemove[item]) {
                    LastId lastId = mapSitem2TRSU.get(item);
                    if (lastId == null) {
//                        int middleU = get_middleU(projectULinkList, index);
//                        mapSitem2TRSU.put(item, new LastId(localSwu - middleU, uLinkList));
//                        int NUB1=get_NUB1(projectULinkList, index);
//                        mapSitem2TRSU.put(item, new LastId(NUB1, uLinkList));
                        int NUB2=get_NUB2(projectULinkList, index,localSwu);
                        mapSitem2TRSU.put(item, new LastId(NUB2, uLinkList));
                    } else {
                        // should not add sequence utility of same sequence more than once
                        if (lastId.uLinkList != uLinkList) {
//                            int middleU = get_middleU(projectULinkList, index);
//                            lastId.swu += localSwu - middleU;
//                            lastId.uLinkList = uLinkList;
//                            int NUB1=get_NUB1(projectULinkList, index);
//                            lastId.swu += NUB1;
//                            lastId.uLinkList = uLinkList;
                            int NUB2=get_NUB2(projectULinkList, index,localSwu);
                            lastId.swu += NUB2;
                            lastId.uLinkList = uLinkList;
                        }else{
//                            int NUB1=get_NUB1(projectULinkList, index);
//                            lastId.swu = Math.max(lastId.swu,NUB1);
                            int NUB2=get_NUB2(projectULinkList, index,localSwu);
                            lastId.swu = Math.max(lastId.swu,NUB2);
                        }
                    }
                }
            }
        }
        return mapSitem2TRSU;
    }

    /**
     * @param projectedDB:              database
     * @param mapIitem2TRSU: upper-bound of addItem
     */
    protected void iConcatenation(ArrayList<ProjectULinkList> projectedDB, HashMap<Integer, LastId> mapIitem2TRSU) throws IOException {
        for (Map.Entry<Integer, LastId> entry : mapIitem2TRSU.entrySet()) {
            if (Double.compare(entry.getValue().swu,minUtility) >= 0) {
                /***
                 * 计算 (n+1)-sequence 的utility
                 * 计算 (n+1)-sequence 的PEU
                 * 计算 (n+1)-sequence 的seqPro（seq-array + Extension-list）
                 */
                candidateNum += 1;
                int addItem = entry.getKey();
//                System.out.println("current candidaite:"+Joiner.on(" ").join(prefix)+" "+entry.getKey());
                int sumUtility = 0;
                int PEU = 0;
                ArrayList<ProjectULinkList> newProjectULinkListDB = new ArrayList<>();

                for (ProjectULinkList projectULinkList : projectedDB) {
                    ULinkList uLinkList = projectULinkList.getULinkList();
                    ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
                    Integer[] itemIndices = uLinkList.getItemIndices(addItem);

                    int utility = 0;
                    int peu = 0;
                    ArrayList<UPosition> newUPositions = new ArrayList<>();
                    int addItemInd;
                    if (itemIndices == null){
                        continue;
                    }
                    for (int i = 0, j = 0; i < uPositions.size() && j < itemIndices.length; ) {
                        addItemInd = itemIndices[j];
                        UPosition uPosition = uPositions.get(i);
                        int uPositionItemsetIndex = uLinkList.whichItemset(uPosition.index());
                        int addItemItemsetIndex = uLinkList.whichItemset(addItemInd);

                        if (uPositionItemsetIndex == addItemItemsetIndex) {
                            int curUtility = uLinkList.utility(addItemInd) + uPosition.utility();
                            utility = Math.max(utility, curUtility);
                            peu = Math.max(peu, getUpperBound(uLinkList, addItemInd, curUtility));
                            newUPositions.add(new UPosition(addItemInd, curUtility));

                            i++;
                            j++;
                        } else if (uPositionItemsetIndex > addItemItemsetIndex) {
                            j++;
                        } else if (uPositionItemsetIndex < addItemItemsetIndex) {
                            i++;
                        }
                    }

                    if (newUPositions.size() > 0) {
                        newProjectULinkListDB.add(new ProjectULinkList(uLinkList, newUPositions, utility));
                        sumUtility += utility;
                        PEU += peu;
                    }

                }
                if (Double.compare(sumUtility,minUtility) >= 0) {
                    huspNum++;
//                    patterns.add(Joiner.on(" ").join(prefix)+" "+entry.getKey());
                    mapPattern2Util.put(Joiner.on(" ").join(prefix)+" "+entry.getKey(),sumUtility);
                }
                if (Double.compare(PEU,minUtility) >= 0) {
                    prefix.add(addItem);
                    runHUSPspan(newProjectULinkListDB);
                    prefix.remove(prefix.size() - 1);
                }
            }
        }
    }

    /**
     * S-concatenation
     * <p>
     * each addItem (candidate item) has multiple index in the sequence
     * each index can be s-concatenation with multiple UPositions before this index
     * but these UPositions s-concatenation with the same index are regarded as one sequence
     * so for each index, choose the UPosition with maximal utility
     * <p>
     * candidate sequences are evaluated by (prefix utility + remaining utility) (PU)
     *
     * @param projectedDB:              database
     * @param mapSitem2TRSU: upper-bound of addItem
     */
    protected void sConcatenation(ArrayList<ProjectULinkList> projectedDB, HashMap<Integer, LastId> mapSitem2TRSU) throws IOException {
        for (Map.Entry<Integer, LastId> entry : mapSitem2TRSU.entrySet()) {
            if (Double.compare(entry.getValue().swu,minUtility) >= 0) {
                /***
                 * 计算 (n+1)-sequence 的utility
                 * 计算 (n+1)-sequence 的PEU
                 * 计算 (n+1)-sequence 的seqPro（seq-array + Extension-list）
                 */
                candidateNum += 1;
                int addItem = entry.getKey();
//                System.out.println("current candidaite:"+Joiner.on(" ").join(prefix)+" "+entry.getKey());
                int sumUtility = 0;
                int PEU = 0;
                ArrayList<ProjectULinkList> newProjectULinkListDB = new ArrayList<ProjectULinkList>();

                for (ProjectULinkList projectULinkList : projectedDB) {
                    ULinkList uLinkList = projectULinkList.getULinkList();
                    ArrayList<UPosition> uPositions = projectULinkList.getUPositions();

                    Integer[] itemIndices = uLinkList.getItemIndices(addItem);
                    if (itemIndices == null)  // addItem should be in the transaction
                        continue;
                    int utility = 0;
                    int peu = 0;
                    ArrayList<UPosition> newUPositions = new ArrayList<UPosition>();

                    /*
                     * each addItem has multiple index (will become new UPosition) in the
                     * sequence, each index (will become new UPosition) can be s-concatenation
                     * with multiple UPositions (contain position of last item in prefix)
                     * before this index, but multiple UPositions s-concatenation with the same
                     * index are regarded as one new UPosition, so for each index, choose the
                     * maximal utility of UPositions before this index as prefix utility for
                     * this index.
                     */
                    int maxPositionUtility = 0;  // choose the maximal utility of UPositions
                    int uPositionNextItemsetPos = -1;

                    int addItemInd;
                    for (int i = 0, j = 0; j < itemIndices.length; j++) {
                        addItemInd = itemIndices[j];
                        for (; i < uPositions.size(); i++) {
                            uPositionNextItemsetPos = uLinkList.nextItemsetPos(uPositions.get(i).index());

                            // 1. next itemset should be in transaction
                            // 2. addItem should be after or equal to the next itemset of UPosition
                            //后缀位置确定，找前缀的最大效用
                            if (uPositionNextItemsetPos != -1 && uPositionNextItemsetPos <= addItemInd) {
                                if (maxPositionUtility < uPositions.get(i).utility()){
                                    maxPositionUtility = uPositions.get(i).utility();
                                }
                            } else {
                                break;
                            }
                        }

                        // maxPositionUtility is initialized outside the loop,
                        // will be the same or larger than before
                        if (maxPositionUtility != 0) {
                            int curUtility = uLinkList.utility(addItemInd) + maxPositionUtility;
                            newUPositions.add(new UPosition(addItemInd, curUtility));
                            utility = Math.max(utility, curUtility);
                            peu = Math.max(peu, getUpperBound(uLinkList, addItemInd, curUtility));
                        }
                    }

                    // if exist new positions, update the sumUtility and upper-bound
                    if (newUPositions.size() > 0) {
                        newProjectULinkListDB.add(
                                new ProjectULinkList(uLinkList, newUPositions, utility));

                        sumUtility += utility;
                        PEU += peu;
                    }

                }
                if (Double.compare(sumUtility,minUtility) >= 0) {
                    huspNum++;
//                    patterns.add(Joiner.on(" ").join(prefix)+" -1 "+entry.getKey());
                    mapPattern2Util.put(Joiner.on(" ").join(prefix)+" -1 "+entry.getKey(),sumUtility);
                }
                if (Double.compare(PEU,minUtility) >= 0) {
                    prefix.add(-1);
                    prefix.add(addItem);
                    runHUSPspan(newProjectULinkListDB);
                    prefix.remove(prefix.size() - 1);
                    prefix.remove(prefix.size() - 1);
                }
            }
        }
    }

    /**
     *
     * Example for check of S-Concatenation
     * <[(3:25)], [(1:32) (2:18) (4:10) (5:8)], [(2:12) (3:40) (5:1)]> 146
     * Pattern: 3 -1 2
     * UPositions: (3:25), (3:40)
     * For
     * addItemInd = firstPosOfItemByName = (2:18)
     *   UPosition = (3:25)
     *   uPositionNextItemsetPos = [(1:32) (2:18) (4:10) (5:8)]
     *   maxPositionUtility = 25
     *   UPosition = (3:40)
     *   uPositionNextItemsetPos = -1 -> break
     * newUPosition = 25 + 18
     * addItemInd = (2:12)
     *   UPosition = (3:40)
     *   uPositionNextItemsetPos = -1 -> break
     * newUPosition = 25 + 12
     * End
     */

    /**
     * PEU
     *
     * @param uLinkList
     * @param index
     * @param curUtility
     * @return
     */
    protected int getUpperBound(ULinkList uLinkList, int index, int curUtility) {
        return curUtility + uLinkList.remainUtility(index);
    }

    /**
     * PEU
     *
     * @param projectULinkList
     * @return
     */
    protected int getPEUofTran(ProjectULinkList projectULinkList) {
        ULinkList uLinkList = projectULinkList.getULinkList();
        ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
        int peu = 0;
        for (UPosition uPosition : uPositions) {
            peu = Math.max(peu,uPosition.utility() + uLinkList.remainUtility(uPosition.index()));
        }
        return peu;
    }
    protected int getPEUofTran2(ProjectULinkList projectULinkList) {
        ULinkList uLinkList = projectULinkList.getULinkList();
        ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
        int peu = 0;
        byte count = 0;
        for (UPosition uPosition : uPositions) {
            count++;
            int localPeu = uPosition.utility() + uLinkList.remainUtility(uPosition.index());
            if (localPeu > peu) {
                peu = localPeu;
                firstPEU = true;
                if (count != 1) {
                    firstPEU = false;
//                    break;//应该注释掉
                }
            }
        }
        return peu;
    }
    /**
     * Funtion of removeItem, using the position of remaining utility
     * used for mapItemSwu(swu = position.utility + position.remaining utility)
     * 只改remaining utility？？？？？
     * @param projectULinkListDB
     */
    protected void removeItem(ArrayList<ProjectULinkList> projectULinkListDB) {
        if(!DBUpdated)
        {
            return;
        }
        for (ProjectULinkList projectULinkList : projectULinkListDB) {
            ULinkList uLinkList = projectULinkList.getULinkList();
            ArrayList<UPosition> uPositions = projectULinkList.getUPositions();
            int positionIndex = uPositions.get(0).index();
            int remainingUtility = 0;

            for (int i = uLinkList.length() - 1; i >= positionIndex; --i) {
                int item = uLinkList.itemName(i);

                if (!isRemove[item]) {
                    uLinkList.setRemainUtility(i, remainingUtility);
                    remainingUtility += uLinkList.utility(i);
                } else {  // ??? can be delete
                    // no, someone >= minUtility should reset remaining utility
                    uLinkList.setRemainUtility(i, remainingUtility);
                }
            }
        }
        DBUpdated = false;
    }

    @Override
    public String toString() {
        return "HuspMiner{" +
                "threshold= " + threshold +
                ", DB= '" + pathname.split("/")[pathname.split("/").length - 1] +
                ", minUtility= " + minUtility +
                ", huspNum= " + huspNum +
                ", candidateNum= " + candidateNum +
                '}';
    }


    /**
     * Print statistics about the algorithm execution
     */
    public void printStatistics()  {
        System.out.println("=============  HUSPull_PLUS ALGORITHM - STATS ============");
        System.out.println("minUtilRatio: " + String.format("%.5f", threshold));
        System.out.println(" minUtil: " + minUtility);
        System.out.println("time: " + (System.currentTimeMillis() - currentTime)/1000.0 + " s");
        System.out.println("Max memory: " + MemoryLogger.getInstance().getMaxMemory() + "  MB");
        System.out.println("HUSPs: " + huspNum);
        System.out.println("Candidates: " + candidateNum);
    }


}
