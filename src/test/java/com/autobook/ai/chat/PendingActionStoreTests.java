package com.autobook.ai.chat;

import com.autobook.ai.AiProperties;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.InvalidRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pending confirmations are single use, expire, and belong to one customer and one conversation.
 */
class PendingActionStoreTests {

	private final MutableClock clock = new MutableClock();
	private final PendingActionStore store = new PendingActionStore(new AiProperties(1000, 8, 10,
			Duration.ofMinutes(30), Duration.ofMinutes(5), new AiProperties.Faq(4, 0.6, "x"),
			new AiProperties.Reminders(true, Duration.ofHours(24), 25, Duration.ofHours(6))), clock);

	@Test
	void ownerCanClaimOnce() {
		var action = store.create(1, "conv", PendingActionStore.Type.BOOK, 42, "Book slot 42");

		assertEquals(42, store.claim(action.token(), 1, "conv").targetId());
		InvalidRequestException repeated = assertThrows(InvalidRequestException.class,
				() -> store.claim(action.token(), 1, "conv"));
		assertTrue(repeated.getMessage().contains("no longer valid"));
	}

	@Test
	void anotherCustomerCannotClaimAndDoesNotConsumeIt() {
		var action = store.create(1, "conv", PendingActionStore.Type.BOOK, 42, "Book slot 42");

		assertThrows(ForbiddenOperationException.class, () -> store.claim(action.token(), 2, "conv"));
		assertEquals(42, store.claim(action.token(), 1, "conv").targetId(), "owner can still confirm");
	}

	@Test
	void otherConversationCannotClaim() {
		var action = store.create(1, "conv", PendingActionStore.Type.CANCEL, 7, "Cancel 7");

		assertThrows(InvalidRequestException.class, () -> store.claim(action.token(), 1, "other"));
	}

	@Test
	void expiredActionIsRejected() {
		var action = store.create(1, "conv", PendingActionStore.Type.BOOK, 42, "Book slot 42");
		clock.advance(Duration.ofMinutes(5));

		InvalidRequestException ex = assertThrows(InvalidRequestException.class,
				() -> store.claim(action.token(), 1, "conv"));
		assertTrue(ex.getMessage().contains("expired"));
		assertTrue(store.current(1, "conv").isEmpty());
	}

	@Test
	void newActionReplacesTheConversationsPreviousOne() {
		var first = store.create(1, "conv", PendingActionStore.Type.BOOK, 1, "Book 1");
		var second = store.create(1, "conv", PendingActionStore.Type.BOOK, 2, "Book 2");

		assertNotEquals(first.token(), second.token());
		assertThrows(InvalidRequestException.class, () -> store.claim(first.token(), 1, "conv"));
		assertEquals(second.token(), store.current(1, "conv").orElseThrow().token());
	}

	@Test
	void unknownTokenIsRejected() {
		assertThrows(InvalidRequestException.class, () -> store.claim("made-up", 1, "conv"));
		assertThrows(InvalidRequestException.class, () -> store.claim(null, 1, "conv"));
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-17T18:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("America/Los_Angeles");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
