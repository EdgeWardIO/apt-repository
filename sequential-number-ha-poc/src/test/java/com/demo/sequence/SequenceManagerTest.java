package com.demo.sequence;

import com.demo.sequence.model.SequenceRequest;
import com.demo.sequence.model.SequenceResponse;
import com.demo.sequence.model.SequenceStats;
import com.demo.sequence.service.EtcdSequenceManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic test for Sequential Number Generation POC
 */
@SpringBootTest
@ActiveProfiles("test")
public class SequenceManagerTest {

    @Test
    public void contextLoads() {
        // Simple test to ensure Spring Boot context loads
        assertThat(true).isTrue();
    }

    @Test
    public void sequenceRequestValidation() {
        SequenceRequest request = new SequenceRequest("site-1", "partition-a", "on-cycle");
        assertThat(request.getSiteId()).isEqualTo("site-1");
        assertThat(request.getPartitionId()).isEqualTo("partition-a");
        assertThat(request.getInvoiceType()).isEqualTo("on-cycle");
        assertThat(request.getCount()).isEqualTo(1);
    }

    @Test
    public void sequenceResponseCreation() {
        SequenceResponse response = SequenceResponse.success(java.util.List.of(1L, 2L, 3L), 3L);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSequenceNumbers()).hasSize(3);
        assertThat(response.getGlobalCounter()).isEqualTo(3L);
    }
}