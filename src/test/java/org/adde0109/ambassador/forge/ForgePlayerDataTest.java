package org.adde0109.ambassador.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ForgePlayerDataTest {

  private record PlayerSession(Player player, AtomicBoolean active) {
    void close() {
      active.set(false);
    }
  }

  private static PlayerSession session(UUID accountId) {
    AtomicBoolean active = new AtomicBoolean(true);
    Player[] self = new Player[1];
    self[0] = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "getUniqueId" -> accountId;
          case "getUsername" -> "Steve";
          case "isActive" -> active.get();
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> "session of " + accountId;
          default -> throw new UnsupportedOperationException(method.getName());
        });
    return new PlayerSession(self[0], active);
  }

  @Test
  void theFirstSessionOfAnAccountClaimsItsHandshakeData() {
    PlayerSession first = session(UUID.randomUUID());

    assertTrue(ForgePlayerData.claim(first.player()));
    assertSame(ForgePlayerData.get(first.player()), ForgePlayerData.get(first.player()));
  }

  @Test
  void aSecondSessionOfTheSameAccountCannotClaimWhileTheFirstIsActive() {
    UUID accountId = UUID.randomUUID();
    PlayerSession first = session(accountId);
    PlayerSession second = session(accountId);
    ForgePlayerData.claim(first.player());

    assertFalse(ForgePlayerData.claim(second.player()));
    assertThrows(IllegalStateException.class, () -> ForgePlayerData.get(second.player()));
  }

  @Test
  void theActiveHolderIsReportedToOtherSessionsButNotToItself() {
    UUID accountId = UUID.randomUUID();
    PlayerSession first = session(accountId);
    PlayerSession second = session(accountId);
    ForgePlayerData.claim(first.player());

    assertEquals(Optional.of(first.player()), ForgePlayerData.findOtherActiveHolder(second.player()));
    assertEquals(Optional.empty(), ForgePlayerData.findOtherActiveHolder(first.player()));
  }

  @Test
  void aNewSessionTakesOverOnceTheHolderIsNoLongerActive() {
    UUID accountId = UUID.randomUUID();
    PlayerSession first = session(accountId);
    PlayerSession second = session(accountId);
    ForgePlayerData.claim(first.player());
    first.close();

    assertEquals(Optional.empty(), ForgePlayerData.findOtherActiveHolder(second.player()));
    assertTrue(ForgePlayerData.claim(second.player()));
    assertThrows(IllegalStateException.class, () -> ForgePlayerData.get(first.player()));
  }

  @Test
  void aRefusedSessionDisconnectingKeepsTheHoldersHandshakeData() {
    UUID accountId = UUID.randomUUID();
    PlayerSession first = session(accountId);
    PlayerSession second = session(accountId);
    ForgePlayerData.claim(first.player());
    ForgePlayerData firstData = ForgePlayerData.get(first.player());
    ForgePlayerData.claim(second.player());

    ForgePlayerData.remove(second.player());

    assertSame(firstData, ForgePlayerData.get(first.player()));
  }

  @Test
  void theHolderDisconnectingRemovesItsData() {
    PlayerSession first = session(UUID.randomUUID());
    ForgePlayerData.claim(first.player());

    ForgePlayerData.remove(first.player());

    assertThrows(IllegalStateException.class, () -> ForgePlayerData.get(first.player()));
  }

  @Test
  void takeOverReplacesAnActiveHolder() {
    UUID accountId = UUID.randomUUID();
    PlayerSession first = session(accountId);
    PlayerSession second = session(accountId);
    ForgePlayerData.claim(first.player());

    ForgePlayerData.takeOver(second.player());

    assertThrows(IllegalStateException.class, () -> ForgePlayerData.get(first.player()));
    assertTrue(ForgePlayerData.claim(second.player()));
  }

  @Test
  void readingNeverCreatesData() {
    PlayerSession unknown = session(UUID.randomUUID());

    assertThrows(IllegalStateException.class, () -> ForgePlayerData.get(unknown.player()));
    assertEquals(Optional.empty(), ForgePlayerData.findOtherActiveHolder(unknown.player()));
  }
}
