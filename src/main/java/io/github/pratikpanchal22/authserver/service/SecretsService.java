package io.github.pratikpanchal22.authserver.service;

import org.springframework.stereotype.Service;

/**
 * Resolves secret references. ARN-based refs are handled in PR-14 (AWS Secrets Manager);
 * for now, a non-ARN value is returned as-is (dev/test convenience).
 */
@Service
public class SecretsService {

    private static final String ARN_PREFIX = "arn:aws:secretsmanager:";

    public String resolve(String ref) {
        if (ref != null && ref.startsWith(ARN_PREFIX)) {
            // AWS Secrets Manager integration added in PR-14
            throw new UnsupportedOperationException(
                    "AWS Secrets Manager resolution not yet implemented. Ref: " + ref);
        }
        return ref;
    }
}
