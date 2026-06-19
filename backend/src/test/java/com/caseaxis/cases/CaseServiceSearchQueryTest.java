package com.caseaxis.cases;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaseServiceSearchQueryTest {

    @Test
    void toPrefixTsQueryNormalizesTermsForToTsquery() {
        assertEquals("appeal:* & overdue:*", CaseService.toPrefixTsQuery(" Appeal, overdue!! "));
    }

    @Test
    void toPrefixTsQueryDropsUnsafePunctuation() {
        assertEquals("new:* & assigned:*", CaseService.toPrefixTsQuery("new:* | assigned;"));
    }

    @Test
    void toPrefixTsQueryReturnsNullForBlankOrPunctuationOnlyInput() {
        assertNull(CaseService.toPrefixTsQuery("   "));
        assertNull(CaseService.toPrefixTsQuery("!!!"));
    }

    @Test
    void normalizeCaseNumberOnlyAcceptsCanonicalCaseNumbers() {
        assertEquals("CA-000123", CaseService.normalizeCaseNumber(" ca-000123 "));
        assertNull(CaseService.normalizeCaseNumber("000123"));
        assertNull(CaseService.normalizeCaseNumber("CA-123"));
    }
}
