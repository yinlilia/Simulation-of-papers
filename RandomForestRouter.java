package routing;

import core.*;
import routing.RandomForest.DescribeTrees;
import routing.RandomForest.MeetingProbabilitySet;
import routing.RandomForest.RandomForDijkstra;
import routing.RandomForest.RandomForest;
import util.Tuple;

import java.util.*;

public class RandomForestRouter extends ActiveRouter {
    public static final String NROF_COPIES="nrofCopies";
    public static final String PRIORITY_MSG="priorityMessages";
    public static final String PROPERTY_MSG="propertyMessages";
    public static final String RANDOMFORESTROUTER_NS="RandomForestRouter";
    public static final String MSG_COUNT_PROPERTY = RANDOMFORESTROUTER_NS + "." +
            "copies";
   /* public static final String SORTING_RESULT="soretingResult";
    public static final String ES_PATH="expected_shortest_path";
    public static final int HIGHEST_PRIORITY=1;
    public static final int SECOND_PRIORITY=2;
    public static final int THIRD_PRIORITY=3;
    public static final int FOURTH_PRIORITY=4;
    public static final int FIFTH_PRIORITY=5;
    public static final int SIXTH_PRIORITY=6;*/
    /**
     * 可以存储相遇概率的最大个数*/
    public static final String PROB_SET_MAX_SIZE_S = "probSetMaxSize";
    private static int probSetMaxSize;
    public static String propertyMessages;
    /**
     * probSetMaxSize的默认值*/
    public static final int DEFAULT_PROB_SET_MAX_SIZE = 50;
    private MeetingProbabilitySet probs;
    private Map<Integer,MeetingProbabilitySet> allProbs;
    private RandomForDijkstra dijkstra;
    private Set<String> ackedMessageIds;
    private Map<Integer,Double> costsForMessages;
    private DTNHost lastCostFrom;
    private Map<DTNHost,Set<String>> sentMessages;
    /** The alpha parameter string*/
    //public static final String ALPHA_S = "alpha";

    /** The alpha variable, default = 1;*/
    //private double alpha;
    private RandomForest rf;

    /** The default value for alpha */
    public static final double DEFAULT_ALPHA = 1.0;
    /** The duration of connection*/
    private Map<Connection,Double> encounterPos;
    protected int initialNrofCopies;
    protected int priority;
    private Double startTime;
    private Double endTime;
    public RandomForestRouter(Settings s){
        super(s);
        Settings rfSettings=new Settings(RANDOMFORESTROUTER_NS);

        Settings mpSettings = new Settings(RANDOMFORESTROUTER_NS);
        if (mpSettings.contains(PROB_SET_MAX_SIZE_S)) {
            probSetMaxSize = mpSettings.getInt(PROB_SET_MAX_SIZE_S);
        } else {
            probSetMaxSize = DEFAULT_PROB_SET_MAX_SIZE;
        }

        initialNrofCopies=rfSettings.getInt(NROF_COPIES);//Todo
        priority=(int)(Math.random()*100+1);//1~100
        encounterPos=new HashMap<>();
        train();

    }
    public RandomForestRouter(RandomForestRouter r){
        super(r);
        initialNrofCopies=r.initialNrofCopies;
        priority=(int)(Math.random()*3+1);//1~3
        //this.alpha = r.alpha;
        this.probs = new MeetingProbabilitySet(probSetMaxSize, encounterPos);
        this.allProbs = new HashMap<Integer, MeetingProbabilitySet>();
        this.dijkstra = new RandomForDijkstra(this.allProbs);
        this.ackedMessageIds = new HashSet<String>();
        this.sentMessages = new HashMap<DTNHost, Set<String>>();
        encounterPos=new HashMap<>();
        train();
    }
    private void train(){
        String trainPath = "/Users/yinlili/Downloads/one_1.5.1/routing/RandomForest/TrainData2";
        //String testPath = "/Users/yinlili/Downloads/One2/Data/Test.txt";
        int numTrees = 10;
        int categ = 0;
        DescribeTrees DT = new DescribeTrees(trainPath);
        ArrayList<int[]> Train = DT.CreateInput(trainPath);

        int trainLength = Train.get(0).length;
        for(int k=0; k<Train.size(); k++){
            if(Train.get(k)[trainLength-1] < categ)
                continue;
            else{
                categ = Train.get(k)[trainLength-1];
            }
        }
        rf = new RandomForest(numTrees, Train);
        rf.C = categ;//the num of labels
        rf.M = Train.get(0).length-1;//the num of Attr
        //属性扰动，每次从M个属性中随机选取Ms个属性，Ms = ln(m)/ln(2)
        rf.Ms = (int)Math.round(Math.log(rf.M)/Math.log(2) + 1);//随机选择的属性数量
        rf.Start2();
    }
    @Override
    public void changedConnection(Connection con){
        super.changedConnection(con);
        if(con.isUp()){
            startTime=SimClock.getTime();
            this.costsForMessages=null;
            if(con.isInitiator(getHost())){
                DTNHost otherHost=con.getOtherNode(getHost());
                MessageRouter mRouter=otherHost.getRouter();

                assert mRouter instanceof RandomForestRouter:"RandomForest only works "+
                        " with other routers of same type";
                RandomForestRouter otherRouter=(RandomForestRouter)mRouter;

                this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
                otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
                deleteAckedMessages();
                otherRouter.deleteAckedMessages();

                /* update both meeting probabilities */
                probs.updateMeetingProbFor(otherHost.getAddress(),getHost(),otherHost);
                otherRouter.probs.updateMeetingProbFor(getHost().getAddress(),getHost(),otherHost);

                this.updateTransitiveProbs(otherRouter.allProbs);
                otherRouter.updateTransitiveProbs(this.allProbs);
                this.allProbs.put(otherHost.getAddress(),
                        otherRouter.probs.replicate());
                otherRouter.allProbs.put(getHost().getAddress(),
                        this.probs.replicate());//TODO
            }
        }

        if(!con.isUp()){
            endTime=SimClock.getTime();
            double duration=(endTime-startTime)/endTime;
            if(encounterPos.get(con)!=null){
                encounterPos.put(con,(encounterPos.get(con)*startTime+duration)/endTime);
            }else{
                encounterPos.put(con,duration/endTime);
            }
        }
    }
    /**
     * Deletes the messages from the message buffer that are known to be ACKed
     */
    private void deleteAckedMessages() {
        for (String id : this.ackedMessageIds) {
            if (this.hasMessage(id) && !isSending(id)) {
                this.deleteMessage(id, false);
            }
        }
    }
    private void updateTransitiveProbs(Map<Integer,MeetingProbabilitySet> p){
        for(Map.Entry<Integer,MeetingProbabilitySet> e:p.entrySet()){
            MeetingProbabilitySet myProb=this.allProbs.get(e.getKey());
            if(myProb==null||e.getValue().getLastUpdateTime()>myProb.getLastUpdateTime() ){
                this.allProbs.put(e.getKey(),e.getValue().replicate());
            }
        }
    }

    //开始接受消息时调用
    @Override
    public int receiveMessage(Message m, DTNHost from){
        return super.receiveMessage(m,from);
    }
    //消息被成功传输之后调用
    @Override
    public Message messageTransferred(String id,DTNHost from){
        this.costsForMessages=null;
        Message msg=super.messageTransferred(id,from);
        if(isDeliveredMessage(msg)){
            this.ackedMessageIds.add(id);
        }
        return  msg;
    }
    @Override
    public boolean createNewMessage(Message msg){
        makeRoomForMessage(msg.getSize());
        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROPERTY,new Integer(initialNrofCopies));
        msg.addProperty(PRIORITY_MSG,priority);
        //msg.addProperty(SORTING_RESULT,0);
        addToMessages(msg,true);
        return true;

    }
    // 这个方法应该在每个模拟间隔上调用(至少一次)来更新传输状态。
    @Override
    public void update(){
        int sorting_result;
        List<Message> toBeTransmitted=new ArrayList<>();
        super.update();
        if(!canStartTransfer()||isTransferring()){
            return;
        }
        if(exchangeDeliverableMessages()!=null){
            return;
        }
        //TODO
        List<Message> copiesLeft=getMessagesWithCopies();
        /*for(int i=0;i<copiesLeft.size();i++){
            Message msg=copiesLeft.get(i);
            sorting_result=sortedByRandomForest(msg);
            msg.updateProperty(SORTING_RESULT,sorting_result);
        }*/
        /*for(int i=0;i<copiesLeft.size();i++){
            Message msg=copiesLeft.get(i);
            priority=(int)msg.getProperty(SORTING_RESULT);
            if(priority<=THIRD_PRIORITY){
                toBeTransmitted.add(msg);
            }
        }*/
        //tryMessagesToConnections(toBeTransmitted,getConnections());
        tryOtherMessage();


    }
    private Tuple<Message,Connection> tryOtherMessage(){
        List<Tuple<Message,Connection>> messages=new ArrayList<>();
        Collection<Message> msgCollection=getMessageCollection();
        for(Connection con:getConnections()){
            DTNHost other=con.getOtherNode(getHost());
            RandomForestRouter otherRouter=(RandomForestRouter)other.getRouter();
            Set<String> setMsgIds=this.sentMessages.get(otherRouter);

            if(otherRouter.isTransferring()){
                continue;
            }
            for(Message m:msgCollection){
                if(otherRouter.hasMessage(m.getId())||m.getHops().contains(otherRouter)){
                    continue;
                }
                if(setMsgIds!=null&&setMsgIds.contains(m.getId())){
                    continue;
                }
                messages.add(new Tuple<>(m,con));
            }
        }
        if(messages.size()==0){
            return null;
        }
        Collections.sort(messages,new RandomForestTupleComparator());
        return tryMessagesForConnected(messages);
    }
    protected List<Message> getMessagesWithCopies(){
        List<Message> list = new ArrayList<>();

        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "SnW message " + m + " didn't have " +
                    "nrof copies property!";

            list.add(m);

        }

        return list;
    }
    @Override
    protected void transferDone(Connection con){
        Integer nrofCopies;
        String msgId=con.getMessage().getId();
        DTNHost recipient=con.getOtherNode(getHost());
        Set<String> sentMsgIds = this.sentMessages.get(recipient);
        Message msg=getMessage(msgId);
        if(msg==null){
            return;
        }
        if(msg.getTo()==recipient){
            this.ackedMessageIds.add(msgId);
            this.deleteAckedMessages();
        }
        if(sentMsgIds==null){
            sentMsgIds=new HashSet<>();
            sentMessages.put(recipient,sentMsgIds);
        }
        sentMsgIds.add(msgId);
        nrofCopies=(Integer)msg.getProperty(MSG_COUNT_PROPERTY);
        nrofCopies++;
        msg.updateProperty(MSG_COUNT_PROPERTY,nrofCopies);
    }
    public double getCost(DTNHost from, DTNHost to) {
        /* check if the cached values are OK */
        if (this.costsForMessages == null || lastCostFrom != from) {
            /* cached costs are invalid -> calculate new costs */
            this.allProbs.put(getHost().getAddress(), this.probs);
            int fromIndex = from.getAddress();

            /* calculate paths only to nodes we have messages to
             * (optimization) */
            Set<Integer> toSet = new HashSet<>();
            for (Message m : getMessageCollection()) {
                toSet.add(m.getTo().getAddress());
            }

            this.costsForMessages = dijkstra.getCosts(fromIndex, toSet);
            this.lastCostFrom = from; // store source host for caching checks
        }

        if (costsForMessages.containsKey(to.getAddress())) {
            return costsForMessages.get(to.getAddress());
        }
        else {
            /* there's no known path to the given host */
            return Integer.MAX_VALUE-1;
        }
    }
    @Override
    public RandomForestRouter replicate(){
        return new RandomForestRouter(this);
    }

    private class RandomForestComparator{
        int o1,o2;
        String propertyMsg1;
        String propertyMsg2;
        public RandomForestComparator(String s1,String s2){
            this.propertyMsg1=s1;
            this.propertyMsg2=s2;
        }
        public int compare(Message msg1,Message msg2){
            int result;
            if(msg1==msg2){
                return 0;
            }
            o1=getOrder(propertyMsg1);
            o2=getOrder(propertyMsg2);
            result=o1==o2?0:(o1>o2?-1:1);
            return result;
        }
        public int getOrder(String test){
            int order=0;

            DescribeTrees DT2 = new DescribeTrees();
            int[] Test = DT2.CreateInput2(test);
            order=rf.test(Test);
            System.out.println("order : "+order);
            return order;
        }
    }
    private class RandomForestTupleComparator implements Comparator <Tuple<Message, Connection>>{
        private RandomForestTupleComparator(){

        }
        @Override
        public int compare(Tuple<Message, Connection> t1,Tuple<Message, Connection> t2){
            StringBuilder sb1=new StringBuilder();
            sb1.append(" ");
            StringBuilder sb2=new StringBuilder();
            sb2.append(" ");
            Message m1=t1.getKey();
            Message m2=t2.getKey();
            DTNHost from1=t1.getValue().getOtherNode(getHost());
            DTNHost from2=t2.getValue().getOtherNode(getHost());
            int cost1=(int)getCost(from1,m1.getTo())*100;
            sb1.append(cost1+" ");
            int cost2=(int)getCost(from2,m2.getTo())*100;
            sb2.append(cost2+" ");
            int p1=(int)m1.getProperty(PRIORITY_MSG);
            sb1.append(p1+" ");
            int p2=(int)m2.getProperty(PRIORITY_MSG);
            sb2.append(p2+" ");
            int n1=(int)m1.getProperty(MSG_COUNT_PROPERTY);
            sb1.append(n1+" ");
            int n2=(int)m2.getProperty(MSG_COUNT_PROPERTY);
            sb2.append(n2+" ");
            String s1=sb1.toString();
            String s2=sb2.toString();

            RandomForestComparator rfComparator=new RandomForestComparator(s1,s2);
            return rfComparator.compare(m1,m2);
        }
    }
}
