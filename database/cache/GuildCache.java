package atavism.agis.database.cache;

import java.time.Duration;
import java.util.Set;

import com.github.benmanes.caffeine.cache.*;

import atavism.agis.database.AccountDatabase;
import atavism.server.engine.OID;

public class GuildCache {

    private final LoadingCache<Integer, Set<OID>> cache;

    public GuildCache(AccountDatabase aDB) {
        cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5))
                .refreshAfterWrite(Duration.ofMinutes(2)).build(key -> aDB.getGuildMambersOid(key));
    }

    public Set<OID> getGuildMambersOid(int guildId) {
        return cache.get(guildId);
    }

}
