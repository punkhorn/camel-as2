package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.component.as2.api.AS2CharSet;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.entity.ContentType;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;

public class ApplicationPkcs7SignatureEntity extends MimeEntity {
    
    private static final String CONTENT_DISPOSITION = "attachment; filename=\"smime.p7s\"";
    
    private static final String CONTENT_DESCRIPTION = "S/MIME Cryptographic Signature";
    
    private final MimeEntity data;
    private final CMSSignedDataGenerator signer;
    
    public ApplicationPkcs7SignatureEntity(MimeEntity data, CMSSignedDataGenerator signer, String charset, String transferEncoding, boolean isMainBody) {
        super(ContentType.parse(Util.appendParameter(AS2MediaType.APPLICATION_PKCS7_SIGNATURE, "charset",  charset)), transferEncoding, isMainBody);
        addHeader(this.contentType);
        addHeader(this.contentTransferEncoding);
        addHeader(AS2Header.CONTENT_DISPOSITION, CONTENT_DISPOSITION);
        addHeader(AS2Header.CONTENT_DESCRIPTION, CONTENT_DESCRIPTION);
        this.data = data;
        this.signer = signer;
    }

    public MimeEntity getData() {
        return data;
    }

    public CMSSignedDataGenerator getSigner() {
        return signer;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);

        try {
            
            // Write out mime part headers if this is not the main body of message.
            if (!isMainBody()) {
                try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, AS2CharSet.US_ASCII)) {

                    HeaderIterator it = headerIterator();
                    while (it.hasNext()) {
                        Header header = it.nextHeader();
                        canonicalOutstream.writeln(header.toString());
                    }
                    canonicalOutstream.writeln(); // ensure empty line between headers and body; RFC2046 - 5.1.1
                }
            }

            // Write out signed data.
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    Base64OutputStream base64OutputStream = new Base64OutputStream(ncos, true)) {
                data.writeTo(bos);
                bos.flush();

                CMSTypedData contentData = new CMSProcessableByteArray(bos.toByteArray());
                CMSSignedData signedData = signer.generate(contentData, false);

                base64OutputStream.write(signedData.getEncoded());
            }
        } catch (CMSException e) {
            throw new IOException("failed to sign data", e);
        }

    }

}
