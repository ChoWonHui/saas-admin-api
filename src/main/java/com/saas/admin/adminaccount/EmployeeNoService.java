package com.saas.admin.adminaccount;

import com.saas.admin.adminaccount.domain.EmployeeNoSeq;
import com.saas.admin.adminaccount.repository.EmployeeNoSeqRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 사번 채번. 형식은 <b>YY + 4자리 순번</b> — 260001 = 2026년 1번째.
 * <p>
 * 문자열로 다룬다. 숫자로 두면 2006년의 060001 에서 앞의 0 이 사라진다.
 */
@Service
@RequiredArgsConstructor
public class EmployeeNoService {

    private static final int MAX_SEQ_PER_YEAR = 9999;

    private final EmployeeNoSeqRepository seqRepository;

    /**
     * 올해의 다음 사번을 발급한다.
     * <p>
     * 채번 행을 FOR UPDATE 로 잠그므로, 동시 요청은 여기서 줄을 선다.
     * 호출자의 트랜잭션에 참여한다 — 관리자 생성이 실패하면 사번도 함께 롤백된다.
     * (번호에 구멍이 생기지 않는다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issue() {
        String yearPrefix = currentYearPrefix();

        EmployeeNoSeq seq = seqRepository.findForUpdate(yearPrefix)
                .orElseGet(() -> createYearRow(yearPrefix));

        int next = seq.next();
        if (next > MAX_SEQ_PER_YEAR) {
            throw new ApiException(ErrorCode.EMPLOYEE_NO_EXHAUSTED);
        }
        return yearPrefix + String.format("%04d", next);
    }

    private String currentYearPrefix() {
        return String.format("%02d", LocalDate.now().getYear() % 100);
    }

    /**
     * 그 해 첫 발급이면 채번 행이 아직 없다.
     * 동시에 둘이 첫 발급을 시도하면 한쪽이 PK 충돌로 실패하는데, 그때는 상대가 만든 행을 잠그고 읽는다.
     */
    private EmployeeNoSeq createYearRow(String yearPrefix) {
        try {
            return seqRepository.saveAndFlush(new EmployeeNoSeq(yearPrefix));
        } catch (DataIntegrityViolationException e) {
            return seqRepository.findForUpdate(yearPrefix)
                    .orElseThrow(() -> new ApiException(ErrorCode.EMPLOYEE_NO_GENERATION_FAILED));
        }
    }
}
