package io.github.pratikpanchal22.authserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pratikpanchal22.authserver.domain.AuditEvent;
import io.github.pratikpanchal22.authserver.repository.AuditEventRepository;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditEventRepository,
                        UserRepository userRepository,
                        ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public void log(String eventType, String email, HttpServletRequest request) {
        log(eventType, email, request, null);
    }

    public void log(String eventType, String email, HttpServletRequest request,
                    Map<String, String> extra) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(u -> event.setUserId(u.getId()));
        }
        if (request != null) {
            event.setIpAddress(extractIp(request));
            event.setUserAgent(request.getHeader("User-Agent"));
        }

        Map<String, String> meta = new LinkedHashMap<>();
        if (email != null) meta.put("email", email);
        if (extra != null) meta.putAll(extra);
        if (!meta.isEmpty()) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(meta));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit metadata for event {}: {}", eventType, e.getMessage());
            }
        }

        try {
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write audit event {}: {}", eventType, e.getMessage());
        }
    }

    private static String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
