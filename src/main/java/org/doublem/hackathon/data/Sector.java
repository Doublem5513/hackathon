package org.doublem.hackathon.data;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Created by mmatosevic on 19.2.2015.
 */
public class Sector {
    private Rect activeArea;
    private long lastActivityTime;
    private int lastFrameActivityCount;
    private int activationThreshold = 5;
    private int activitySustainThreshold = 1;
    private int activeStateTimeoutMs = 2*1000;
    private boolean active = false;

    public Sector(Rect activeArea, int activationThreshold, int activitySustainThreshold, int activeStateTimeoutMs) {
        this.activeArea = activeArea;
        this.activationThreshold = activationThreshold;
        this.activitySustainThreshold = activitySustainThreshold;
        this.activeStateTimeoutMs = activeStateTimeoutMs;
    }

    public void analyzeData(Mat sectorData){
        Mat submat = sectorData.submat(this.activeArea);
        int nonZeroPixels = Core.countNonZero(submat);
        this.lastFrameActivityCount = nonZeroPixels;
        if(nonZeroPixels >= activationThreshold){
            this.active = true;
        }
        if(nonZeroPixels >= activitySustainThreshold){
            lastActivityTime = System.currentTimeMillis();
        }
        long timeSinceLastActivity = System.currentTimeMillis() - this.lastActivityTime;
        if(timeSinceLastActivity >= activeStateTimeoutMs){
            this.active = false;
        }
        if(this.active){
            Core.rectangle(sectorData, new Point(this.activeArea.x, this.activeArea.y), new Point(this.activeArea.x + this.activeArea.width, this.activeArea.y + this.activeArea.height), new Scalar(2550, 0, 0));
        }
    }

    public boolean isActive() {
        return active;
    }
}
