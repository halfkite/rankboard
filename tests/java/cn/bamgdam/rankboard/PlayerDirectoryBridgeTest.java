package cn.bamgdam.rankboard;

import java.util.UUID;

/** Dependency-free regression fixtures for named and production Fabric APIs. */
public final class PlayerDirectoryBridgeTest {
    private static final UUID ID = UUID.fromString("d5092546-a18a-4a00-9802-94e486a2573a");
    public record Profile(UUID id, String name) { }
    public record NameAndId(UUID id, String name) { }
    public static final class OldWhitelist {
        boolean allow;
        public boolean isAllowed(Profile profile) { return allow && valid(profile.id(), profile.name()); }
    }
    public static final class NewWhitelist {
        public boolean isWhiteListed(NameAndId entry) { return valid(entry.id(), entry.name()); }
    }
    public static final class FabricWhitelist {
        public boolean method_14653(NameAndId entry) { return valid(entry.id(), entry.name()); }
        public boolean contains(Object unrelated) { throw new AssertionError("wrong overload"); }
    }
    public static final class OldCache {
        Profile entry;
        int saves;
        public void add(Profile profile) { entry = profile; }
        public void save() { saves++; }
    }
    public static final class NewCache {
        NameAndId entry;
        int saves;
        public void method_14508(NameAndId profile) { entry = profile; }
        public void method_14518() { saves++; }
    }
    public static final class OldServer {
        final OldCache cache = new OldCache();
        public OldCache getUserCache() { return cache; }
    }
    public static final class NeoServer {
        final OldCache cache = new OldCache();
        public OldCache getProfileCache() { return cache; }
    }
    public static final class Services {
        final NewCache cache = new NewCache();
        public NewCache comp_4407() { return cache; }
    }
    public static final class NewServer {
        final Services services = new Services();
        public Services method_73550() { return services; }
    }
    public static final class NamedCache {
        NameAndId entry;
        int saves;
        public void add(NameAndId entry) { this.entry = entry; }
        public void save() { saves++; }
    }
    public static final class NamedServices {
        final NamedCache cache = new NamedCache();
        public NamedCache nameToIdCache() { return cache; }
    }
    public static final class NamedServer {
        final NamedServices services = new NamedServices();
        public NamedServices services() { return services; }
    }
    private static boolean valid(UUID id, String name) { return ID.equals(id) && name.equals("Smoke"); }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] arguments) {
        OldWhitelist old = new OldWhitelist();
        check(!PlayerDirectoryBridge.isAllowed(old, ID, "Smoke"));
        old.allow = true;
        check(PlayerDirectoryBridge.isAllowed(old, ID, "Smoke"));
        check(PlayerDirectoryBridge.isAllowed(new NewWhitelist(), ID, "Smoke"));
        check(PlayerDirectoryBridge.isAllowed(new FabricWhitelist(), ID, "Smoke"));
        check(!PlayerDirectoryBridge.isAllowed(new FabricWhitelist(), ID, "Other"));
        OldServer legacy = new OldServer();
        PlayerDirectoryBridge.cache(legacy, ID, "Smoke");
        PlayerDirectoryBridge.saveCache(legacy);
        check(valid(legacy.cache.entry.id(), legacy.cache.entry.name()) && legacy.cache.saves == 2);
        NeoServer neo = new NeoServer();
        PlayerDirectoryBridge.cache(neo, ID, "Smoke");
        check(neo.cache.saves == 1);
        NewServer modern = new NewServer();
        PlayerDirectoryBridge.cache(modern, ID, "Smoke");
        PlayerDirectoryBridge.saveCache(modern);
        check(valid(modern.services.cache.entry.id(), modern.services.cache.entry.name())
                && modern.services.cache.saves == 2);
        NamedServer named = new NamedServer();
        PlayerDirectoryBridge.cache(named, ID, "Smoke");
        PlayerDirectoryBridge.saveCache(named);
        check(valid(named.services.cache.entry.id(), named.services.cache.entry.name())
                && named.services.cache.saves == 2);
        try {
            PlayerDirectoryBridge.isAllowed(new Object(), ID, "Smoke");
            throw new AssertionError("Unsupported API must not bypass whitelist");
        } catch (IllegalStateException expected) { }
        System.out.println("PASS: old/new whitelist, denial, cache add/save, unmapped/named APIs");
    }
}
