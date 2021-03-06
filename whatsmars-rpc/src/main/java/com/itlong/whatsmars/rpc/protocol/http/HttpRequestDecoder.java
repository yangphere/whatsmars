package com.itlong.whatsmars.rpc.protocol.http;

import com.itlong.whatsmars.grpc.service.HelloRequest;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

import java.util.Arrays;

/**
 * Created by shenhongxi on 2017/6/14.
 */
public class HttpRequestDecoder  extends CumulativeProtocolDecoder {
    @Override
    protected boolean doDecode(IoSession ioSession, IoBuffer ioBuffer, ProtocolDecoderOutput out) throws Exception {
        int start = ioBuffer.position();
        String content = "";
        byte[] messages = null;
        try {
            //content = inBuffer.getString(Charset.forName("UTF-8").newDecoder());
            messages = new byte[ioBuffer.limit()];
            ioBuffer.get(messages);
            content = new String(messages,"UTF-8");
        } catch (Exception e) {
            ioBuffer.position(start);
            return false;
        }

        int position = content.indexOf("\r\n\r\n");
        if (position == -1) {
            ioBuffer.position(start);
            return false;
        }

        HttpRequestMessage reqMsg = new HttpRequestMessage();
        String headerContent = content.substring(0, position);
        int headerLength = position + 4;
        String[] headers = headerContent.split("\r\n");
        for (String header : headers) {
            String[] temps = header.split(": ");
            if (temps == null || temps.length <= 1)
                continue;
            reqMsg.addRequestHeader(temps[0].trim().toLowerCase(), temps[1].trim());
        }

        int fLPosition = headerContent.indexOf("\r\n");
        String url = headerContent.substring(0, fLPosition);
        String[] urls = url.split(" ");

        if (urls.length < 3) {
            reqMsg.setErrorCode(400);
            out.write(reqMsg);
            return true;
        }

        if (!"GET".equalsIgnoreCase(urls[0]) && !"POST".equalsIgnoreCase(urls[0])) {
            reqMsg.setErrorCode(400);
            out.write(reqMsg);
            return true;
        }

        reqMsg.setRequestProtocol(urls[0]);
        reqMsg.setRequestUrl(urls[1]);
        reqMsg.setVersion(urls[2]);

        String bodyLengthStr = reqMsg.getRequestHeader("Content-Length".toLowerCase());
        if (bodyLengthStr == null) {
            reqMsg.setErrorCode(400);
            out.write(reqMsg);
            return true;
        }

        int bodyLength = -1;
        try {
            bodyLength = Integer.parseInt(bodyLengthStr.trim());
        } catch (Exception e) {
            reqMsg.setErrorCode(400);
            out.write(reqMsg);
            return true;
        }

        String bodyContent = null;

        int packageLength = bodyLength + headerLength;

        if (content.getBytes("UTF-8").length >= packageLength) {
            ioBuffer.position(packageLength);
            int l = messages.length;
            byte[] bodyBytes = Arrays.copyOfRange(messages, headerLength, packageLength);
            String requestName = reqMsg.getRequestHeader("Request-Name".toLowerCase());
            if (requestName != null && !requestName.equals("")) {
                if (requestName.equals("hello")) {
                    HelloRequest helloRequest = HelloRequest.parseFrom(bodyBytes);

                }
            } else {
                bodyContent = new String(bodyBytes, "UTF-8");
            }
        } else {
            ioBuffer.position(start);
            return false;
        }
        reqMsg.setMessageBody(bodyContent);
        out.write(reqMsg);

        return true;
    }
}
