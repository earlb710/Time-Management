package com.timemanagement.core.dataclass;

public class MicrosoftIdentity {
    private String subjectId;
    private String email;
    private String displayName;

    public MicrosoftIdentity() {
    }

    public MicrosoftIdentity(String subjectId, String email, String displayName) {
        this.subjectId = subjectId;
        this.email = email;
        this.displayName = displayName;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }
}
