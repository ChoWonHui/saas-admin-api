package com.saas.admin.mailbox.repository;

import com.saas.admin.mailbox.domain.MailFolder;
import com.saas.admin.mailbox.domain.MailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MailMessageRepository extends JpaRepository<MailMessage, Long> {

    /**
     * 한 관리자가 보는 폴더 목록.
     * <p>
     * 자기 앞으로 온 것(owner = 사번)만 본다.
     * 받는 주소의 앞부분이 자기 사번인 메일만 그 사람 것이며, 사번과 매칭되지 않는
     * 주소(공용/기타)로 온 메일은 각자의 메일함에 보이지 않는다.
     */
    @Query("""
            select m from MailMessage m
            where m.ownerEmpNo = :empNo
              and m.folder = :folder
              and (:keyword is null
                   or lower(m.subject) like lower(concat('%', :keyword, '%'))
                   or lower(m.fromAddress) like lower(concat('%', :keyword, '%'))
                   or lower(m.toAddress) like lower(concat('%', :keyword, '%')))
            order by m.sentAt desc, m.id desc
            """)
    Page<MailMessage> findBox(String empNo, MailFolder folder, String keyword, Pageable pageable);

    @Query("""
            select count(m) from MailMessage m
            where m.ownerEmpNo = :empNo
              and m.folder = :folder
            """)
    long countBox(String empNo, MailFolder folder);

    @Query("""
            select count(m) from MailMessage m
            where m.ownerEmpNo = :empNo
              and m.folder = :folder and m.unread = true
            """)
    long countUnread(String empNo, MailFolder folder);

    boolean existsByMessageId(String messageId);

    /** 마지막으로 읽어온 IMAP UID. 다음 동기화가 그 뒤부터 가져간다. */
    @Query("select coalesce(max(m.imapUid), 0) from MailMessage m where m.imapUid is not null")
    long maxImapUid();
}
