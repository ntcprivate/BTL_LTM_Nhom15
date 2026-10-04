package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class DrawUpload implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final byte[] image;

    public DrawUpload(String matchId, byte[] image) {
        this.matchId = matchId;
        this.image = image == null ? null : image.clone();
    }

    public String getMatchId() { return matchId; }
    public byte[] getImage() { return image == null ? null : image.clone(); }
}
