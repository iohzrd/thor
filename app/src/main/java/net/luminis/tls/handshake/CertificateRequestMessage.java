package net.luminis.tls.handshake;

import com.google.common.base.Utf8;

import net.luminis.tls.TlsConstants;
import net.luminis.tls.TlsProtocolException;
import net.luminis.tls.alert.BadCertificateAlert;
import net.luminis.tls.alert.DecodeErrorException;
import net.luminis.tls.extension.Extension;
import net.luminis.tls.extension.ExtensionParser;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CertificateRequestMessage extends HandshakeMessage{

    private List<Extension> extensions;
    private byte[] raw;

    public byte[] getCertificate_request_context() {
        return certificate_request_context;
    }

    private byte[] certificate_request_context;
    private static final int MINIMAL_MESSAGE_LENGTH = 1 + 3 + 2;
    public CertificateRequestMessage parse(ByteBuffer buffer,
                                           int length, ExtensionParser customExtensionParser) throws TlsProtocolException {
        if (buffer.remaining() < MINIMAL_MESSAGE_LENGTH) {
            throw new DecodeErrorException("Message too short");
        }

        int start = buffer.position();
        int msgLength = buffer.getInt() & 0x00ffffff;
        if (buffer.remaining() < msgLength || msgLength < 2) {
            throw new DecodeErrorException("Incorrect message length");
        }

        //int certificate_request_context_length = buffer.getShort()  & 0xffff;;
        certificate_request_context  = new byte[msgLength];
        buffer.get(certificate_request_context);

        // extensions = parseExtensions(buffer, TlsConstants.HandshakeType.server_hello, customExtensionParser);

        // Raw bytes are needed for computing the transcript hash
        buffer.position(start);
        raw = new byte[length];
        buffer.mark();
        buffer.get(raw);

        return this;
    }


    public List<Extension> getExtensions() {
        return extensions;
    }

    @Override
    public byte[] getBytes() {
        return raw;
    }

    @Override
    public TlsConstants.HandshakeType getType() {
        return TlsConstants.HandshakeType.certificate_request;
    }

}
