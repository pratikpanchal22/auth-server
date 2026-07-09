package io.github.pratikpanchal22.authserver.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecretsService {

    private static final String ARN_PREFIX = "arn:aws:secretsmanager:";

    private volatile SecretsManagerClient client;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String resolve(String ref) {
        if (ref != null && ref.startsWith(ARN_PREFIX)) {
            return cache.computeIfAbsent(ref, arn ->
                    client().getSecretValue(GetSecretValueRequest.builder().secretId(arn).build())
                            .secretString());
        }
        return ref;
    }

    private SecretsManagerClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = SecretsManagerClient.create();
                }
            }
        }
        return client;
    }
}
