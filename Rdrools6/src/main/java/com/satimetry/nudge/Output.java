package com.satimetry.nudge;

import java.util.HashMap;

public class Output {

    private String outputMap;

    public Output() {
    }

    public Output(String outputMap) {
        this.outputMap = outputMap;
    }

    public String getOutputMap() {
        return outputMap;
    }

    public void setOutputMap(String outputMap) {
        this.outputMap = outputMap;
    }

    public String toString() {
        return outputMap.toString(); 
    }

}

