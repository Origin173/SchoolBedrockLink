package com.origin173.schoolBedrockLink.link;

import com.origin173.schoolBedrockLink.audit.AuditLogger;
import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.floodgate.FloodgateMapping;
import com.origin173.schoolBedrockLink.floodgate.FloodgateService;
import com.origin173.schoolBedrockLink.floodgate.LinkOperations;
import com.origin173.schoolBedrockLink.floodgate.MappingLookup;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/** Coordinates the Approved Registry and Floodgate PlayerLink with one-to-one checks. */
public final class LinkService {

    private final ApprovedLinkRegistry registry;
    private final LinkOperations floodgate;
    private final AuditLogger audit;
    private final Logger logger;
    private final Duration timeout;
    private final Object operationLock = new Object();

    public LinkService(ApprovedLinkRegistry registry, LinkOperations floodgate,
                       AuditLogger audit, Logger logger, Duration timeout) {
        this.registry = registry;
        this.floodgate = floodgate;
        this.audit = audit;
        this.logger = logger;
        this.timeout = timeout;
    }

    public AccessOutcome checkAccess(BedrockIdentity identity, boolean autoRepair) {
        synchronized (operationLock) {
            if (!registry.isHealthy() || !floodgate.isReady()) {
                return new AccessOutcome(AccessStatus.NOT_READY, null);
            }

            ApprovedLink approved = registry.findByBedrock(identity.bedrockUuid());
            if (approved == null) {
                return new AccessOutcome(AccessStatus.UNAPPROVED, null);
            }
            if (!approved.xuid().equals(identity.xuid())) {
                audit.record("CONFLICT", "DENY", identity, approved.javaUuid(), approved.javaUsername());
                return new AccessOutcome(AccessStatus.CONFLICT, approved);
            }

            MappingLookup current = floodgate.getMapping(identity.bedrockUuid(), timeout);
            LinkPolicy.Decision decision = LinkPolicy.decide(approved,
                    current.mapping(), current.available(), autoRepair);
            if (decision == LinkPolicy.Decision.ALLOW) {
                return new AccessOutcome(AccessStatus.ALLOW, approved);
            }
            if (decision == LinkPolicy.Decision.CONFLICT) {
                audit.record("CONFLICT", "DENY", identity, current.mapping().javaUuid(),
                        current.mapping().javaUsername());
                return new AccessOutcome(AccessStatus.CONFLICT, approved);
            }
            if (decision != LinkPolicy.Decision.REPAIR_REQUIRED) {
                return new AccessOutcome(AccessStatus.NOT_READY, approved);
            }

            boolean linked = floodgate.linkPlayer(identity.bedrockUuid(), approved.javaUuid(),
                    approved.javaUsername(), timeout);
            if (!linked) {
                logger.warning("Floodgate mapping repair failed; Bedrock access remains denied.");
                return new AccessOutcome(AccessStatus.NOT_READY, approved);
            }

            MappingLookup repaired = floodgate.getMapping(identity.bedrockUuid(), timeout);
            if (!repaired.available()) {
                return new AccessOutcome(AccessStatus.NOT_READY, approved);
            }
            if (repaired.mapping() == null) {
                logger.warning("Floodgate mapping repair returned no mapping; Bedrock access remains denied.");
                return new AccessOutcome(AccessStatus.NOT_READY, approved);
            }
            if (!approved.javaUuid().equals(repaired.mapping().javaUuid())) {
                audit.record("CONFLICT", "DENY", identity, repaired.mapping().javaUuid(),
                        repaired.mapping().javaUsername());
                return new AccessOutcome(AccessStatus.CONFLICT, approved);
            }
            audit.record("REPAIR", "SUCCESS", identity, approved.javaUuid(), approved.javaUsername());
            return new AccessOutcome(AccessStatus.REPAIRED, approved);
        }
    }

    public BindOutcome bind(BedrockIdentity identity, com.origin173.schoolBedrockLink.oauth.MinecraftProfile profile) {
        synchronized (operationLock) {
            if (!registry.isHealthy() || !floodgate.isReady()) {
                return new BindOutcome(BindStatus.NOT_READY, null);
            }

            ApprovedLink approved = registry.findByBedrock(identity.bedrockUuid());
            if (approved != null && !approved.xuid().equals(identity.xuid())) {
                audit.record("CONFLICT", "DENY", identity, approved.javaUuid(), approved.javaUsername());
                return new BindOutcome(BindStatus.CONFLICT, approved);
            }

            ApprovedLink javaOwner = registry.findByJava(profile.uuid());
            if (javaOwner != null && !javaOwner.bedrockUuid().equals(identity.bedrockUuid())) {
                audit.record("CONFLICT", "DENY", identity, profile.uuid(), profile.username());
                return new BindOutcome(BindStatus.CONFLICT, javaOwner);
            }
            if (approved != null && !approved.javaUuid().equals(profile.uuid())) {
                audit.record("CONFLICT", "DENY", identity, profile.uuid(), profile.username());
                return new BindOutcome(BindStatus.CONFLICT, approved);
            }

            MappingLookup before = floodgate.getMapping(identity.bedrockUuid(), timeout);
            if (!before.available()) {
                return new BindOutcome(BindStatus.NOT_READY, approved);
            }
            if (before.mapping() != null && !before.mapping().javaUuid().equals(profile.uuid())) {
                audit.record("CONFLICT", "DENY", identity, before.mapping().javaUuid(),
                        before.mapping().javaUsername());
                return new BindOutcome(BindStatus.CONFLICT, approved);
            }

            boolean alreadyCorrect = approved != null
                    && before.mapping() != null
                    && approved.javaUuid().equals(profile.uuid());
            if (!alreadyCorrect) {
                // The API call is intentionally made only after all registry and
                // current-mapping checks, and before the Approved Record is written.
                if (!floodgate.linkPlayer(identity.bedrockUuid(), profile.uuid(), profile.username(), timeout)) {
                    logger.warning("Floodgate linkPlayer failed; Approved Registry was not written.");
                    return new BindOutcome(BindStatus.FAILED, approved);
                }

                MappingLookup after = floodgate.getMapping(identity.bedrockUuid(), timeout);
                if (!after.available() || after.mapping() == null) {
                    logger.warning("Floodgate linkPlayer completed without a verifiable mapping.");
                    return new BindOutcome(BindStatus.FAILED, approved);
                }
                if (!after.mapping().javaUuid().equals(profile.uuid())) {
                    audit.record("CONFLICT", "DENY", identity, after.mapping().javaUuid(),
                            after.mapping().javaUsername());
                    return new BindOutcome(BindStatus.CONFLICT, approved);
                }
            }

            Instant linkedAt = approved == null ? Instant.now() : approved.linkedAt();
            ApprovedLink saved = new ApprovedLink(identity.bedrockUuid(), identity.xuid(), identity.gamertag(),
                    profile.uuid(), profile.username(), linkedAt);
            if (!saved.equals(approved) && !registry.save(saved)) {
                logger.severe("Floodgate mapping exists but Approved Registry could not be written; access remains fail closed.");
                return new BindOutcome(BindStatus.FAILED, approved);
            }
            audit.record("BIND", "SUCCESS", identity, profile.uuid(), profile.username());
            return new BindOutcome(approved == null ? BindStatus.SUCCESS : BindStatus.IDEMPOTENT, saved);
        }
    }

    public UnlinkOutcome unlink(ApprovedLink approved) {
        synchronized (operationLock) {
            if (!registry.isHealthy() || !floodgate.isReady()) {
                return new UnlinkOutcome(UnlinkStatus.NOT_READY);
            }
            if (!approved.equals(registry.findByBedrock(approved.bedrockUuid()))) {
                return new UnlinkOutcome(UnlinkStatus.CONFLICT);
            }
            MappingLookup before = floodgate.getMapping(approved.bedrockUuid(), timeout);
            if (!before.available()) {
                return new UnlinkOutcome(UnlinkStatus.NOT_READY);
            }
            if (before.mapping() != null && !before.mapping().javaUuid().equals(approved.javaUuid())) {
                audit.record("CONFLICT", "DENY", new BedrockIdentity(approved.bedrockUuid(), approved.xuid(),
                        approved.gamertag()), before.mapping().javaUuid(), before.mapping().javaUsername());
                return new UnlinkOutcome(UnlinkStatus.CONFLICT);
            }
            if (!floodgate.unlinkPlayer(approved.javaUuid(), timeout)) {
                return new UnlinkOutcome(UnlinkStatus.FAILED);
            }
            MappingLookup after = floodgate.getMapping(approved.bedrockUuid(), timeout);
            if (!after.available() || after.mapping() != null) {
                return new UnlinkOutcome(UnlinkStatus.FAILED);
            }
            if (!registry.removeByBedrock(approved.bedrockUuid())) {
                logger.severe("Floodgate was unlinked but Approved Registry could not be updated.");
                return new UnlinkOutcome(UnlinkStatus.FAILED);
            }
            audit.record("UNLINK", "SUCCESS", new BedrockIdentity(approved.bedrockUuid(), approved.xuid(),
                    approved.gamertag()), approved.javaUuid(), approved.javaUsername());
            return new UnlinkOutcome(UnlinkStatus.SUCCESS);
        }
    }

    public enum AccessStatus {
        ALLOW,
        REPAIRED,
        UNAPPROVED,
        CONFLICT,
        NOT_READY
    }

    public record AccessOutcome(AccessStatus status, ApprovedLink approved) {
    }

    public enum BindStatus {
        SUCCESS,
        IDEMPOTENT,
        CONFLICT,
        NOT_READY,
        FAILED
    }

    public record BindOutcome(BindStatus status, ApprovedLink approved) {
    }

    public enum UnlinkStatus {
        SUCCESS,
        CONFLICT,
        NOT_READY,
        FAILED
    }

    public record UnlinkOutcome(UnlinkStatus status) {
    }
}
