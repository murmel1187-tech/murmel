package com.murmelsvisuals.util;

/**
 * Lets the GUI show whether a mixin hook really ran. A mixin that silently failed to apply (for
 * example after a Minecraft update renamed its target) would otherwise look like a "fake" feature.
 *
 * <p>Mixins call {@link #hit()} whenever they modified rendering; the client tick calls
 * {@link #expect(boolean)} while the situation that should trigger the hook is happening.</p>
 */
public final class HookStatus {
	public static final HookStatus LOW_FIRE = new HookStatus();
	public static final HookStatus SIDE_SHIELD = new HookStatus();

	private volatile long lastHitMs = 0;
	private volatile long expectedSinceMs = 0;

	public void hit() {
		lastHitMs = System.currentTimeMillis();
	}

	/** Called every tick: {@code true} while the hook should currently be firing. */
	public void expect(boolean expected) {
		if (!expected) expectedSinceMs = 0;
		else if (expectedSinceMs == 0) expectedSinceMs = System.currentTimeMillis();
	}

	public enum State { IDLE, ACTIVE, NOT_TRIGGERING }

	/**
	 * ACTIVE: ran recently. NOT_TRIGGERING: it should have run for 3+ s but never did (likely a failed
	 * mixin target - check the log). IDLE: nothing to report yet.
	 */
	public State state() {
		long now = System.currentTimeMillis();
		if (now - lastHitMs < 2500) return State.ACTIVE;
		if (expectedSinceMs != 0 && now - expectedSinceMs > 3000 && lastHitMs < expectedSinceMs) return State.NOT_TRIGGERING;
		return lastHitMs != 0 ? State.ACTIVE : State.IDLE;
	}

	public long lastHitMs() { return lastHitMs; }
}
