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

@Entity
@Table(name = "ls_logic_fragment")
public class LogicFragmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_uuid", nullable = false, length = 64)
    private String scriptUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "f_type", nullable = false, length = 20)
    private FragmentType fragmentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "logic_desc", columnDefinition = "TEXT")
    private String logicDesc;

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

    public FragmentType getFragmentType() {
        return fragmentType;
    }

    public void setFragmentType(FragmentType fragmentType) {
        this.fragmentType = fragmentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLogicDesc() {
        return logicDesc;
    }

    public void setLogicDesc(String logicDesc) {
        this.logicDesc = logicDesc;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
