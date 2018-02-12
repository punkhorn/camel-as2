package org.apache.camel.component.as2.api.entity;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;
import org.bouncycastle.util.encoders.Base64;

public class EntityUtils {

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
    
    public static OutputStream encode(OutputStream os, String encoding) throws Exception {
        Args.notNull(os, "Output Stream");
        
        if (encoding == null) {
            // Identity encoding
            return os;
        }
        switch (encoding.toLowerCase()) {
        case "base64":
            return new Base64OutputStream(os, true);
        case "quoted-printable":
            // TODO: implement QuotedPrintableOutputStream
            return new Base64OutputStream(os, true);
        case "binary":
        case "7bit":
        case "8bit":
            // Identity encoding
            return os;
        default:
            throw new Exception("Unknown encoding: " + encoding);
        }
    }
    
    public static byte[] decode(byte[] data, String encoding) throws Exception {
        Args.notNull(data, "Input Stream");
        
        if (encoding == null) {
            // Identity encoding
            return data;
        }
        switch (encoding.toLowerCase()) {
        case "base64":
            return Base64.decode(data);
        case "quoted-printable":
            return QuotedPrintableCodec.decodeQuotedPrintable(data);
        case "binary":
        case "7bit":
        case "8bit":
            // Identity encoding
            return data;
        default:
            throw new Exception("Unknown encoding: " + encoding);
        }
    }
    
    public static InputStream decode(InputStream is, String encoding) throws Exception {
        Args.notNull(is, "Input Stream");
        
        if (encoding == null) {
            // Identity encoding
            return is;
        }
        switch (encoding.toLowerCase()) {
        case "base64":
            return new Base64InputStream(is, false);
        case "quoted-printable":
            // TODO: implement QuotedPrintableInputStream
            return new Base64InputStream(is, false);
        case "binary":
        case "7bit":
        case "8bit":
            // Identity encoding
            return is;
        default:
            throw new Exception("Unknown encoding: " + encoding);
        }
    }
    
    public static ApplicationEDIEntity createEDIEntity(String ediMessage, ContentType ediMessageContentType, String contentTransferEncoding, boolean isMainBody) throws Exception {
        Args.notNull(ediMessage, "EDI Message");
        Args.notNull(ediMessageContentType, "EDI Message Content Type");
        
        switch(ediMessageContentType.getMimeType().toLowerCase()) {
        case AS2MediaType.APPLICATION_EDIFACT:
            return new ApplicationEDIFACTEntity(ediMessage, ediMessageContentType.getCharset().toString(), contentTransferEncoding, isMainBody);            
        case AS2MediaType.APPLICATION_EDI_X12:
            return new ApplicationEDIX12Entity(ediMessage, ediMessageContentType.getCharset().toString(), contentTransferEncoding, isMainBody);            
        case AS2MediaType.APPLICATION_EDI_CONSENT:
            return new ApplicationEDIConsentEntity(ediMessage, ediMessageContentType.getCharset().toString(), contentTransferEncoding, isMainBody);            
        default:
            throw new Exception("Invalid EDI entity mime type: " + ediMessageContentType.getMimeType());
        }
        
    }
    
}
