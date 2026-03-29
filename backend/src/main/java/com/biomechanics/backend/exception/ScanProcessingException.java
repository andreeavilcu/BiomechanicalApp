package com.biomechanics.backend.exception;

public class ScanProcessingException extends RuntimeException {

    private final String scanFileName;
    private final String processingStage;

    public ScanProcessingException(String message) {
        super(message);
        this.scanFileName = null;
        this.processingStage = null;
    }

    public ScanProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.scanFileName = null;
        this.processingStage = null;
    }

    public ScanProcessingException(String message, String scanFileName, String processingStage) {
        super(message);
        this.scanFileName = scanFileName;
        this.processingStage = processingStage;
    }

    public ScanProcessingException(String message, String scanFileName, String processingStage, Throwable cause) {
        super(message, cause);
        this.scanFileName = scanFileName;
        this.processingStage = processingStage;
    }

    public String getScanFileName() {
        return scanFileName;
    }

    public String getProcessingStage() {
        return processingStage;
    }
}
