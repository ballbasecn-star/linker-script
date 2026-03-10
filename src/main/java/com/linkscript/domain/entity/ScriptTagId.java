package com.linkscript.domain.entity;

import java.io.Serializable;
import java.util.Objects;

public class ScriptTagId implements Serializable {

    private String scriptUuid;
    private Long tagId;

    public ScriptTagId() {
    }

    public ScriptTagId(String scriptUuid, Long tagId) {
        this.scriptUuid = scriptUuid;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        ScriptTagId that = (ScriptTagId) other;
        return Objects.equals(scriptUuid, that.scriptUuid) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scriptUuid, tagId);
    }
}
