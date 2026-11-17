package br.com.lotolab.contests.application.port.out;

/** Outbound effect preserving the existing post-registration research behavior. */
public interface ContestResearchTrigger {
    boolean trigger(String origin);
}
