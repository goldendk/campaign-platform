package dev.campaign.json;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.Event;
import dev.campaign.kernel.api.Ruleset;
import dev.campaign.kernel.api.RulesetRegistry;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.Id;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.id.PlayerId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.function.Function;

/**
 * The only place that knows about JSON. Commands, events, views and stored events are serialised directly: the
 * kernel records ARE the wire format and the storage format, so there are no DTOs or mappers to maintain.
 *
 * - Ids are plain strings.
 * - Commands and events carry a "type" property such as "tiny-realms:MoveUnit" (ruleset id + record name), built from
 *   what each ruleset registered. Two rulesets can therefore both define "MoveUnit".
 *
 * Uses Jackson 2 deliberately: it keeps this module independent of Spring Boot 4's Jackson 3 default. If you later
 * move to Jackson 3, only this class and its test change.
 */
public final class CampaignJson {
    private final ObjectMapper mapper;

    public CampaignJson(Collection<? extends Ruleset> rulesets) {
        SimpleModule ids = new SimpleModule("campaign-ids");
        idType(ids, PlayerId.class, PlayerId::of);
        idType(ids, FactionId.class, FactionId::of);
        idType(ids, EntityId.class, EntityId::of);
        idType(ids, LocationId.class, LocationId::of);
        idType(ids, CampaignId.class, CampaignId::of);

        this.mapper = JsonMapper.builder()
                .addModule(ids)
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addMixIn(Command.class, Typed.class)
                .addMixIn(Event.class, Typed.class)
                .build();

        for (Ruleset ruleset : rulesets) {
            RulesetRegistry registry = RulesetRegistry.build(ruleset);
            registry.commandTypes().forEach((name, type) -> mapper.registerSubtypes(new NamedType(type, name)));
            registry.eventTypes().forEach((name, type) -> mapper.registerSubtypes(new NamedType(type, name)));
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    abstract static class Typed {}

    private static <T extends Id> void idType(SimpleModule module, Class<T> type, Function<String, T> factory) {
        module.addSerializer(type, ToStringSerializer.instance);
        module.addDeserializer(type, new FromStringDeserializer<T>(type) {
            @Override
            protected T _deserialize(String value, DeserializationContext context) {
                return factory.apply(value);
            }
        });
        module.addKeyDeserializer(type, new KeyDeserializer() {
            @Override
            public Object deserializeKey(String key, DeserializationContext context) {
                return factory.apply(key);
            }
        });
    }
}
