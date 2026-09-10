package com.origin173.schoolBedrockLink.util;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Limits bytes before accumulation and bounds the entire headers/body exchange. */
public final class LimitedHttpResponse {
    private LimitedHttpResponse() { }

    public static Response send(HttpClient client, HttpRequest request, int maxBytes)
            throws IOException, InterruptedException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        var future = client.sendAsync(request, info -> new LimitedSubscriber(maxBytes));
        try {
            var response = future.get(request.timeout().orElseThrow().toMillis(), TimeUnit.MILLISECONDS);
            return new Response(response.statusCode(), response.body());
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new HttpTimeoutException("Upstream response deadline exceeded");
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException failure) throw failure;
            throw new IOException("Upstream exchange failed", exception.getCause());
        }
    }

    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;
        private int received;
        private Flow.Subscription subscription;
        private boolean failed;

        private LimitedSubscriber(int maximum) { this.maximum = maximum; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }
        public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maximum - received) {
                    failed = true;
                    subscription.cancel();
                    delegate.onError(new ResponseTooLargeException());
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        public void onError(Throwable failure) { if (!failed) delegate.onError(failure); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }

    public record Response(int statusCode, byte[] body) { }
    public static final class ResponseTooLargeException extends IOException {
        public ResponseTooLargeException() { super("Upstream response size limit exceeded"); }
    }
}
