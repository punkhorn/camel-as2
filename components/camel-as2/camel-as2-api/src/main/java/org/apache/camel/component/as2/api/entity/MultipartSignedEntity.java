package org.apache.camel.component.as2.api.entity;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public class MultipartSignedEntity extends MultipartMimeEntity {

    public MultipartSignedEntity(MimeEntity data, AS2SignedDataGenerator signer, String signatureCharSet, String signatureTransferEncoding, boolean isMainBody, String boundary) throws Exception {
        super(null, isMainBody, boundary);
        ContentType contentType = signer.createMultipartSignedContentType(this.boundary);
        this.contentType = new BasicHeader(AS2Header.CONTENT_TYPE, contentType.toString());
        addPart(data);
        ApplicationPkcs7SignatureEntity signature = new ApplicationPkcs7SignatureEntity(data, signer, signatureCharSet, signatureTransferEncoding, false);
        addPart(signature);
    }
    
    protected MultipartSignedEntity() {
    }
    
    public static HttpEntity parseMultipartSignedEntity(HttpEntity entity, boolean isMainBody) throws Exception{
        Args.notNull(entity, "Entity");
        Args.check(entity.isStreaming(), "Entity is not streaming");
        MultipartSignedEntity multipartSignedEntity = null;
        Header[] headers = null;

        try {
            // Determine and validate the Content Type
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader == null) {
                throw new HttpException("Content-Type header is missing");
            }
            ContentType multipartSignedContentType =  ContentType.parse(entity.getContentType().getValue());
            if (!multipartSignedContentType.getMimeType().equals(AS2MimeType.MULTIPART_SIGNED)) {
                throw new HttpException("Entity has invalid MIME type '" + multipartSignedContentType.getMimeType() + "'");
            }

            SessionInputBufferImpl inBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8 * 1024);
            inBuffer.bind(entity.getContent());
            
            // Parse Headers
            if (!isMainBody) {
               headers = AbstractMessageParser.parseHeaders(
                        inBuffer,
                        -1,
                        -1,
                        BasicLineParser.INSTANCE,
                        null);
            }
            
            multipartSignedEntity = new MultipartSignedEntity();
            
            if (headers != null) {
                multipartSignedEntity.setHeaders(headers);
            }

            // Get Boundary Value
            String boundary = multipartSignedContentType.getParameter("boundary");
            if (boundary == null) {
                throw new HttpException("Failed to retrive boundary value");
            }
            
            //
            // Parse EDI Message Body Part
            //
            
            // Skip Preamble and Start Boundary line
            skipPreambleAndStartBoundary(inBuffer, boundary);
            
            // Read EDI Message Body Part Headers
            headers = AbstractMessageParser.parseHeaders(
                    inBuffer,
                    -1,
                    -1,
                    BasicLineParser.INSTANCE,
                    null);
            
            // Get Content-Type and Content-Transfer-Encoding
            ContentType ediMessageContentType = null;
            String ediMessageContentTransferEncoding = null;
            for (Header header : headers) {
                switch (header.getName().toLowerCase()) {
                case AS2Header.CONTENT_TYPE:
                    ediMessageContentType = ContentType.parse(header.getValue());
                    break;
                case AS2Header.CONTENT_TRANSFER_ENCODING:
                    ediMessageContentTransferEncoding = header.getValue();
                    break;
                }
            }
            if (ediMessageContentType == null) {
                throw new HttpException("Failed to find Content-Type header in EDI message body part");
            }
            if (!isEDIMessageContentType(ediMessageContentType)) {
                throw new HttpException("Invalid content type '" + ediMessageContentType.getMimeType() + "' for EDI message body part");
            }
            
           
            // - Read EDI Message Body Part Content
            CharArrayBuffer ediMessageContentBuffer = new CharArrayBuffer(1024);
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            boolean foundMultipartEndBoundary = false;
            while(inBuffer.readLine(lineBuffer) != -1) {
                if (isBoundaryDelimiter(lineBuffer, null, boundary)) {
                    foundMultipartEndBoundary = true;
                    lineBuffer.clear();
                    break;
                }
                ediMessageContentBuffer.append(lineBuffer);
                ediMessageContentBuffer.append("\r\n"); // add line delimiter
                lineBuffer.clear();
            }
            if (!foundMultipartEndBoundary) {
                throw new HttpException("Failed to find end boundary delimiter for EDI message body part");
            }
            
            // Decode Content
            Charset ediMessageCharset = ediMessageContentType.getCharset();
            if (ediMessageCharset == null) {
                ediMessageCharset = StandardCharsets.US_ASCII;
            }
            byte[] bytes = EntityUtils.decode(ediMessageContentBuffer.toString().getBytes(ediMessageCharset), ediMessageContentTransferEncoding);
            String ediMessageContent = new String(bytes, ediMessageCharset);
            
            // Build application EDI entity and add to multipart.
            ApplicationEDIEntity applicationEDIEntity = EntityUtils.createEDIEntity(ediMessageContent, ediMessageContentType, ediMessageContentTransferEncoding, false);
            applicationEDIEntity.removeAllHeaders();
            applicationEDIEntity.setHeaders(headers);
            multipartSignedEntity.addPart(applicationEDIEntity);
            
            //
            // End EDI Message Body Parts
            
            return multipartSignedEntity;
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }
    
    public static boolean isEDIMessageContentType(ContentType ediMessageContentType) {
        switch(ediMessageContentType.getMimeType().toLowerCase()) {
        case AS2MediaType.APPLICATION_EDIFACT:
            return true;
        case AS2MediaType.APPLICATION_EDI_X12:
            return true;
        case AS2MediaType.APPLICATION_EDI_CONSENT:
            return true;
        default:
            return false;
        }
    }
    
    public static void skipPreambleAndStartBoundary(SessionInputBufferImpl inBuffer, String boundary) throws HttpException {

        boolean foundStartBoundary;
        try {
            foundStartBoundary = false;
            CharArrayBuffer lineBuffer = new CharArrayBuffer(1024);
            while(inBuffer.readLine(lineBuffer) != -1) {
                final ParserCursor cursor = new ParserCursor(0, lineBuffer.length());
                if (isBoundaryDelimiter(lineBuffer, cursor, boundary)) {
                    foundStartBoundary = true;
                    break;
                }
                lineBuffer.clear();
            }
        } catch (Exception e) {
            throw new HttpException("Failed to read start boundary for body part", e);
        }
        
        if (!foundStartBoundary) {
            throw new HttpException("Failed to find start boundary for body part");
        }
        
    }
    
    public static boolean isBoundaryDelimiter(final CharArrayBuffer buffer, ParserCursor cursor, String boundary) {
        Args.notNull(buffer, "Buffer");
        Args.notNull(boundary, "Boundary");
        if (cursor == null) {
            cursor = new ParserCursor(0, buffer.length());
        }

        String boundaryDelimiter = "--" + boundary; // boundary delimiter - RFC2046 5.1.1
        
        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();
        
        if ((indexFrom + boundaryDelimiter.length()) > indexTo) {
            return false; 
        }
        
        for (int i = indexFrom; i < indexTo; ++i) {
            if (buffer.charAt(i) != boundaryDelimiter.charAt(i)) {
                return false;
            }
        }
        
        return true;
    }

    public static boolean isBoundaryCloseDelimiter(final CharArrayBuffer buffer, ParserCursor cursor, String boundary) {
        Args.notNull(buffer, "Buffer");
        Args.notNull(boundary, "Boundary");
        if (cursor == null) {
            cursor = new ParserCursor(0, buffer.length());
        }

        String boundaryCloseDelimiter = "--" + boundary + "--"; // boundary close-delimiter - RFC2046 5.1.1
        
        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();
        
        if ((indexFrom + boundaryCloseDelimiter.length()) > indexTo) {
            return false; 
        }
        
        for (int i = indexFrom; i < indexTo; ++i) {
            if (buffer.charAt(i) != boundaryCloseDelimiter.charAt(i)) {
                return false;
            }
        }
        
        return true;
    }

}
