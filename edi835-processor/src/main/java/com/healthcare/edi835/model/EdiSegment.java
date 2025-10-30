package com.healthcare.edi835.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic EDI segment holder for flexible segment building.
 * Used by SegmentBuilder utility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdiSegment {

    // Segment identifier (e.g., ISA, GS, ST, BPR, CLP)
    private String segmentId;

    // Elements in the segment
    private List<String> elements;

    // Composite elements (if applicable)
    private List<CompositeElement> compositeElements;

    // Segment terminator (default is ~)
    private String terminator;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompositeElement {
        private List<String> components;
        private String separator;  // Default is :
    }

    /**
     * Converts segment to EDI string format.
     *
     * @return EDI formatted segment string
     */
    public String toEdiString() {
        StringBuilder sb = new StringBuilder();
        sb.append(segmentId);

        if (elements != null) {
            for (String element : elements) {
                sb.append("*").append(element != null ? element : "");
            }
        }

        sb.append(terminator != null ? terminator : "~");
        return sb.toString();
    }
}
