package io.github.pratikpanchal22.authserver.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecretsService {

    private static final String ARN_PREFIX = "arn:aws:secretsmanager:";

    private final SecretsManagerClient client;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public SecretsService() {
        this.client = SecretsManagerClient.create();
    }

    public String resolve(String ref) {
        if (ref != null && ref.startsWith(ARN_PREFIX)) {
            return cache.computeIfAbsent(ref, arn ->
                    client.getSecretValue(GetSecretValueRequest.builder().secretId(arn).build())
                          .secretString());
        }
        return ref;
    }
}
