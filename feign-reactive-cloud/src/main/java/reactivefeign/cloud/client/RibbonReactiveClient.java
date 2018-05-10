package reactivefeign.cloud.client;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.MethodMetadata;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactivefeign.client.ReactiveHttpClient;
import reactivefeign.client.ReactiveHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Sergii Karpenko
 */
public class RibbonReactiveClient implements ReactiveHttpClient {

    private final LoadBalancerCommand<Object> loadBalancerCommand;
    private final ReactiveHttpClient reactiveClient;
    private final Type returnPublisherType;

    public RibbonReactiveClient(MethodMetadata metadata,
                                @Nullable
                                        LoadBalancerCommand<Object> loadBalancerCommand,
                                ReactiveHttpClient reactiveClient) {
        this.loadBalancerCommand = loadBalancerCommand;
        this.reactiveClient = reactiveClient;

        returnPublisherType = ((ParameterizedType) metadata.returnType()).getRawType();
    }

    @Override
    public Publisher<Object> executeRequest(ReactiveHttpRequest request) {

        if (loadBalancerCommand != null) {
            Observable<Object> observable = loadBalancerCommand.submit(server -> {

                ReactiveHttpRequest lbRequest = loadBalanceRequest(request, server);

                return RxReactiveStreams.toObservable(reactiveClient.executeRequest(lbRequest));
            });

            Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

            return returnPublisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
        } else {
            return reactiveClient.executeRequest(request);
        }
    }

    protected ReactiveHttpRequest loadBalanceRequest(ReactiveHttpRequest request, Server server) {
        URI uri = request.uri();
        try {
            URI lbUrl = new URI(uri.getScheme(), uri.getUserInfo(), server.getHost(), server.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
            return new ReactiveHttpRequest(request.method(), lbUrl, request.headers(), request.body());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
