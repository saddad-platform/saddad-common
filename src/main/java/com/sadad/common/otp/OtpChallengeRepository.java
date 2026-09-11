package com.sadad.common.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /**
     * Every still-usable challenge for this subject, newest first.
     *
     * <p>Plural on purpose. One person can legitimately have two codes outstanding - a phone
     * and a laptop, or a request retried over a slow network - and a store that keeps only
     * the latest tells the first one it is wrong when it is not.
     */
    @Query("""
            SELECT c FROM OtpChallenge c
            WHERE c.purpose = :purpose AND c.subject = :subject
              AND c.consumedAt IS NULL AND c.expiresAt > :now
            ORDER BY c.createdAt DESC
            """)
    List<OtpChallenge> findUsable(@Param("purpose") String purpose,
                                  @Param("subject") String subject,
                                  @Param("now") Instant now);

    /** Any challenge for this subject, usable or not - to tell "wrong" from "expired". */
    @Query("""
            SELECT c FROM OtpChallenge c
            WHERE c.purpose = :purpose AND c.subject = :subject AND c.consumedAt IS NULL
            ORDER BY c.createdAt DESC
            """)
    List<OtpChallenge> findUnconsumed(@Param("purpose") String purpose, @Param("subject") String subject);

    /** Housekeeping: rows past their usefulness, which is every row an hour after expiry. */
    @Modifying
    @Query("DELETE FROM OtpChallenge c WHERE c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
