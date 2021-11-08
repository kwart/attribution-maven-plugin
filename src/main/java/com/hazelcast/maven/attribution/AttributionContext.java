package com.hazelcast.maven.attribution;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Context object of the Attribution generator. Its shared by all producers and consumers. Data structures used here should be
 * thread safe!
 */
class AttributionContext {
    final BlockingQueue<SrcFile> srcQueue = new LinkedBlockingQueue<>(1024);
    final AtomicBoolean producersRunning = new AtomicBoolean(true);
    final ConcurrentMap<String, Set<String>> foundAttribution = new ConcurrentSkipListMap<>();
    final Set<String> exclusionPatterns = Collections.newSetFromMap(new ConcurrentHashMap<>());
}
