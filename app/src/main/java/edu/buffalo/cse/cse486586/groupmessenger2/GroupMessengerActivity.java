package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ContentHandler;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;
    //String[] remotePorts = {REMOTE_PORT0, REMOTE_PORT1 , REMOTE_PORT2};
    String[] remotePorts = {REMOTE_PORT0, REMOTE_PORT1,REMOTE_PORT2 ,REMOTE_PORT3,REMOTE_PORT4};
    ArrayList<String> remotePortsList = new ArrayList<String>();

    static String myPort;
    String failedNode = null;
    String tempFailedNode = null;
    int numFailureTries = 0;

    boolean failureHandled = false;
    boolean failureAgreed = false;
    int scheduled = 0;
    int message_counter = 0;

    //For FIFO Ordering
    int sentMessagesCount = 0; //messages sent to the group
    HashMap<String,Integer> deliveredMessagesCount = new HashMap<String,Integer>(); // messages delivered from group
    HashMap<String,HashMap<Integer,MessageObject>> fifoHoldBackQueue = new HashMap<String,HashMap<Integer,MessageObject>>();
    // if messages arrive out of order, hold them until previous messages arrive

    Timer timer = new Timer(true);
    TimerTask task = null;

    //for ISIS
    //  priority queue ordererd on increasing sequence number
    PriorityQueue<MessageObject> isisHoldBackQueue = new PriorityQueue<MessageObject>(); // for storing messages based on priority until delivered
    LinkedList<MessageObject> isisDeliveryQueue = new LinkedList<MessageObject>(); // delivery queue
    //to keep track of whether all AVDs have responded
    HashMap<String,HashMap<String,MessageObject>> allProposedSequences = new HashMap<String,HashMap<String,MessageObject>>();

    HashMap<Long,HashMap<String,String>> expectingProposals = new HashMap<Long,HashMap<String,String>>();
    HashMap<Long,HashMap<String,String>> expectingFinals = new HashMap<Long,HashMap<String,String>>();

    HashMap<String, ArrayList<String>> receivedProposals = new HashMap<String,ArrayList<String>>();
    HashMap<String, ArrayList<String>> receivedAgreement = new HashMap<String,ArrayList<String>>();
    ArrayList<String> receivedFailureVote = new ArrayList<String>();
    HashMap<String, Integer> failureVotes = new HashMap<String,Integer>();


    int largestAgreedSequenceNumber  = 0;
    int largestProposedSequenceNumber = 0 ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        //Set the delivered messages count to zeros
        deliveredMessagesCount.put(REMOTE_PORT0 , 0);
        deliveredMessagesCount.put(REMOTE_PORT1 , 0);
        deliveredMessagesCount.put(REMOTE_PORT2 , 0);
        deliveredMessagesCount.put(REMOTE_PORT3 , 0);
        deliveredMessagesCount.put(REMOTE_PORT4 , 0);

        for (int i = 0 ; i < remotePorts.length ; i++)
        {
            remotePortsList.add(remotePorts[i]);
            receivedProposals.put(remotePorts[i], null);
            receivedAgreement.put(remotePorts[i],null);

        }
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        if(scheduled == 0)
        {
            Log.v("SCHEDULE" , "Scheduling Timed Task");
            scheduled = 1;
            task = new TimerTask() {
                @Override
                public void run() {
                    checkFailure();
                }
            };

            timer.schedule(task,3000,1000);
        }


        try {

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }



        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String msg = editText.getText().toString()+"\n";
                editText.setText("");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {


        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            int message_counter = 0;
            while(true) {

                ServerSocket serverSocket = sockets[0];
                try {
                    //serverSocket.setSoTimeout(1000);
                    Socket clientSocket = serverSocket.accept();

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));
                    String message = null;
                    String val;
                    while ((val = in.readLine()) != null)
                    {
                        if(message == null)
                            message = val;
                        else
                            message = message + val;
                    }
                    MessageObject mo = processMessage(message);

                    if(mo.isFailureMessage())
                    {
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, tempFailedNode, myPort, mo.getPort());

                    }
                    else if(mo.isFailureConfirmationMessage())
                    {
                        Log.e("RECEIVEDVOTE" , "*******"+ mo.getText() +"********** from "+mo.getPort()+"********");
                        synchronized (receivedFailureVote)
                        {


                            if(!receivedFailureVote.contains(mo.getPort())) {
                                receivedFailureVote.add(mo.getPort());
                                if (failureVotes.containsKey(mo.getText())) {

                                    failureVotes.put(mo.getText(), failureVotes.get(mo.getText()) + 1);
                                } else {
                                    failureVotes.put(mo.getText(), 1);

                                }
                                Log.e("RECEIVEDVOTESIZE" , Integer.toString(receivedFailureVote.size()));
                                Log.e("FAILURE" , failureVotes.keySet().toString());


                                if(receivedFailureVote.size() == remotePortsList.size() - 1)
                                {
                                    numFailureTries = numFailureTries + 1;
                                    int max = 0;
                                    String failed = null;
                                    for(String node : failureVotes.keySet())
                                    {
                                        if(node!=null && !node.equals("null"))
                                        {
                                            if (failureVotes.get(node) > max) {
                                                max = failureVotes.get(node);
                                                failed = node;
                                            }
                                        }

                                    }
                                    if(numFailureTries <=8 ){
                                    if(failed!=null && !failed.equals("null") && !failed.equals(myPort) && max >=2 ) {
                                        failedNode = failed;
                                        tempFailedNode = failedNode;
                                        Log.e("MAXFAILURES", Integer.toString(max) + " : " + failed);
                                        timer.cancel();
                                        handleFailure(failed);

                                    }
                                    else
                                    {
                                        receivedFailureVote.clear();
                                        failureVotes.clear();
                                        failedNode = null;
                                        tempFailedNode = null;
                                        //timer.schedule(task,1000,1000);
                                        //checkFailure();
                                        //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "failure", myPort);

                                    }}
                                    else
                                    {

                                        failedNode = tempFailedNode;
                                        Log.e("NOCONCENSUS",  " : " + failedNode);
                                        timer.cancel();
                                        handleFailure(failedNode);
                                    }

                                }
                            }


                        }
                    }
                    // If a new message arrives

                    else if (!mo.isProposalMessage() && !mo.isAgreedMessage()) {
                        synchronized (isisHoldBackQueue){

                            if(failedNode == null || ( failedNode != null && !failedNode.equals(mo.getPort())))
                            {
                                largestProposedSequenceNumber = max(largestProposedSequenceNumber, largestAgreedSequenceNumber) + 1;
                                mo.setSequenceNumber(largestProposedSequenceNumber);

                                //Store messages in a priority queue  ordered with the smallest sequence number at the front
                                isisHoldBackQueue.add(mo);
                                Log.v("Received", "ISIS size" + Integer.toString(isisHoldBackQueue.size()));

                                Log.v("Received", "New: Message from" + mo.getPort() + "-" + mo.getText() + ":ID:" + mo.getmId());

                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mo.getText(), myPort, Integer.toString(mo.getSequenceNumber()), mo.getPort(), mo.getmId());
                            }}
                    }
                    else if(mo.isProposalMessage() && !mo.isAgreedMessage()) // a sequence proposal for a message arrives
                    {
                        synchronized (allProposedSequences)
                        {
                            if(failedNode == null || ( failedNode != null && !failedNode.equals(mo.getPort())))
                            {

                                if(receivedProposals.get(mo.getPort()) != null)
                                {
                                    ArrayList<String> lst = receivedProposals.get(mo.getPort());
                                    lst.add(mo.getmId());
                                    receivedProposals.put(mo.getPort(),lst);
                                }
                                else
                                {
                                    ArrayList<String> lst = new ArrayList<String>();
                                    lst.add(mo.getmId());
                                    receivedProposals.put(mo.getPort(),lst);
                                }

                                Log.v("Received", "Proposal : Message from" + mo.getPort() + "-" + mo.getText() + ":ID:" + mo.getmId());

                                addtoProposedMessagesList(mo); // Add the proposal for this message
                                int toCheck = remotePortsList.size();
                                if(failedNode != null)
                                    toCheck = toCheck - 1;
                                if (allProposedSequences.get(mo.getmId()).size() == toCheck)
                                {
                                    Log.v("Proposed" , Integer.toString(allProposedSequences.get(mo.getmId()).size()));
                                    // All AVDs have replied with the proposed sequence number

                                    MessageObject agreedSequenceNumber = getLargestProposedSequence(mo.getmId());
                                    allProposedSequences.remove(mo.getmId());

                                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mo.getText(), myPort, Integer.toString(agreedSequenceNumber.getSequenceNumber()), mo.getPort(), mo.getmId(), "agreed", agreedSequenceNumber.getProcessId());

                                }
                            }}
                    }
                    else if(mo.isAgreedMessage())
                    {

                        synchronized (isisHoldBackQueue)
                        {
                            if(failedNode == null || ( failedNode != null && !failedNode.equals(mo.getPort()))) {
                                if (receivedAgreement.get(mo.getPort()) != null) {
                                    ArrayList<String> lst = receivedAgreement.get(mo.getPort());
                                    lst.add(mo.getmId());
                                    receivedAgreement.put(mo.getPort(), lst);
                                } else {
                                    ArrayList<String> lst = new ArrayList<String>();
                                    lst.add(mo.getmId());
                                    receivedAgreement.put(mo.getPort(), lst);
                                }


                                largestAgreedSequenceNumber = max(largestAgreedSequenceNumber, mo.getSequenceNumber());
                                Log.v("Received", "Agreed : Sequence: " + mo.getSequenceNumber() + " from" + mo.getPort() + "-" + mo.getText() + ":ID:" + mo.getmId());

                                // Reorder the priority Queue
                                PriorityQueue<MessageObject> isisHoldBackQueueReordered = new PriorityQueue<MessageObject>();
                                while (isisHoldBackQueue.size() != 0) {
                                    MessageObject moPQ = isisHoldBackQueue.poll();

                                    if (moPQ.getmId().equals(mo.getmId())) {
                                        moPQ.setSequenceNumber(mo.getSequenceNumber());
                                        moPQ.setDeliverable(true);
                                    }
                                    isisHoldBackQueueReordered.add(moPQ);
                                }
                                isisHoldBackQueue.clear();
                                isisHoldBackQueue = isisHoldBackQueueReordered;
                                Log.v("ISIS", "Size " + Integer.toString(isisHoldBackQueue.size()));
                                Log.v("ISIS", "PEEK " + isisHoldBackQueue.peek().getPort()
                                        + " : " + isisHoldBackQueue.peek().isDeliverable());


                                //Check if the head of the queue is deliverable, move to delivery queue
                                while (isisHoldBackQueue.size() != 0 && isisHoldBackQueue.peek().isDeliverable()) {
                                    MessageObject toBeDelivered = isisHoldBackQueue.poll();
                                    isisDeliveryQueue.add(toBeDelivered);
                                    Log.v("ISIS", "Moving for Delivery: " + toBeDelivered.getText());
                                    Log.v("ISIS", "Size " + Integer.toString(isisHoldBackQueue.size()));

                                }
                                //Deliver all messages in the delivery queue
                                while (isisDeliveryQueue.size() != 0) {
                                    MessageObject temp = new MessageObject();
                                    temp = isisDeliveryQueue.remove();
                                    // When the messages are coming out of the delivery queue , keep track of FIFO ordering
                                    if (temp.getFifoSequenceNumber() == deliveredMessagesCount.get(temp.getPort()) + 1) {
                                        deliveredMessagesCount.put(temp.getPort(), temp.getFifoSequenceNumber());
                                        Log.v("Delivering", temp.getText() + " ISIS Holdback Queue :" + isisHoldBackQueue.size()
                                                + " ISIS Delivery Queue: " + isisDeliveryQueue.size());
                                        publishProgress(temp.getText());
                                        //check if there are any messages in the fifo hold back queue ready for delivery
                                        if (fifoHoldBackQueue.containsKey(temp.getPort())) {
                                            if (fifoHoldBackQueue.get(temp.getPort()).containsKey(temp.getFifoSequenceNumber() + 1)) {
                                                int nextCount = temp.getFifoSequenceNumber() + 1;
                                                deliveredMessagesCount.put(temp.getPort(), nextCount);

                                                publishProgress(fifoHoldBackQueue.get(temp.getPort()).remove(nextCount).getText());
                                                Log.v("Delivering", temp.getText() + " ISIS Holdback Queue :" + isisHoldBackQueue.size()
                                                        + " ISIS Delivery Queue: " + isisDeliveryQueue.size() +
                                                        " FIFO Hold Back Queue" + fifoHoldBackQueue.size());
                                            }
                                        }

                                        // if not FIFO ordered, hold back in a FIFO queue until interveing messages arrive
                                    } else if (temp.getFifoSequenceNumber() > deliveredMessagesCount.get(temp.getPort()) + 1) {
                                        HashMap<Integer, MessageObject> fhb = new HashMap<Integer, MessageObject>();
                                        fhb.put(temp.getFifoSequenceNumber(), temp);
                                        Log.v("FIFO", "Moving to FIFO :size-" + fifoHoldBackQueue.size() + " : " + temp.getText());
                                        fifoHoldBackQueue.put(temp.getPort(), fhb);
                                    }
                                }
                            }}
                    }
                    //handleFailure(checkFailure());

                }
               /* catch(SocketTimeoutException ex)
                {
                    Log.e("SOCKETTIMEOUT", "Checking for a node failure");
                    handleFailure(checkFailure());
                }*/
                /*catch (InterruptedIOException ex)
                {
                    Log.e(TAG, "Interrupted Exception while receiving message");

                }
                */catch (IOException ex)
                {
                    Log.e(TAG, "IO Exception while receiving message");
                    // Log.e(TAG, ex.getMessage());
                }
                catch (Exception ex)
                {
                    Log.e(TAG, "Exception while receiving message");
                    Log.e(TAG, ex.getMessage());
                }

            }

        }


        public synchronized void handleFailure(String port)
        {

            try {
                if (port != null) {
                    Log.v("FAILUREAGREED", "Failed Node" + port + " :" + myPort);
                    // Remove any message from the failednode in my isisholdback queue

                    Log.v("FIX", "-------------------FIXING FAILURE----------------");
                    if(failureHandled == false)
                    {
                        PriorityQueue<MessageObject> tempIsisHoldBackQueue = new PriorityQueue<MessageObject>();
                        if(isisHoldBackQueue.size()!=0) {
                            while (isisHoldBackQueue.size() != 0) {
                                MessageObject obj = isisHoldBackQueue.poll();
                                if (!obj.getPort().equals(port)) {
                                    tempIsisHoldBackQueue.add(obj);

                                    Log.v("ISIS CHECK" ," Deliverable: " +   Boolean.toString(obj.isDeliverable()) +
                                            " Sequence Number :" + obj.getSequenceNumber()  + " Sender:" + obj.getPort() + "All Proposed Sequences" +
                                            allProposedSequences.get(obj.getmId()));
                                }
                            }


                            isisHoldBackQueue.clear();
                            isisHoldBackQueue = tempIsisHoldBackQueue;
                            Log.v("FIX", "New ISIS size " + isisHoldBackQueue.size());
                        }

                        Log.v("FIX", "All Proposed Sequences " + allProposedSequences.size());
                        Log.v("FIX", "Failed Port " + port);

                        //Ignore any proposals from the failed node
                        ArrayList<String> proposalsToRemove = new ArrayList<String>();
                        if(allProposedSequences.size()!=0) {
                            for (String key : allProposedSequences.keySet()) {
                                if (allProposedSequences.get(key).keySet().contains(port)) {
                                    allProposedSequences.get(key).remove(port);
                                }
                                if (allProposedSequences.get(key).size() == 0) {
                                    allProposedSequences.remove(key);
                                }
                                Log.v("FIX", "All Proposed Sequences " + allProposedSequences.size());

                                if (allProposedSequences.get(key).size() == remotePortsList.size() - 1) {
                                    Log.v("Proposed", Integer.toString(allProposedSequences.get(key).size()));
                                    // All AVDs have replied with the proposed sequence number
                                    //MessageObject agreedSequenceNumber = getLargestProposedSequence(key);
                                    HashMap<String, MessageObject> props = allProposedSequences.get(key);
                                    int sent = 0;
                                    for (String host : props.keySet()) {
                                        if (sent == 0) {
                                            proposalsToRemove.add(key);
                                            MessageObject agreedSequenceNumberWhenFailed = getLargestProposedSequence(key);

                                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, props.get(host).getText(), myPort, Integer.toString(agreedSequenceNumberWhenFailed.getSequenceNumber()), props.get(host).getPort(), key, "agreed", agreedSequenceNumberWhenFailed.getProcessId());
                                            sent = sent + 1;
                                        }
                                    }
                                }
                            }

                            for (int i = 0; i < proposalsToRemove.size(); i++) {
                                allProposedSequences.remove(proposalsToRemove.get(i));
                            }
                        }
                        ArrayList<Integer> toBeRemoved = new ArrayList<Integer>();
                        for (int i = 0; i < isisDeliveryQueue.size(); i++) {
                            MessageObject obj = isisDeliveryQueue.get(i);
                            if (obj.getPort().equals(port)) {
                                toBeRemoved.add(i);
                            }
                        }
                        for (int i = 0; i < toBeRemoved.size(); i++) {
                            if (isisDeliveryQueue.size() != 0) {
                                isisDeliveryQueue.remove(toBeRemoved.get(i));
                            }
                        }

                        //Check if the head of the queue is deliverable, move to delivery queue
                        while (isisHoldBackQueue.size() != 0 && isisHoldBackQueue.peek().isDeliverable())
                        {
                            MessageObject toBeDelivered = isisHoldBackQueue.poll();
                            isisDeliveryQueue.add(toBeDelivered);
                            Log.v("ISIS", "Moving for Delivery: " + toBeDelivered.getText());
                            Log.v("ISIS" , "Size " + Integer.toString(isisHoldBackQueue.size()) );

                        }

                        //Deliver all messages in the delivery queue
                        while (isisDeliveryQueue.size() != 0)
                        {
                            MessageObject temp = new MessageObject();
                            temp = isisDeliveryQueue.remove();
                            // When the messages are coming out of the delivery queue , keep track of FIFO ordering
                            if (temp.getFifoSequenceNumber() == deliveredMessagesCount.get(temp.getPort()) + 1)
                            {
                                deliveredMessagesCount.put(temp.getPort(), temp.getFifoSequenceNumber());
                                Log.v("Delivering", temp.getText() + " ISIS Holdback Queue :"  + isisHoldBackQueue.size()
                                        + " ISIS Delivery Queue: " + isisDeliveryQueue.size()   );
                                publishProgress(temp.getText());
                                //check if there are any messages in the fifo hold back queue ready for delivery
                                if(fifoHoldBackQueue.containsKey(temp.getPort()))
                                {
                                    if (fifoHoldBackQueue.get(temp.getPort()).containsKey(temp.getFifoSequenceNumber() + 1))
                                    {
                                        int nextCount = temp.getFifoSequenceNumber() + 1;
                                        deliveredMessagesCount.put(temp.getPort(), nextCount);

                                        publishProgress(fifoHoldBackQueue.get(temp.getPort()).remove(nextCount).getText());
                                        Log.v("Delivering", temp.getText() + " ISIS Holdback Queue :"  + isisHoldBackQueue.size()
                                                + " ISIS Delivery Queue: " + isisDeliveryQueue.size() +
                                                " FIFO Hold Back Queue" + fifoHoldBackQueue.size()   );
                                    }
                                }

                                // if not FIFO ordered, hold back in a FIFO queue until interveing messages arrive
                            }
                            else if (temp.getFifoSequenceNumber() > deliveredMessagesCount.get(temp.getPort()) + 1)
                            {
                                HashMap<Integer,MessageObject> fhb = new HashMap<Integer,MessageObject>();
                                fhb.put(temp.getFifoSequenceNumber(), temp);
                                Log.v("FIFO" , "Moving to FIFO :size-" + fifoHoldBackQueue.size()  +" : "  + temp.getText());
                                fifoHoldBackQueue.put(temp.getPort() , fhb);
                            }
                        }

                        failureHandled = true;
                        failureAgreed = true;
                    }}
            }
            catch(Exception ex)
            {
                Log.e("Error" , "---------------FATAL ERROR IN HANDLING FAILURE--------------------" + ex.getMessage());
            }
        }

        protected void onProgressUpdate(String...strings)
        {

            ContentResolver cr = getContentResolver();
            Uri iUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            ContentValues cv = new ContentValues();
            cv.put("key", Integer.toString(message_counter));
            cv.put("value", strings[0]);
            cr.insert(iUri, cv);
            message_counter = message_counter + 1;
            Log.v("Screen" , "Delivering Message : " + strings[0] + " Port:" + myPort);
            String strReceived = strings[0].trim();
            TextView textView= (TextView) findViewById(R.id.textView1);
            textView.append(strReceived + "\t\n");



        }
    }

    public MessageObject processMessage(String message)
    {
        String[] moInStr = message.split("#");
        MessageObject mo = new MessageObject();
        mo.setText(moInStr[1]);
        mo.setPort(moInStr[2]);
        if(moInStr[0].equals("failure"))
        {
            //type#text#sender#processID#sequenceNumber#mID
            mo.setFailureMessage(true);
            return mo;
        }
        if(moInStr[0].equals("failureConfirmation"))
        {
            //type#text#sender#processID#sequenceNumber#mID
            mo.setFailureConfirmationMessage(true);
            return mo;
        }

        mo.setProcessId(moInStr[3]);
        mo.setmId(moInStr[5]);

        if(moInStr[0].equals("new"))
        {
            //type#text#sender#processID#fifoSequenceNumber#mID
            mo.setFifoSequenceNumber(Integer.parseInt(moInStr[4]));
        }
        if(moInStr[0].equals("proposal"))
        {
            //type#text#sender#processID#sequenceNumber#mID
            mo.setProposalMessage(true);
            mo.setSequenceNumber(Integer.parseInt(moInStr[4]));
        }
        if(moInStr[0].equals("agreed"))
        {
            //type#text#sender#processID#sequenceNumber#mID
            mo.setAgreedMessage(true);
            mo.setSequenceNumber(Integer.parseInt(moInStr[4]));
        }




        return mo;
    }

    public String checkFailure()
    {
        if(tempFailedNode == null) {

            Log.v("FAILUREDETECT", "-----Checking for a Node Failure----");
            ArrayList<Long> expectedToBeRemoved = new ArrayList<Long>();
            for (Long timestamp : expectingProposals.keySet()) {
                HashMap<String, String> tmp = new HashMap<String, String>();
                tmp = expectingProposals.get(timestamp);
                for (String host : receivedProposals.keySet()) {
                    if (tmp.containsKey(host)) {
                        if (receivedProposals.get(host) != null) {
                            if (receivedProposals.get(host).contains(tmp.get(host))) {
                                expectedToBeRemoved.add(timestamp);
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < expectedToBeRemoved.size(); i++) {
                expectingProposals.remove(expectedToBeRemoved.get(i));
            }


            ArrayList<Long> finalsToBeRemoved = new ArrayList<Long>();
            for (Long timestamp : expectingFinals.keySet()) {
                HashMap<String, String> tmp = new HashMap<String, String>();
                tmp = expectingFinals.get(timestamp);
                for (String host : receivedAgreement.keySet()) {
                    if (tmp.containsKey(host)) {
                        if (receivedAgreement.get(host) != null) {
                            if (receivedAgreement.get(host).contains(tmp.get(host))) {
                                finalsToBeRemoved.add(timestamp);
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < finalsToBeRemoved.size(); i++) {
                expectingFinals.remove(finalsToBeRemoved.get(i));
            }

            List<Long> propKeys = new ArrayList(expectingProposals.keySet());
            //Collections.sort(propKeys);


            List<Long> finalsKeys = new ArrayList(expectingFinals.keySet());
            //Collections.sort(finalsKeys);

            List<Long> allKeys = new ArrayList<>(propKeys);
            allKeys.addAll(finalsKeys);
            Collections.sort(allKeys);

            Log.e("FAILUREDETECT" ,Integer.toString( propKeys.size()) + " : "  + Integer.toString(finalsKeys.size())  + " : "  +
                    allKeys.size() );

            for (int i = 0; i < allKeys.size(); i++) {
                HashMap<String, String> tmp = new HashMap<String,String>();
                if(expectingProposals.containsKey(allKeys.get(i)))
                    tmp = expectingProposals.get(allKeys.get(i));
                if(expectingFinals.containsKey(allKeys.get(i)))
                    tmp = expectingFinals.get(allKeys.get(i));
                if(tmp.keySet().size() != 0)
                {
                    for (String key : tmp.keySet()) {
                        Log.e("FAILUREDETECT", "EXPECTING  from " + key + " :" + tmp.get(key) + " :" +
                                Long.toString(System.currentTimeMillis() - allKeys.get(i)));

                        if (System.currentTimeMillis() - allKeys.get(i) > 8000) {

                                tempFailedNode = key;
                                Log.e("FAILUREDETECT", "Failed:" + tempFailedNode);

                                //timer.cancel();
                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "failure", myPort);


                                return tempFailedNode;


                        }
                    }
                }

            }

            /*for (int i = 0; i < finalsKeys.size(); i++) {
                HashMap<String, String> tmp = expectingFinals.get(finalsKeys.get(i));
                for (String key : tmp.keySet()) {
                    Log.e("FAILUREDETECT", "EXPECTING AGREEMENT from " + key + " :" + tmp.get(key) + " :" +
                            Long.toString(System.currentTimeMillis() - finalsKeys.get(i)));
                    if (System.currentTimeMillis() - finalsKeys.get(i) > 20000) {
                        failedNode = key;
                        Log.e("FAILUREDETECT", "Failed:" + failedNode);
                        return failedNode;
                    }
                }

            }*/
        }


        return tempFailedNode;
    }



    public void addtoProposedMessagesList(MessageObject mo)
    {
        //New Proposal message from a AVD
        if(!allProposedSequences.containsKey(mo.getmId()))
        {
            HashMap<String, MessageObject> currProposedMessage = new HashMap<String, MessageObject>();
            currProposedMessage.put(mo.getPort(), mo);
            allProposedSequences.put(mo.getmId(), currProposedMessage);
        }
        else
        {
            //Proposal from other AVDs, AVD's are identified by their processID's
            HashMap<String, MessageObject> proposedMessages = allProposedSequences.get(mo.getmId());
            if(!proposedMessages.containsKey(mo.getPort()))
            {
                proposedMessages.put(mo.getPort(),mo);
                allProposedSequences.put(mo.getmId(),proposedMessages);
            }
        }
    }

    public MessageObject getLargestProposedSequence(String mId)
    {
        // Need to implement an ISIS tie breaker if process propose the same sequence number
        //Proposal from all AVDs
        MessageObject mo = new MessageObject();
        mo.setSequenceNumber(0);
        HashMap<String, MessageObject> proposedMessages = allProposedSequences.get(mId);
        for(String avd : proposedMessages.keySet())
        {
            if(proposedMessages.get(avd).getSequenceNumber() > mo.getSequenceNumber())
            {
                mo.setSequenceNumber( proposedMessages.get(avd).getSequenceNumber());
                mo.setPort(proposedMessages.get(avd).getPort());
            }
        }

        // If the proposed sequence number is same, pick the max process ID
        for(String avd : proposedMessages.keySet())
        {
            if(proposedMessages.get(avd).getSequenceNumber() == mo.getSequenceNumber())
            {
                if(proposedMessages.get(avd).getPort() != mo.getPort())
                {
                    if(Integer.parseInt(proposedMessages.get(avd).getPort()) > Integer.parseInt(mo.getPort()))
                    {
                        mo.setSequenceNumber(proposedMessages.get(avd).getSequenceNumber());
                        mo.setPort(proposedMessages.get(avd).getPort());
                    }
                }
            }
        }

        return mo;
    }

    public int max(int largestProposedSequenceNumber,int largestAgreedSequenceNumber)
    {
        if(largestProposedSequenceNumber >= largestAgreedSequenceNumber)
            return largestProposedSequenceNumber;
        else
            return largestAgreedSequenceNumber;
    }
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {



            if(msgs.length > 5 && msgs[5] != null && msgs[5].equals("agreed"))
            {


                for (int i = 0; i < remotePortsList.size(); i++) {
                    try
                    {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePortsList.get(i)));
                        //socket.setSoTimeout(500);

                        String type = "agreed";
                        String text = msgs[0];
                        String sender = msgs[1];
                        String processId = msgs[6];
                        String sequenceNumber = msgs[2];
                        String mID = msgs[4];

                        //type#text#sender#processID#sequenceNumber#mID
                        String msgToSend = type + "#" + text +"#" + sender +"#" + processId +"#" + sequenceNumber +"#" + mID;

                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        out.println(msgToSend);
                        Log.v("Sent", "AgreedSequence Multicast : Message to " + remotePortsList.get(i) + " from " + msgs[1] + "-" + msgs[0] + ":ID:" + msgs[4]);
                        socket.close();
                    }
                    /*catch(SocketTimeoutException ex)
                    {

                        Log.e(TAG, "ClientTask SocketTimeOutException");

                    }
                    catch(InterruptedIOException ex)
                    {

                        Log.e(TAG, "ClientTask SocketTimeOutException");

                    }*/
                    catch (UnknownHostException e)
                    {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    }
                    catch (IOException e)
                    {


                        Log.e(TAG, "ClientTask socket IOException" );
                        Log.e(TAG, e.getMessage());
                    }
                }

            }
            else if(msgs.length == 3)
            {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(msgs[2]));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("failureConfirmation#" + msgs[0] + "#" + msgs[1]);

                    socket.close();
                }
                catch(Exception ex)
                {
                    Log.e("CONFIRMING FAILURE" , " --- ");
                }
            }
            else if(msgs.length >2 && msgs[2] != null && msgs[2].length() != 0)
            {

                boolean isProposed = true;

                String type = "proposal";
                String text = msgs[0];
                String sender = msgs[1];
                String processId = msgs[1];
                String sequenceNumber = msgs[2];
                String mID = msgs[4];

                //type#text#sender#processID#sequenceNumber#mID
                String msgToSend = type + "#" + text +"#" + sender +"#" + processId +"#" + sequenceNumber +"#" + mID;


                try
                {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(msgs[3]));
                    //socket.setSoTimeout(500);
                    //expectingFinals.put(msgs[3],msgIDs);
                    if(!msgs[1].equals(myPort)) {
                        HashMap<String, String> msg = new HashMap<String, String>();
                        msg.put(msgs[3], mID);
                        expectingFinals.put(System.currentTimeMillis(), msg);
                    }

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);

                    Log.v("Sent", "Proposal : Message to " + msgs[3] + " from " + msgs[1] + "-" + msgs[0] + ":ID:" + msgs[4]);
                    socket.close();
                }
              /* catch(SocketTimeoutException ex)
                {


                    Log.e(TAG, "ClientTask SocketTimeOutException");

                }
                catch(InterruptedIOException ex)
                {


                    Log.e(TAG, "ClientTask SocketTimeOutException");

                }*/
                catch (UnknownHostException e)
                {
                    Log.e(TAG, "ClientTask UnknownHostException");
                }
                catch (IOException e)
                {


                    Log.e(TAG, "ClientTask socket IOException" );
                    Log.e(TAG, e.getMessage());
                }


            }
            else if (msgs.length == 2) {
                if(msgs[0].equals("failure"))
                {

                    for (int i = 0; i < remotePortsList.size(); i++) {
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remotePortsList.get(i)));
                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                            out.println("failure#failure#" + msgs[1]);

                            socket.close();
                        }
                        catch(Exception ex)
                        {
                            Log.e("CONFIRMING FAILURE" , " --- ");
                        }
                    }



                }
                else
                {
                    //FIFO - increment the sent message counter
                    sentMessagesCount = sentMessagesCount + 1;

                    for (int i = 0; i < remotePortsList.size(); i++)
                    {

                        //Total Ordering - Multicast with message and unique id
                        String type = "new";
                        String text = msgs[0];
                        String sender = msgs[1];
                        String processId = msgs[1];
                        String fifoSequenceNumber = Integer.toString(sentMessagesCount);
                        String mID = msgs[1]+"."+Integer.toString(sentMessagesCount);

                        //type#text#sender#processID#fifoSequenceNumber#mID
                        String msgToSend = type + "#" + text +"#" + sender +"#" + processId +"#" + fifoSequenceNumber +"#" + mID;
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remotePortsList.get(i)));
                            //socket.setSoTimeout(500);
                            if(!remotePortsList.get(i).equals(myPort)) {
                                HashMap<String, String> msg = new HashMap<String, String>();
                                msg.put(remotePortsList.get(i), mID);
                                expectingProposals.put(System.currentTimeMillis(), msg);
                            }
                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                            out.println(msgToSend);
                            Log.v("Sent", "Multicast : Message to " + remotePortsList.get(i) + " from " + msgs[1] + "-" + msgs[0] + ":ID:" +mID);

                            socket.close();
                        }
                    /*catch(SocketTimeoutException ex)
                    {
                        Log.v("SOCKETTIMEOUT" , "Exception");

                    }
                    catch(InterruptedIOException ex)
                    {

                        Log.v("InterruptedIO" , "Exception");

                        //ailedNode = remotePortsList.get(i);
                        //Log.v("SENDFAILURE" , "Failed Node" + failedNode );
                        //handleFailure(failedNode);

                    }*/
                        catch (UnknownHostException e) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        }
                        catch (IOException e) {

                            //failedNode = remotePortsList.get(i);
                            //Log.v("SENDFAILURE" , "Failed Node" + failedNode );
                            //handleFailure(failedNode);

                            Log.e(TAG, "ClientTask socket IOException");
                            Log.e(TAG, e.getMessage());
                        }
                    }

                }}
            return null;
        }


    }
}