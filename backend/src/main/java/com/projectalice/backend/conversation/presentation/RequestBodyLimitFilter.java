package com.projectalice.backend.conversation.presentation;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final long limit;
    public RequestBodyLimitFilter(@Value("${alice.api.conversation.max-request-body-size:131072}") long limit) {
        if (limit < 131_072) throw new IllegalArgumentException("conversation request body limit must be at least 131072 bytes");
        this.limit = limit;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v1/conversation/messages".equals(request.getRequestURI());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > limit) { sendTooLarge(request,response); return; }
        try { chain.doFilter(new LimitedRequest(request,limit),response); }
        catch (PayloadTooLargeIOException tooLarge) { sendTooLarge(request,response); }
    }
    private static void sendTooLarge(HttpServletRequest request,HttpServletResponse response) throws IOException {
        Object requestId=request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String id=requestId == null ? UUID.randomUUID().toString() : requestId.toString();
        response.setStatus(413); response.setContentType("application/problem+json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Request-Id",id);
        response.getWriter().write("{\"type\":\"urn:project-alice:problem:payload-too-large\",\"title\":\"Payload too large\",\"status\":413,\"detail\":\"Request Bodyが上限を超えています。\",\"code\":\"PAYLOAD_TOO_LARGE\",\"requestId\":\""+id+"\"}");
    }
    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private ServletInputStream stream; private final long limit;
        LimitedRequest(HttpServletRequest request,long limit) { super(request); this.limit=limit; }
        @Override public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new LimitedStream(super.getInputStream(),limit);
            return stream;
        }
    }
    private static final class LimitedStream extends ServletInputStream {
        private final ServletInputStream delegate; private final long limit; private long read;
        LimitedStream(ServletInputStream delegate,long limit) { this.delegate=delegate; this.limit=limit; }
        @Override public int read() throws IOException { int value=delegate.read(); if (value >= 0 && ++read > limit) throw new PayloadTooLargeIOException(); return value; }
        @Override public int read(byte[] bytes,int offset,int length) throws IOException {
            int allowed=(int)Math.min(length,limit-read+1); if (allowed <= 0) throw new PayloadTooLargeIOException();
            int amount=delegate.read(bytes,offset,allowed); if (amount > 0 && (read+=amount)>limit) throw new PayloadTooLargeIOException(); return amount;
        }
        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }
}
