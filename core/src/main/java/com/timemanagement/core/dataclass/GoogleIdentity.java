package com.timemanagement.core.dataclass;

public class GoogleIdentity {
    private String subjectId;
    private String email;
    private String displayName;

    public GoogleIdentity() {
    }

    public GoogleIdentity(String subjectId, String email, String displayName) {
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
