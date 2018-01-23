package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.Args;

public abstract class MimeEntity extends AbstractHttpEntity {
    
    /**
     * An OuputStream wrapper that doesn't close its underlying output stream.
     * <p>
     * Instances of this stream are used by entities to attach encoding streams
     * to underlying output stream in order to write out their encoded content
     * and then flush and close these encoding streams without closing the
     * underlying output stream.
     */
    protected static class NoCloseOutputStream extends FilterOutputStream {
        public NoCloseOutputStream(OutputStream os) {
            super(os);
        }

        public void close() {
            // do nothing
        }
    }

    protected static final long UNKNOWN_CONTENT_LENGTH = -1;
    
    protected static final long RECALCULATE_CONTENT_LENGTH = -2;

    protected boolean isMainBody = false;

    private final HeaderGroup headergroup = new HeaderGroup();
    
    protected Header contentTransferEncoding = null;

    protected long contentLength = RECALCULATE_CONTENT_LENGTH;

    protected MimeEntity(ContentType contentType, String contentTransferEncoding, boolean isMainBody) {
        this.isMainBody = isMainBody;
        if (contentType != null) {
            this.contentType = new BasicHeader(AS2Header.CONTENT_TYPE, contentType.toString());
        }
        if (contentTransferEncoding != null) {
            this.contentTransferEncoding = new BasicHeader(AS2Header.CONTENT_TRANSFER_ENCODING, contentTransferEncoding);
        }
    }
    
    protected MimeEntity() {
    }
    
    public boolean isMainBody() {
        return isMainBody;
    }
    
    public void setMainBody(boolean isMainBody) {
        this.isMainBody = isMainBody;
    }

    public Header getContentTransferEncoding() {
        return this.contentTransferEncoding;
    }
    
    public boolean containsHeader(final String name) {
        return this.headergroup.containsHeader(name);
    }

    public Header[] getHeaders(final String name) {
        return this.headergroup.getHeaders(name);
    }

    public Header getFirstHeader(final String name) {
        return this.headergroup.getFirstHeader(name);
    }

    public Header getLastHeader(final String name) {
        return this.headergroup.getLastHeader(name);
    }

    public Header[] getAllHeaders() {
        return this.headergroup.getAllHeaders();
    }

    public void addHeader(final Header header) {
        this.headergroup.addHeader(header);
    }

    public void addHeader(final String name, final String value) {
        Args.notNull(name, "Header name");
        this.headergroup.addHeader(new BasicHeader(name, value));
    }

    public void setHeader(final Header header) {
        this.headergroup.updateHeader(header);
    }

    public void setHeader(final String name, final String value) {
        Args.notNull(name, "Header name");
        this.headergroup.updateHeader(new BasicHeader(name, value));
    }

    public void setHeaders(final Header[] headers) {
        this.headergroup.setHeaders(headers);
    }

    public void removeHeader(final Header header) {
        this.headergroup.removeHeader(header);
    }

    public void removeHeaders(final String name) {
        if (name == null) {
            return;
        }
        for (final HeaderIterator i = this.headergroup.iterator(); i.hasNext(); ) {
            final Header header = i.nextHeader();
            if (name.equalsIgnoreCase(header.getName())) {
                i.remove();
            }
        }
    }

    public HeaderIterator headerIterator() {
        return this.headergroup.iterator();
    }

    public HeaderIterator headerIterator(final String name) {
        return this.headergroup.iterator(name);
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public boolean isStreaming() {
        return !isRepeatable();
    }
    
    @Override
    public long getContentLength() {
        if (contentLength == RECALCULATE_CONTENT_LENGTH) {
            // Calculate content length
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                writeTo(out);
                contentLength = out.toByteArray().length;
            } catch (IOException e) {
                contentLength = MimeEntity.UNKNOWN_CONTENT_LENGTH;
            }
        }
        return contentLength;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        final ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        writeTo(outstream);
        outstream.flush();
        return new ByteArrayInputStream(outstream.toByteArray());
    }

    public String getCharset() {
        ContentType contentType = ContentType.parse(getContentType().getValue());
        Charset charset = contentType.getCharset();
        if (charset != null) {
            return charset.name();
        }
        return AS2CharSet.US_ASCII;
    }

}
