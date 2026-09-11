package com.sadad.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mapping language that lets an upstream contract change without a release.
 *
 * <p>Tested hard, because everything downstream trusts it: if this silently returns nothing,
 * an identity check reads as "not verified" and an applicant is refused for no reason; if it
 * silently returns the wrong thing, one reads as verified when it was not.
 */
class ResponseMapperTest {

    private final ResponseMapper mapper = new ResponseMapper(new ObjectMapper());

    // --- Locating a value ---------------------------------------------------------------

    @Test
    void readsAPlainField() {
        var result = mapper.map(Map.of("crName", "Acme Trading"),
                """
                {"entityNameEn": {"path": "crName"}}""");

        assertEquals("Acme Trading", result.text("entityNameEn"));
    }

    @Test
    void acceptsTheShorthandFormForTheCommonCase() {
        // An operator typing a mapping by hand should not have to write the long form to say
        // "it is just this field".
        var result = mapper.map(Map.of("crName", "Acme Trading"),
                """
                {"entityNameEn": "crName"}""");

        assertEquals("Acme Trading", result.text("entityNameEn"));
    }

    @Test
    void walksIntoNestedObjects() {
        var result = mapper.map(Map.of("address", Map.of("city", "Riyadh")),
                """
                {"cityEn": {"path": "address.city"}}""");

        assertEquals("Riyadh", result.text("cityEn"));
    }

    @Test
    void indexesIntoLists() {
        var result = mapper.map(Map.of("owners", List.of(Map.of("name", "Faisal"), Map.of("name", "Nora"))),
                """
                {"first": {"path": "owners[0].name"}, "second": {"path": "owners[1].name"}}""");

        assertEquals("Faisal", result.text("first"));
        assertEquals("Nora", result.text("second"));
    }

    @Test
    void fallsBackWhenAVendorUsesADifferentNameOnDifferentOperations() {
        // Wathq is the real example: the registration state arrives as `status` on one call
        // and `crStatus` on another. Handling that in configuration is the whole point.
        String mapping = """
                {"statusRaw": {"path": "status", "fallbackPaths": ["crStatus"]}}""";

        assertEquals("active", mapper.map(Map.of("status", "active"), mapping).text("statusRaw"));
        assertEquals("active", mapper.map(Map.of("crStatus", "active"), mapping).text("statusRaw"));
    }

    @Test
    void prefersThePrimaryPathOverAFallback() {
        var result = mapper.map(Map.of("status", "active", "crStatus", "cancelled"),
                """
                {"statusRaw": {"path": "status", "fallbackPaths": ["crStatus"]}}""");

        assertEquals("active", result.text("statusRaw"));
    }

    @Test
    void usesTheDefaultOnlyWhenNothingMatched() {
        String mapping = """
                {"cityEn": {"path": "city", "default": "unknown"}}""";

        assertEquals("unknown", mapper.map(Map.of(), mapping).text("cityEn"));
        assertEquals("Jeddah", mapper.map(Map.of("city", "Jeddah"), mapping).text("cityEn"));
    }

    // --- Recognising a value ------------------------------------------------------------

    @Test
    void treatsTheConfiguredValuesAsAffirmativeAndNothingElse() {
        String mapping = """
                {"active": {"path": "status", "truthy": ["active", "1"]}}""";

        assertTrue(mapper.map(Map.of("status", "active"), mapping).bool("active"));
        assertTrue(mapper.map(Map.of("status", "ACTIVE"), mapping).bool("active"), "case must not matter");
        assertTrue(mapper.map(Map.of("status", "1"), mapping).bool("active"));
        assertFalse(mapper.map(Map.of("status", "cancelled"), mapping).bool("active"));
        assertFalse(mapper.map(Map.of("status", "suspended"), mapping).bool("active"));
    }

    @Test
    void recognisesARealJsonBooleanAsWellAsATextualOne() {
        // Yaqeen answers with a JSON boolean; other registries answer "true" as a string.
        String mapping = """
                {"isOwner": {"path": "isOwner", "truthy": ["true"]}}""";

        assertTrue(mapper.map(Map.of("isOwner", true), mapping).bool("isOwner"));
        assertTrue(mapper.map(Map.of("isOwner", "true"), mapping).bool("isOwner"));
        assertFalse(mapper.map(Map.of("isOwner", false), mapping).bool("isOwner"));
    }

    @Test
    void aMissingFieldIsNotAffirmative() {
        // The safe direction. An absent answer must never read as a passed check.
        var result = mapper.map(Map.of("somethingElse", "active"),
                """
                {"active": {"path": "status", "truthy": ["active"]}}""");

        assertFalse(result.bool("active"));
    }

    // --- Failing safely -----------------------------------------------------------------

    @Test
    void anUnreadableMappingYieldsEmptyRatherThanThrowing() {
        // A stray character typed into an admin field must not take down an identity check.
        var result = mapper.map(Map.of("crName", "Acme"), "{ this is not json");

        assertTrue(result.isEmpty());
        assertFalse(result.bool("active"));
    }

    @Test
    void noMappingConfiguredIsDistinguishableFromAMappingThatFoundNothing() {
        // Callers rely on this difference: empty means "use the field names I was written
        // with", whereas a mapping that ran and found nothing means the check genuinely
        // failed. Collapsing the two would silently re-enable the hardcoded names on an
        // endpoint an operator had deliberately remapped.
        assertTrue(mapper.map(Map.of("a", 1), null).isEmpty());
        assertTrue(mapper.map(Map.of("a", 1), "").isEmpty());

        var ranButFoundNothing = mapper.map(Map.of("a", 1),
                """
                {"active": {"path": "missing", "truthy": ["yes"]}}""");
        assertFalse(ranButFoundNothing.isEmpty(), "the mapping ran; it produced a field");
        assertFalse(ranButFoundNothing.bool("active"));
    }

    @Test
    void aPathIntoTheWrongShapeIsSimplyNotFound() {
        var result = mapper.map(Map.of("status", "active"),
                """
                {"deep": {"path": "status.nested.value"}}""");

        assertNull(result.text("deep"));
    }

    @Test
    void anOutOfRangeListIndexIsSimplyNotFound() {
        var result = mapper.map(Map.of("owners", List.of(Map.of("name", "Faisal"))),
                """
                {"third": {"path": "owners[2].name"}}""");

        assertNull(result.text("third"));
    }

    @Test
    void aNullResponseIsHandled() {
        assertTrue(mapper.map(null, """
                {"a": {"path": "b"}}""").isEmpty());
    }

    @Test
    void oneBadFieldDoesNotDiscardTheGoodOnesBesideIt() {
        var result = mapper.map(Map.of("crName", "Acme Trading"),
                """
                {"entityNameEn": {"path": "crName"}, "broken": 12345}""");

        assertEquals("Acme Trading", result.text("entityNameEn"));
    }

    // --- The seeded Wathq mapping, end to end -------------------------------------------

    @Test
    void mapsARealisticWathqPayloadExactlyAsTheJavaConstantsDidBefore() {
        // The mapping seeded by V36, against a payload shaped like the one the hardcoded
        // provider read. This is the equivalence that makes the migration safe: same input,
        // same answer, no code involved.
        String seeded = """
                {"entityNameEn":{"path":"crName"},
                 "entityNameAr":{"path":"crNameAr"},
                 "cityEn":{"path":"city"},
                 "cityAr":{"path":"cityAr"},
                 "statusRaw":{"path":"status","fallbackPaths":["crStatus"]},
                 "active":{"path":"status","fallbackPaths":["crStatus"],"truthy":["active","1"]}}""";

        var result = mapper.map(Map.of(
                "crName", "Acme Trading Est.",
                "crNameAr", "مؤسسة أكمي للتجارة",
                "city", "Riyadh",
                "cityAr", "الرياض",
                "status", "active"), seeded);

        assertEquals("Acme Trading Est.", result.text("entityNameEn"));
        assertEquals("مؤسسة أكمي للتجارة", result.text("entityNameAr"));
        assertEquals("Riyadh", result.text("cityEn"));
        assertEquals("الرياض", result.text("cityAr"));
        assertEquals("active", result.text("statusRaw"));
        assertTrue(result.bool("active"));
    }

    @Test
    void theSameMappingRefusesACancelledRegistration() {
        String seeded = """
                {"statusRaw":{"path":"status","fallbackPaths":["crStatus"]},
                 "active":{"path":"status","fallbackPaths":["crStatus"],"truthy":["active","1"]}}""";

        var result = mapper.map(Map.of("crStatus", "cancelled"), seeded);

        assertEquals("cancelled", result.text("statusRaw"));
        assertFalse(result.bool("active"));
    }
}
