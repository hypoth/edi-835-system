package com.healthcare.edi835.ncpdp.parser;

/**
 * Exception thrown when parsing NCPDP D.0 transactions fails.
 *
 * <p>This exception is used to indicate errors during NCPDP parsing including:</p>
 * <ul>
 *   <li>Invalid format or structure</li>
 *   <li>Missing required segments</li>
 *   <li>Invalid field values</li>
 *   <li>Malformed data</li>
 * </ul>
 */
public class NcpdpParseException extends Exception {

    private final String segmentId;
    private final int lineNumber;

    /**
     * Constructs a new parse exception with the specified message
     *
     * @param message the detail message
     */
    public NcpdpParseException(String message) {
        super(message);
        this.segmentId = null;
        this.lineNumber = -1;
    }

    /**
     * Constructs a new parse exception with message and cause
     *
     * @param message the detail message
     * @param cause the cause
     */
    public NcpdpParseException(String message, Throwable cause) {
        super(message, cause);
        this.segmentId = null;
        this.lineNumber = -1;
    }

    /**
     * Constructs a new parse exception with segment context
     *
     * @param message the detail message
     * @param segmentId the segment where error occurred
     * @param lineNumber the line number where error occurred
     */
    public NcpdpParseException(String message, String segmentId, int lineNumber) {
        super(String.format("%s (Segment: %s, Line: %d)", message, segmentId, lineNumber));
        this.segmentId = segmentId;
        this.lineNumber = lineNumber;
    }

    /**
     * Gets the segment ID where the error occurred
     *
     * @return segment ID or null if not available
     */
    public String getSegmentId() {
        return segmentId;
    }

    /**
     * Gets the line number where the error occurred
     *
     * @return line number or -1 if not available
     */
    public int getLineNumber() {
        return lineNumber;
    }
}
