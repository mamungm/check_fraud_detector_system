package com.research.fraud.service;

import com.research.fraud.dto.EnumFraudDisposition;
import org.json.JSONObject;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ActionService {
    private final KafkaTemplate<String, String> kafka;

    public ActionService(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public void fraudDispositionAction(UUID eventId, EnumFraudDisposition fraudDisposition) {
        JSONObject object = new JSONObject();
        object.put("eventId", eventId);
        object.put("fraudDisposition", fraudDisposition.toString());

        kafka.send("check.deposit.fraud.decision", eventId.toString(),
                object.toString());
    }
}
