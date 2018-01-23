package org.apache.camel.component.as2.api.entity;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;

public class MultipartSignedEntity extends MultipartMimeEntity {

    public MultipartSignedEntity(MimeEntity data, AS2SignedDataGenerator signer, String signatureCharSet, String signatureTransferEncoding, boolean isMainBody, String boundary) throws Exception {
        super(null, isMainBody, boundary);
        ContentType contentType = signer.createMultipartSignedContentType(this.boundary);
        this.contentType = new BasicHeader(AS2Header.CONTENT_TYPE, contentType.toString());
        addPart(data);
        ApplicationPkcs7SignatureEntity signature = new ApplicationPkcs7SignatureEntity(data, signer, signatureCharSet, signatureTransferEncoding, false);
        addPart(signature);
    }
    
}
