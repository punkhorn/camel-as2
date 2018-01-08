package org.apache.camel.component.as2.api.entity;

import java.util.concurrent.atomic.AtomicLong;

public class Util {
    
    private static AtomicLong partNumber = new AtomicLong();
    
   /**
     * Generated a unique value for a Multipart boundary string.
     * <p>
     * The boundary string is composed of the components:
     * "----=_Part_&lt;global_part_number&gt;_&lt;newly_created_object's_hashcode&gt;.&lt;current_time&gt;"
     * <p>
     * The generated string contains only US-ASCII characters and hence is safe
     * for use in RFC822 headers.
     * 
     * @return The generated boundary string.
     */
    public static String createBoundaryValue() {
        // TODO: ensure boundary string is limited to 70 characters or less.
        StringBuffer s = new StringBuffer();
        s.append("----=_Part_").append(partNumber.incrementAndGet()).append("_").append(s.hashCode()).append(".")
                .append(System.currentTimeMillis());
        return s.toString();
    }

    public static boolean validateBoundaryValue(String boundaryValue) {
        return true; // TODO: add validation logic.
    }
    
    public static String appendParameter(String headerString, String parameterName, String parameterValue) {
        return headerString + "; " + parameterName + "=" + parameterValue;
    }
 
}
