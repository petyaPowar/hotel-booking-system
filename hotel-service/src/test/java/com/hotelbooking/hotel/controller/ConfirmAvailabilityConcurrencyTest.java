package com.hotelbooking.hotel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hotelbooking.hotel.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ConfirmAvailabilityConcurrencyTest extends IntegrationTest {

    private static final int THREADS = 10;

    @Test
    void parallelConfirmations_forOverlappingDates_allowExactlyOneHold() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        String token = tokens.internal();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> attempts = IntStream.range(0, THREADS)
                .mapToObj(index -> confirmAttempt(room.getId(), index, token, start))
                .toList();

        List<Integer> statuses = runConcurrently(attempts, start);

        assertThat(statuses).filteredOn(code -> code == HttpStatus.OK.value()).hasSize(1);
        assertThat(statuses)
                .filteredOn(code -> code == HttpStatus.CONFLICT.value())
                .hasSize(THREADS - 1);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reload(room).getTimesBooked()).isEqualTo(1);
    }

    private Callable<Integer> confirmAttempt(Long roomId, int index, String token, CountDownLatch start) {
        ConfirmAvailabilityRequest request = new ConfirmAvailabilityRequest(
                "concurrent-" + index, 100L + index, BASE_DATE.plusDays(index % 2), BASE_DATE.plusDays(3));
        return () -> {
            start.await();
            return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> attempts, CountDownLatch start) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Integer>> futures =
                    attempts.stream().map(executor::submit).toList();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }
}
