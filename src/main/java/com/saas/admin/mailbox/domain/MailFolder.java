package com.saas.admin.mailbox.domain;

/**
 * 메일함 폴더.
 * <p>
 * 값을 추가하려면 DB 도 같이 고쳐야 한다. Hibernate 가 MySQL 에 네이티브 ENUM 으로 만들기 때문에
 * {@code ddl-auto: update} 로는 기존 컬럼이 바뀌지 않는다.
 * <pre>
 * ALTER TABLE mail_message MODIFY folder ENUM('INBOX','SENT','DRAFT','TRASH','ARCHIVE') NOT NULL;
 * </pre>
 */
public enum MailFolder {
    INBOX("받은편지함"),
    SENT("보낸편지함"),
    DRAFT("임시보관함"),
    TRASH("휴지통");

    private final String label;

    MailFolder(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
