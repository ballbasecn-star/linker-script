package com.linkscript.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ls_script")
public class ScriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_uuid", nullable = false, unique = true, length = 64)
    private String scriptUuid;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "source_platform", length = 32)
    private String sourcePlatform;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats_json", columnDefinition = "JSONB")
    private String statsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_json", columnDefinition = "JSONB")
    private String reviewJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScriptStatus status;

    @Column(name = "heat_score")
    private Double heatScore;

    @Column(name = "heat_level", length = 2)
    private String heatLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getScriptUuid() {
        return scriptUuid;
    }

    public void setScriptUuid(String scriptUuid) {
        this.scriptUuid = scriptUuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getStatsJson() {
        return statsJson;
    }

    public void setStatsJson(String statsJson) {
        this.statsJson = statsJson;
    }

    public ScriptStatus getStatus() {
        return status;
    }

    public void setStatus(ScriptStatus status) {
        this.status = status;
    }

    public String getReviewJson() {
        return reviewJson;
    }

    public void setReviewJson(String reviewJson) {
        this.reviewJson = reviewJson;
    }

    public Double getHeatScore() {
        return heatScore;
    }

    public void setHeatScore(Double heatScore) {
        this.heatScore = heatScore;
    }

    public String getHeatLevel() {
        return heatLevel;
    }

    public void setHeatLevel(String heatLevel) {
        this.heatLevel = heatLevel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
