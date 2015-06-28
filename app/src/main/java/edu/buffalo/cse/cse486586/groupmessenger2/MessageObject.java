package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;

/**
 * Created by ashish on 3/4/15.
 */
public class MessageObject implements  Comparable<MessageObject> {

        private String text;
        private String mId;
        private boolean failureMessage;
    private boolean isFailureConfirmationMessage;
        private String port;
        private int fifoSequenceNumber;
        private int sequenceNumber;
        private boolean isProposalMessage;
        private boolean isAgreedMessage;
        private boolean isDeliverable;
        private String processId;
    private String failedNode;

    public boolean isFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(boolean failureMessage) {
        this.failureMessage = failureMessage;
    }

    public String getFailedNode() {
        return failedNode;
    }

    public void setFailedNode(String failedNode) {
        this.failedNode = failedNode;
    }

    MessageObject()

    {
        this.isProposalMessage = false;
        this.isAgreedMessage = false;
        this.isDeliverable = false;
        this.failureMessage = false;
        this.isFailureConfirmationMessage = false;
    }

    public boolean isFailureConfirmationMessage() {
        return isFailureConfirmationMessage;
    }

    public void setFailureConfirmationMessage(boolean isFailureConfirmationMessage) {
        this.isFailureConfirmationMessage = isFailureConfirmationMessage;
    }

    public int getFifoSequenceNumber() {
        return fifoSequenceNumber;
    }

    public void setFifoSequenceNumber(int fifoSequenceNumber) {
        this.fifoSequenceNumber = fifoSequenceNumber;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public boolean isAgreedMessage() {
        return isAgreedMessage;
    }

    public void setAgreedMessage(boolean isAgreedMessage) {
        this.isAgreedMessage = isAgreedMessage;
    }

    public boolean isDeliverable() {
        return isDeliverable;
    }

    public void setDeliverable(boolean isDeliverable) {
        this.isDeliverable = isDeliverable;
    }


    public boolean isProposalMessage() {
        return isProposalMessage;
    }

    public void setProposalMessage(boolean isProposalMessage) {
        this.isProposalMessage = isProposalMessage;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getText()
        {
            return text;
        }

        public String getmId()
        {
            return mId;
        }

        public void setText(String text)
        {
            this.text = text;
        }

        public void setmId(String mId)
        {
            this.mId = mId;
        }

        public void setPort(String port)
        {
            this.port = port;
        }

        public String getPort()
        {
            return port;
        }

    @Override
    public int compareTo(MessageObject B)
    {
        if(this.getSequenceNumber() == B.getSequenceNumber())
            return this.getProcessId().compareTo(B.getProcessId());
        return this.getSequenceNumber() - B.getSequenceNumber();
    }

}
