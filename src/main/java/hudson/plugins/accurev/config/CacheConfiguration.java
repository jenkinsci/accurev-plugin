package hudson.plugins.accurev.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.extensions.impl.AccurevDepot;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author josp
 */
public class CacheConfiguration<K,V,E extends Exception> {
    private final int size;
    private final int ttl;

    private transient final Cache<String, Map<String, AccurevDepot>> accurevDepots;

    private transient final Cache<String, Map<String, AccurevStream>> accurevStreams;

    @DataBoundConstructor
    public CacheConfiguration(int size, int ttl) {
        this.size = Math.max(0, Math.min(size, 1000));
        this.ttl = Math.max(0, Math.min(ttl, 3600));

        this.accurevDepots = CacheBuilder.newBuilder()
                .maximumSize(getSize())
                .expireAfterWrite(getTtl(), TimeUnit.SECONDS)
                .build();

        this.accurevStreams = CacheBuilder.newBuilder()
                .maximumSize(getSize())
                .expireAfterWrite(getTtl(), TimeUnit.SECONDS)
                .build();
    }

    public int getSize() {
        return size;
    }

    public int getTtl() {
        return ttl;
    }

    public Cache<String, Map<String, AccurevDepot>> getAccurevDepots() {
        return accurevDepots;
    }

    public Cache<String, Map<String, AccurevStream>> getAccurevStreams() {
        return accurevStreams;
    }
}
