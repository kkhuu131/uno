package com.kkhuu131.uno.backend.support;

import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.model.GameState;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Registers a {@link GameState} in a {@link GameSessionService} for tests without exposing production APIs.
 * Prefer this over widening visibility of the service’s map.
 */
public final class TestGames {

	private TestGames() {}

	@SuppressWarnings("unchecked")
	public static void register(GameSessionService service, String gameId, GameState state) {
		try {
			Field f = GameSessionService.class.getDeclaredField("games");
			f.setAccessible(true);
			Map<String, GameState> map = (Map<String, GameState>) f.get(service);
			map.put(gameId, state);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to inject game into GameSessionService", e);
		}
	}
}
