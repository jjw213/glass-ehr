package com.glassehr.org;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * 조직 쓰기 직렬화. 순환 참조·기간 겹침 검사는 여러 부서의 행을 함께 읽고 판단하므로,
 * 두 변경이 동시에 각각 검사를 통과해 합치면 순환이 생기는 일을 막으려고 쓰기 트랜잭션마다 표 잠금을 건다.
 * Oracle 에서 읽기는 잠금에 막히지 않는다(트리 조회는 기다리지 않음). 조직 변경은 드물어 대기 비용은 작다.
 * 잠금은 트랜잭션이 끝날 때 풀린다.
 */
@Component
public class OrgWriteLock {

    @PersistenceContext
    private EntityManager em;

    /** 현재 트랜잭션 안에서 호출해야 한다. */
    public void acquire() {
        em.createNativeQuery("LOCK TABLE org_dept IN EXCLUSIVE MODE").executeUpdate();
    }
}
