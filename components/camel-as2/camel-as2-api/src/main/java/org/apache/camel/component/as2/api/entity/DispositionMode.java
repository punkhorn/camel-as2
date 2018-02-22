package org.apache.camel.component.as2.api.entity;

public enum DispositionMode {
    MANUAL_ACTION_MDN_SENT_MANUALLY("manual-action", "MDN-sent-manually"),
    MANUAL_ACTION_MDN_SENT_AUTOMATICALLY("manual-action", "MDN-sent-automatically"),
    AUTOMATIC_ACTION_MDN_SENT_MANUALLY("automatic-action", "MDN-sent-manually"),
    AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY("automatic-action", "MDN-sent-automatically");
    
    private String actionMode;
    private String sendingMode;

    private DispositionMode(String actionMode, String sendingMode) {
        this.actionMode = actionMode;
        this.sendingMode = sendingMode;
    }
    
    public String getActionMode() {
        return actionMode;
    }

    public String getSendingMode() {
        return sendingMode;
    }

    @Override
    public String toString() {
        return actionMode + "/" + sendingMode;
    }
}
