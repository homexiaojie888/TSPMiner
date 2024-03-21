import java.util.Arrays;
import java.util.BitSet;

public class ULinkList {
    /** contain items and their utilities in original transaction.
     * can be replaced by Transaction class which contains transactionUtility and sid??? */
    UItem[] seq;//item index & utility index
    int[] remainingUtility;//remaining-utility index

    BitSet itemSetIndex;//element-index
    int [] header;//item-indices table
    Integer [][] headerIndices;//item-indices table

    public ULinkList(){}

    public int length() {
        return seq.length;
    }

    public int itemName(int ind) {
        return seq[ind].itemName();
    }

    public int utility(int ind) {
        return seq[ind].utility();
    }

    public int remainUtility(int ind) {
        return remainingUtility[ind];
    }

    public void setRemainUtility(int ind, int remainUtility) {
         this.remainingUtility[ind] = remainUtility;
    }

    public int headerLength() {
        return header.length;
    }
    //？？？？？？？？？？？？？？？？？
    public int nextItemsetPos(int ind) {
        return itemSetIndex.nextSetBit(ind + 1);
    }
    //？？？？？？？？？？？？？？？？？
    public int whichItemset(int ind) {
        return itemSetIndex.previousSetBit(ind);
    }


    public Integer[] getItemIndices(int item){
        int i = Arrays.binarySearch(header, item);
        if(i < 0)
            return null;
        return headerIndices[i];
    }


    public String toString() {
        String seqString = "";
        seqString += Arrays.toString(seq) + "\n";
        seqString += Arrays.toString(remainingUtility) + "\n";

        String header = "";
        int i = 0;
        for (Integer[] headerIndex : this.headerIndices) {
            header += this.header[i++] + "->";
            header += Arrays.toString(headerIndex) + ", ";
        }

        return "HUSP_USLL.SeqInfo{" +
                "seq=" + seqString +
                "headTable=" + header + "\n" +
                "temSetIndex=" + itemSetIndex.toString() +
                '}';
    }
}
