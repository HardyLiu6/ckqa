package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMetadataLookupTest {

    private final CandidateMetadataLookup lookup = new CandidateMetadataLookup();

    @Test
    void recognizesAllFourKnownCandidates() {
        for (String id : List.of(
                "default",
                "auto_tuned",
                "schema_aware_directional_v2",
                "schema_fewshot_distilled_v2_strict_tuple"
        )) {
            assertThat(lookup.isKnown(id)).as("known: %s", id).isTrue();
            assertThat(lookup.displayNameZh(id)).as("displayNameZh: %s", id).isNotBlank();
            assertThat(lookup.category(id)).as("category: %s", id).isNotBlank();
            assertThat(lookup.description(id)).as("description: %s", id).isNotBlank();
            assertThat(lookup.traits(id)).as("traits: %s", id).isNotEmpty();
        }
    }

    @Test
    void onlyDistilledV2StrictTupleIsRecommended() {
        assertThat(lookup.isRecommended("schema_fewshot_distilled_v2_strict_tuple")).isTrue();
        assertThat(lookup.isRecommended("default")).isFalse();
        assertThat(lookup.isRecommended("auto_tuned")).isFalse();
        assertThat(lookup.isRecommended("schema_aware_directional_v2")).isFalse();
    }

    @Test
    void unknownCandidateReturnsNullsAndEmpty() {
        assertThat(lookup.isKnown("unknown_candidate")).isFalse();
        assertThat(lookup.displayNameZh("unknown_candidate")).isNull();
        assertThat(lookup.category("unknown_candidate")).isNull();
        assertThat(lookup.description("unknown_candidate")).isNull();
        assertThat(lookup.isRecommended("unknown_candidate")).isFalse();
        assertThat(lookup.traits("unknown_candidate")).isEmpty();
    }

    @Test
    void categoryMatchesSpecValues() {
        // spec § "候选译名映射"
        assertThat(lookup.category("default")).isEqualTo("baseline");
        assertThat(lookup.category("auto_tuned")).isEqualTo("auto_tuned");
        assertThat(lookup.category("schema_aware_directional_v2")).isEqualTo("schema_aware");
        assertThat(lookup.category("schema_fewshot_distilled_v2_strict_tuple")).isEqualTo("schema_fewshot");
    }

    @Test
    void traitsContainStableKeysAndChineseLabels() {
        List<CandidateResponse.TraitInfo> distilled = lookup.traits("schema_fewshot_distilled_v2_strict_tuple");
        assertThat(distilled).extracting(CandidateResponse.TraitInfo::getKey)
                .contains("schema_injected", "few_shot_distilled", "strict_tuple");
        assertThat(distilled).allSatisfy(t -> {
            assertThat(t.getKey()).isNotBlank();
            assertThat(t.getLabel()).isNotBlank();
        });
    }

    @Test
    void knownCandidateIdsExposesAllFour() {
        assertThat(lookup.knownCandidateIds())
                .containsExactlyInAnyOrder(
                        "default",
                        "auto_tuned",
                        "schema_aware_directional_v2",
                        "schema_fewshot_distilled_v2_strict_tuple"
                );
    }
}
