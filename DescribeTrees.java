/**
 * Random-Forest implementation in JAVA
 * @Author EDGIS
 * @Contact guoxianwhu@foxmail.com
 */
package routing.RandomForest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import static jdk.nashorn.internal.objects.Global.print;

public class DescribeTrees {
    //把txt文件作为输入，导入到randomForest中
    BufferedReader br = null;
    String path;
    public DescribeTrees(){

    }
    public DescribeTrees(String path){
        this.path = path;
    }
    public int[] CreateInput2(String s){
        int[] DataPoint;
        ArrayList<Integer> spaceIndex=new ArrayList<>();
        for(int i=0;i<s.length();i++){
            if(Character.isWhitespace(s.charAt(i))){
                spaceIndex.add(i);
            }
        }
         DataPoint=new int[spaceIndex.size()-1];
        for(int i=0;i<spaceIndex.size()-1;i++){
             DataPoint[i]=Integer.parseInt(s.substring(spaceIndex.get(i)+1,spaceIndex.get(i+1)));
        }
        return DataPoint;
    }

    public ArrayList<int[]> CreateInput(String path){
        ArrayList<int[]> DataInput = new ArrayList<int[]>();
        try {
            String sCurrentLine;
            br = new BufferedReader(new FileReader(path));

            while ((sCurrentLine = br.readLine()) != null) {
                ArrayList<Integer> spaceIndex = new ArrayList<Integer>();//空格的index
                int i;
                if(sCurrentLine != null){
                    sCurrentLine = " " + sCurrentLine + " ";
                    for(i=0; i < sCurrentLine.length(); i++){
                        if(Character.isWhitespace(sCurrentLine.charAt(i)))
                            spaceIndex.add(i);
                    }
                    int[] DataPoint = new int[spaceIndex.size()-1];
                    for(i=0; i<spaceIndex.size()-1; i++){
                        String s=sCurrentLine.substring(spaceIndex.get(i)+1, spaceIndex.get(i+1));
                        if(s!=""){
                        DataPoint[i]=Integer.parseInt(s);
                        }else{
                            print("输入有错");
                        }
                    }
                    /* print DataPoint
                    for(k=0; k<DataPoint.length; k++){
                        //System.out.print("-");
                        System.out.print(DataPoint[k]);
                        System.out.print(" ");

                    }
                    **/
                    DataInput.add(DataPoint);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return DataInput;
    }
}
