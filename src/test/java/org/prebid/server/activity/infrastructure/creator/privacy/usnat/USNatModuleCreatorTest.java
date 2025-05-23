package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class USNatModuleCreatorTest {

    @Mock(strictness = LENIENT)
    private USNatGppReaderFactory gppReaderFactory;

    @Mock(strictness = LENIENT)
    private Metrics metrics;

    private USNatModuleCreator target;

    @BeforeEach
    public void setUp() {
        given(gppReaderFactory.forSection(anyInt(), any())).willReturn(new USNationalGppReader(null));

        target = new USNatModuleCreator(gppReaderFactory, metrics, 0);
    }

    @Test
    public void qualifierShouldReturnExpectedResult() {
        // when and then
        assertThat(target.qualifier()).isEqualTo(PrivacyModuleQualifier.US_NAT);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfSectionsIdsIsNull() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(null, null);

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfSectionsIdsIsEmpty() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(emptyList(), null);

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfAllSectionsIdsSkipped() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(singletonList(1), null);

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldShouldSkipNotSupportedSectionsIds() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                asList(6, 7, 8, 9, 10, 11, 12, 13), null);

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), any());
        verify(gppReaderFactory).forSection(eq(8), any());
        verify(gppReaderFactory).forSection(eq(9), any());
        verify(gppReaderFactory).forSection(eq(10), any());
        verify(gppReaderFactory).forSection(eq(11), any());
        verify(gppReaderFactory).forSection(eq(12), any());
        verifyNoMoreInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldShouldSkipConfiguredSectionsIds() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(asList(7, 8, 9), asList(8, 9));

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), any());
        verifyNoMoreInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldShouldSkipSectionsWithInvalidGppSubstring() {
        // given
        given(gppReaderFactory.forSection(eq(7), any()))
                .willReturn(new USNationalGppReader(null) {

                    @Override
                    public Integer getMspaServiceProviderMode() {
                        throw new IllegalStateException();
                    }
                });

        final PrivacyModuleCreationContext creationContext = givenCreationContext(singletonList(7), emptyList());

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), any());
        verify(metrics).updateAlertsMetrics(eq(MetricName.general));

        verifyNoMoreInteractions(gppReaderFactory);
        verifyNoMoreInteractions(metrics);
    }

    private static PrivacyModuleCreationContext givenCreationContext(List<Integer> sectionsIds,
                                                                     List<Integer> skipSectionsIds) {

        return PrivacyModuleCreationContext.of(
                Activity.TRANSMIT_UFPD,
                AccountUSNatModuleConfig.of(true, 0, AccountUSNatModuleConfig.Config.of(skipSectionsIds, false)),
                GppContextCreator.from(null, sectionsIds).build().getGppContext());
    }
}
