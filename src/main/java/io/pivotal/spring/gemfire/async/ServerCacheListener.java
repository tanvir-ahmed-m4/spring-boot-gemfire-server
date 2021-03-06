package io.pivotal.spring.gemfire.async;

import com.gemstone.gemfire.cache.*;
import com.gemstone.gemfire.cache.util.CacheListenerAdapter;
import com.gemstone.gemfire.pdx.PdxInstance;
import io.pivotal.spring.gemfire.async.lib.RegionProcessor;

import java.util.LinkedList;
import java.util.Properties;

public class ServerCacheListener<K,V> extends CacheListenerAdapter<K,V> implements Declarable {

    private GemFireCache gemFireCache;
    private Region regionCount;

    private Region<Integer, PdxInstance> regionTop;
    private Region regionTopTen;

    public void afterDestroy(EntryEvent<K,V> e) {
        try {
            if (e.getOperation().equals(Operation.EXPIRE_DESTROY)) {

                PdxInstance raw;
                Object oldValue = e.getOldValue();
                if (oldValue instanceof PdxInstance) {
                    raw = (PdxInstance)oldValue;
                } else {
                    throw new Exception("oldValue class:" + oldValue.getClass().getName());
                }


                Integer countDiff = -1;

                gemFireCache = CacheFactory.getAnyInstance();
                regionCount = gemFireCache.getRegion("RegionCount");
                regionTop = gemFireCache.getRegion("RegionTop");
                regionTopTen = gemFireCache.getRegion("RegionTopTen");

                RegionProcessor processor = new RegionProcessor(regionCount, regionTop, regionTopTen);

                // count & top process
                String route = (String)raw.getField("route");
                Long newTimestamp = (Long)raw.getField("timestamp");

                Integer originalCount = 0 ;
                Integer newCount = 0;
                Long originalTimestamp = 0L;

                PdxInstance originCountValue = (PdxInstance)regionCount.get(route);

                if(originCountValue==null){
                    newCount = 1;
                }
                else
                {
//                    originalCount = ((Byte)originCountValue.getField("route_count")).intValue();
                    originalCount = Integer.parseInt(originCountValue.getField("route_count").toString());
                    originalTimestamp = (Long)originCountValue.getField("timestamp");
                    newCount = originalCount + countDiff;
                }

                // top ten process
                Integer smallestToptenCount = 0;
                PdxInstance topTenValue = (PdxInstance)regionTopTen.get(1);
                if (topTenValue != null) {
                    LinkedList toptenList = (LinkedList)topTenValue.getField("toptenlist");
                    if (toptenList.size() != 0) {
//                        smallestToptenCount = ((Byte)((PdxInstance)toptenList.getLast()).getField("count")).intValue();
                        smallestToptenCount = Integer.parseInt(((PdxInstance)toptenList.getLast()).getField("count").toString());
                    }
                }

                Long keyTimestamp = 0L;
                String keyUuid = "";
                String keyRoute = "";
                Integer keyCount = 0;
                Boolean incremental = false;



                processor.processRegionCount(route, originalCount, originalTimestamp, newCount, newTimestamp);

                processor.processRegionTop(route, originalCount, originalTimestamp, newCount, newTimestamp);

                if (newCount < originalCount) {

                    if (originalCount >= smallestToptenCount) {
                        keyTimestamp = (Long)raw.getField("timestamp");
                        keyUuid = (String)raw.getField("uuid");
                        keyRoute = route;
                        keyCount = newCount;
                        incremental = false;

                        processor.processRegionTopTen(keyRoute, keyUuid, keyCount, keyTimestamp, incremental);
                    }
                }

            }
            else {
                throw new Exception("operation:" + e.getOperation().toString());
            }

        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void init(Properties props) {
        // do nothing
    }
}
