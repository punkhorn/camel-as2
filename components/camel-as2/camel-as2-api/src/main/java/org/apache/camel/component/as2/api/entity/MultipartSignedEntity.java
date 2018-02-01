package org.apache.camel.component.as2.api.entity;

import java.io.IOException;

import org.apache.camel.component.as2.api.AS2Header;
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
    
    public HttpEntity parseEntity(HttpEntity entity, boolean isMainBody) throws Exception{
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
            ContentType contentType =  ContentType.parse(entity.getContentType().getValue());
            if (!contentType.getMimeType().equals(AS2MimeType.MULTIPART_SIGNED)) {
                throw new HttpException("Entity has invalid MIME type '" + contentType.getMimeType() + "'");
            }

            // Determine Transfer Encoding
            Header transferEncoding = entity.getContentEncoding();
            String contentTransferEncoding = transferEncoding == null ? null : transferEncoding.getValue();
            
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
            String boundary = contentType.getParameter("boundary");
            if (boundary == null) {
                throw new HttpException("Failed to retrive boundary value");
            }
            
            //
            // Parse EDI Message Part
            //
            
            // Skip Preamble and Start Boundary line
            skipPreambleAndStartBoundary(inBuffer);
            
            // Read Body Part Headers
            headers = AbstractMessageParser.parseHeaders(
                    inBuffer,
                    -1,
                    -1,
                    BasicLineParser.INSTANCE,
                    null);
            
            // Get Content-Type
            ContentType ediMesssageContentType;
            for (Header header : headers) {
                if (header.getName().equalsIgnoreCase(AS2Header.CONTENT_TYPE)) {
                    ediMesssageContentType = ContentType.parse(header.getValue());
                }
            }
           
            // - Read Body Part Content
            // - Create, Populate and Add Body Part
            
            
            //
            // End Body Parts
            
            return multipartSignedEntity;
        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpException("Failed to parse entity content", e);
        }
    }
    
    public void skipPreambleAndStartBoundary(SessionInputBufferImpl inBuffer) throws HttpException {

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
    
    public boolean isBoundaryDelimiter(final CharArrayBuffer buffer, final ParserCursor cursor, String boundary) {
        Args.notNull(buffer, "Buffer");
        Args.notNull(cursor, "Cursor");

        String boundaryDelimiter = "--" + boundary; // boundary delimiter - RFC2046 5.1.1
        
        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();
        
        // boundary delimiter must occupy entire line - RFC2046 5.1.1
        if ((indexFrom + boundaryDelimiter.length()) != indexTo) {
            return false; 
        }
        
        for (int i = indexFrom; i < indexTo; ++i) {
            if (buffer.charAt(i) != boundaryDelimiter.charAt(i)) {
                return false;
            }
        }
        
        return true;
    }

    public boolean isBoundaryCloseDelimiter(final CharArrayBuffer buffer, final ParserCursor cursor, String boundary) {
        Args.notNull(buffer, "Buffer");
        Args.notNull(cursor, "Cursor");

        String boundaryCloseDelimiter = "--" + boundary + "--"; // boundary close-delimiter - RFC2046 5.1.1
        
        int indexFrom = cursor.getPos();
        int indexTo = cursor.getUpperBound();
        
        // boundary closing-delimiter must occupy entire line - RFC2046 5.1.1
        if ((indexFrom + boundaryCloseDelimiter.length()) != indexTo) {
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
